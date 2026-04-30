package com.pixeltranslator.multi.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Google ML Kit's on-device Translate client.
 *
 * Per-pair translators are cached — ML Kit creates a Translator instance
 * bound to a (source, target) pair, and each instance holds a reference
 * to a downloaded model. Caching avoids repeated model lookups and keeps
 * allocation off the hot path.
 *
 * Models download on first use for a given pair (~30 MB each). We trigger
 * downloadModelIfNeeded with [DownloadConditions] that allow any network
 * so we're not blocked on unmetered. After first success, calls are fully
 * offline.
 *
 * Used by the image-translation pipeline as the second step after Gemma OCR:
 * Gemma extracts the native-script text, this translator turns it into the
 * user's target language. Faster and more deterministic than Gemma's own
 * text-translate call, and purpose-built so it can't fall back to echoing
 * the source text.
 */
class TextTranslator {

    companion object {
        private const val TAG = "TextTranslator"
        // Maximum live Translator instances at once. Each holds the loaded
        // model in memory (~30 MB). Holding all 12 simultaneously was a
        // documented contributor to native OOM crashes alongside E4B —
        // capping at 2 keeps the active pair plus one warm fallback,
        // evicts the rest. Disk cache is untouched, so re-instantiating
        // a previously-evicted pair is just a constructor call (no
        // re-download).
        private const val MAX_LIVE_TRANSLATORS = 2
    }

    // LinkedHashMap ordered by access for LRU eviction.
    private val translators = linkedMapOf<Pair<String, String>, Translator>()

    /**
     * Translate [text] from [sourceIso] to [targetIso] (ISO 639-1 codes).
     * Returns null if the pair isn't supported by ML Kit or the model
     * download / translation itself fails — caller should fall back.
     *
     * Lazy-loads the pair's Translator on first use and evicts least-
     * recently-used Translators beyond [MAX_LIVE_TRANSLATORS].
     */
    suspend fun translate(text: String, sourceIso: String, targetIso: String): String? {
        if (text.isBlank()) return text
        if (sourceIso.equals(targetIso, ignoreCase = true)) return text

        val src = TranslateLanguage.fromLanguageTag(sourceIso.lowercase()) ?: run {
            Log.w(TAG, "Unsupported source: $sourceIso")
            return null
        }
        val tgt = TranslateLanguage.fromLanguageTag(targetIso.lowercase()) ?: run {
            Log.w(TAG, "Unsupported target: $targetIso")
            return null
        }

        val translator = obtainTranslator(sourceIso.lowercase(), targetIso.lowercase(), src, tgt)

        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(text).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Translate $sourceIso→$targetIso failed", t)
            null
        }
    }

    @Synchronized
    private fun obtainTranslator(
        srcKey: String,
        tgtKey: String,
        src: String,
        tgt: String
    ): Translator {
        val key = srcKey to tgtKey
        translators.remove(key)?.let {
            translators[key] = it  // refresh LRU position
            return it
        }
        val newTranslator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
        )
        translators[key] = newTranslator
        // Evict oldest until under cap. Each .close() releases the model's
        // native memory. Disk cache stays — re-instantiating later is cheap.
        while (translators.size > MAX_LIVE_TRANSLATORS) {
            val eldest = translators.entries.iterator().next()
            translators.remove(eldest.key)?.close()
            Log.i(TAG, "Evicted translator ${eldest.key} (over cap)")
        }
        return newTranslator
    }

    /**
     * Pre-download ML Kit translator models to disk for every language in
     * [isoCodes] paired with English. **Does not keep the Translator
     * instances alive** — each is closed immediately after download to
     * release native memory. Subsequent translate() calls re-instantiate
     * cheaply from the disk cache.
     *
     * Run once at first launch so the app is fully offline-capable; the
     * runtime memory cost is bounded by [MAX_LIVE_TRANSLATORS] regardless
     * of how many models are on disk.
     *
     * @return the number of models successfully downloaded.
     */
    suspend fun preloadEnglishPivotModels(isoCodes: List<String>): Int {
        var success = 0
        for (iso in isoCodes.distinct()) {
            if (iso.equals("en", ignoreCase = true)) continue
            val tag = TranslateLanguage.fromLanguageTag(iso.lowercase()) ?: continue
            val transient = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(tag)
                    .build()
            )
            try {
                transient.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                Log.i(TAG, "Preloaded EN↔$iso model (download only, instance closed)")
                success++
            } catch (t: Throwable) {
                Log.w(TAG, "Preload EN↔$iso failed", t)
            } finally {
                transient.close()
            }
        }
        return success
    }

    @Synchronized
    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
