package com.pixeltranslator.multi.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

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
    }

    private val translators = ConcurrentHashMap<Pair<String, String>, Translator>()

    /**
     * Translate [text] from [sourceIso] to [targetIso] (ISO 639-1 codes).
     * Returns null if the pair isn't supported by ML Kit or the model
     * download / translation itself fails — caller should fall back.
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

        val translator = translators.getOrPut(sourceIso.lowercase() to targetIso.lowercase()) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(tgt)
                    .build()
            )
        }

        return try {
            // Download the language pair model if we don't already have it.
            // DownloadConditions is empty → allow any network, no charging
            // requirement. First-call latency can be a few seconds; cached
            // calls are ~50-100 ms.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(text).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Translate $sourceIso→$targetIso failed", t)
            null
        }
    }

    /**
     * Download ML Kit translator models for every language in [isoCodes]
     * paired with English. ML Kit's translate models pivot through English,
     * so bidirectional coverage for a pair (X↔Y) is achieved by having the
     * English↔X and English↔Y models present.
     *
     * Intended to run once at app init so subsequent translations are fully
     * offline. Each model is ~30 MB; 12 non-English models totals ~360 MB
     * of one-time downloads. Progress is per-language; a single failure
     * doesn't block the others.
     *
     * @return the number of models successfully downloaded.
     */
    suspend fun preloadEnglishPivotModels(isoCodes: List<String>): Int {
        var success = 0
        for (iso in isoCodes.distinct()) {
            if (iso.equals("en", ignoreCase = true)) continue
            val tag = TranslateLanguage.fromLanguageTag(iso.lowercase()) ?: continue
            val translator = translators.getOrPut("en" to iso.lowercase()) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(tag)
                        .build()
                )
            }
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                Log.i(TAG, "Preloaded EN↔$iso model")
                success++
            } catch (t: Throwable) {
                Log.w(TAG, "Preload EN↔$iso failed", t)
            }
        }
        return success
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
