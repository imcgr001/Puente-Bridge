package com.pixeltranslator.app.ml

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps LiteRT-LM [Engine] to run Gemma 4 E2B on-device.
 *
 * Uses [Backend.GPU] for both the main LLM and audio encoder, offloading
 * inference to the Tensor G5 NPU on Pixel 10 Pro hardware.
 *
 * ## Model placement
 * ```
 * adb shell mkdir -p /sdcard/Android/data/com.pixeltranslator.app/files/
 * adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.pixeltranslator.app/files/
 * ```
 */
class GemmaTranslatorManager(private val context: Context) {

    companion object {
        private const val TAG = "GemmaTranslator"
        private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        private const val MAX_TOKENS = 512
    }

    private var engine: Engine? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val extDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files dir unavailable")
        val modelFile = File(extDir, MODEL_FILENAME)

        if (!modelFile.exists()) {
            throw IllegalStateException(
                "Model not found at ${modelFile.absolutePath}\n" +
                "Push it with:\n" +
                "  adb shell mkdir -p /sdcard/Android/data/${context.packageName}/files/\n" +
                "  adb push $MODEL_FILENAME /sdcard/Android/data/${context.packageName}/files/"
            )
        }

        Log.i(TAG, "Loading model from ${modelFile.absolutePath} (${modelFile.length() / 1_000_000} MB)")

        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.GPU(),
            audioBackend = Backend.CPU(),
            maxNumTokens = MAX_TOKENS
        )
        val e = Engine(config)
        e.initialize()
        engine = e

        Log.i(TAG, "Model loaded successfully.")
    }

    data class TranslationResult(
        val transcription: String,
        val translation: String,
        val targetLanguage: String  // "en" or "es"
    )

    /**
     * Transcribes speech and translates it to the other language.
     *
     * Auto-detects: English → Spanish, Spanish → English.
     */
    suspend fun translateSpeech(
        pcmAudio: ByteArray,
        onPartialResult: (String) -> Unit = {}
    ): TranslationResult = withContext(Dispatchers.IO) {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before translateSpeech()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        // Step 1: Transcribe
        val transcribeContents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text("Transcribe this speech exactly as spoken. Output ONLY the transcription, nothing else.")
        )

        val conversation = e.createConversation()
        try {
            val transcribeResponse = conversation.sendMessage(transcribeContents)
            val transcription = transcribeResponse.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()

            val sourceLang = detectLanguage(transcription)
            val targetLang = if (sourceLang == "es") "en" else "es"
            val targetName = if (targetLang == "es") "Spanish" else "English"

            // Step 2: Translate (explicit direction, same conversation)
            val translateResponse = conversation.sendMessage(
                "Translate the following to $targetName. Output ONLY the translation, nothing else.\n" +
                "Text: $transcription"
            )
            val translation = translateResponse.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()

            val result = TranslationResult(
                transcription = transcription,
                translation = translation,
                targetLanguage = targetLang
            )
            onPartialResult(translation)
            result
        } finally {
            conversation.close()
        }
    }

    private fun parseResponse(raw: String): TranslationResult {
        val transcription = Regex("TRANSCRIPTION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim() ?: ""
        val translation = Regex("TRANSLATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()

        return TranslationResult(
            transcription = transcription,
            translation = translation,
            targetLanguage = detectLanguage(translation)
        )
    }

    /** Heuristic language detection for English vs Spanish. */
    private fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        // Spanish-specific characters
        if (lower.any { it in "áéíóúñ¿¡ü" }) return "es"
        // Common Spanish words that rarely appear in English
        val spanishWords = listOf(
            "\\bel\\b", "\\bla\\b", "\\blos\\b", "\\blas\\b", "\\bdel\\b",
            "\\bque\\b", "\\bes\\b", "\\ben\\b", "\\bun\\b", "\\buna\\b",
            "\\bpor\\b", "\\bcon\\b", "\\bpara\\b", "\\bcomo\\b", "\\best[aá]\\b",
            "\\bhola\\b", "\\bgracias\\b", "\\bbueno\\b", "\\bdonde\\b",
            "\\btengo\\b", "\\bquiero\\b", "\\bpuedo\\b", "\\bnecesito\\b"
        )
        val matchCount = spanishWords.count { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(lower) }
        return if (matchCount >= 2) "es" else "en"
    }

    /** Wraps raw PCM bytes in a minimal RIFF/WAV header. */
    private fun wrapPcmAsWav(
        pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())          // ChunkID
            putInt(36 + pcm.size)              // ChunkSize
            put("WAVE".toByteArray())          // Format
            put("fmt ".toByteArray())          // Subchunk1ID
            putInt(16)                         // Subchunk1Size (PCM)
            putShort(1)                        // AudioFormat (1 = PCM)
            putShort(channels.toShort())       // NumChannels
            putInt(sampleRate)                 // SampleRate
            putInt(byteRate)                   // ByteRate
            putShort(blockAlign.toShort())     // BlockAlign
            putShort(bitsPerSample.toShort())  // BitsPerSample
            put("data".toByteArray())          // Subchunk2ID
            putInt(pcm.size)                   // Subchunk2Size
        }
        return header.array() + pcm
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
