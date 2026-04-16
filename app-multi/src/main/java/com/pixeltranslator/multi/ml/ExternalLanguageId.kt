package com.pixeltranslator.multi.ml

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Google ML Kit's Language Identification client.
 *
 * ML Kit identifies ~110 languages from text with a bundled, offline-capable
 * model. We use it only in auto-detect mode where we need open-set
 * identification — paired mode still uses [com.pixeltranslator.multi.ui.Language]'s
 * deterministic binary scorer, which is the right tool when the answer space
 * is constrained to two pre-selected languages.
 *
 * The bundled variant (`com.google.mlkit:language-id`) ships the model with
 * the APK so there is no runtime download — critical for our offline-first
 * disaster-response scenarios. Adds ~900 KB to APK size.
 *
 * Returns results in [Result], a sealed class with three cases:
 *   - [Detected]: ML Kit confidently identified the language (ISO 639-1 code)
 *   - [Undetermined]: ML Kit returned "und" — not confident enough to pick one
 *   - [Error]: the underlying call threw; caller should fall back to hand-rolled scoring
 */
class ExternalLanguageId {

    companion object {
        private const val TAG = "ExternalLanguageId"
        // Matches ML Kit's default but made explicit so the threshold is
        // visible in one place and easy to tune.
        private const val CONFIDENCE_THRESHOLD: Float = 0.5f
    }

    private val client = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .build()
    )

    suspend fun identify(text: String): Result {
        if (text.isBlank()) return Result.Undetermined
        return try {
            val code = client.identifyLanguage(text).await()
            Log.i(TAG, "Identified '$text' -> $code")
            when {
                code == "und" -> Result.Undetermined
                else -> Result.Detected(code)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Language identification failed", t)
            Result.Error(t)
        }
    }

    /**
     * Ranked candidates with confidence scores, for when the caller wants to
     * make a policy decision about low-confidence results (e.g. flag the turn)
     * or constrain to a specific pair of languages.
     */
    suspend fun identifyRanked(text: String): List<Candidate> {
        if (text.isBlank()) return emptyList()
        return try {
            client.identifyPossibleLanguages(text).await()
                .filter { it.languageTag != "und" }
                .map { Candidate(it.languageTag, it.confidence) }
        } catch (t: Throwable) {
            Log.w(TAG, "Ranked language identification failed", t)
            emptyList()
        }
    }

    fun close() {
        client.close()
    }

    data class Candidate(val code: String, val confidence: Float)

    sealed class Result {
        data class Detected(val code: String) : Result()
        object Undetermined : Result()
        data class Error(val throwable: Throwable) : Result()
    }
}
