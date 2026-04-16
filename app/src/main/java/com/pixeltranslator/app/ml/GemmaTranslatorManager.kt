package com.pixeltranslator.app.ml

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    enum class ModelSize(val filename: String, val label: String) {
        E2B("gemma-4-E2B-it.litertlm", "Gemma 4 E2B (2.6 GB)"),
        E4B("gemma-4-E4B-it.litertlm", "Gemma 4 E4B (3.7 GB)")
    }

    companion object {
        private const val TAG = "GemmaTranslator"
        private const val MAX_TOKENS = 1536
        /**
         * Shared model directory used by both this app and app-multi so they
         * don't each need their own multi-GB copy of the weights. Requires
         * MANAGE_EXTERNAL_STORAGE — other apps' `Android/data/` dirs are
         * inaccessible even with that permission, so we keep models outside
         * any app-private sandbox.
         */
        val SHARED_MODEL_DIR: File = File(
            Environment.getExternalStorageDirectory(),
            "Download/litertlm-models"
        )
    }

    private var engine: Engine? = null
    var currentModel: ModelSize? = null
        private set

    suspend fun initialize(model: ModelSize = ModelSize.E2B) = withContext(Dispatchers.IO) {
        // Fully drain the prior engine before allocating the new one. Close()
        // alone isn't enough: GPU/native memory is released via finalizers, and
        // if the new Engine starts loading weights while the old ones are still
        // resident, GPU memory gets stomped and the model emits garbage tokens
        // (`<unused48>` spam, session errors).
        engine?.let { prior ->
            prior.close()
            engine = null
            // Canonical "make sure finalizers actually run" pattern: first gc
            // enqueues unreachable objects, runFinalization runs their
            // finalizers (freeing native memory), second gc collects the
            // finalizer-cleared referents. Repeat 5× with a 300 ms delay
            // between cycles — E4B's resident GPU/KV footprint needs roughly
            // this long to fully release on Pixel 10 Pro before the next
            // Engine() can allocate without stomping native memory.
            repeat(5) {
                System.gc()
                System.runFinalization()
                System.gc()
                delay(300)
            }
        }

        val modelFile = File(SHARED_MODEL_DIR, model.filename)

        if (!modelFile.exists()) {
            throw IllegalStateException(
                "Model not found at ${modelFile.absolutePath}\n" +
                "Push it with:\n" +
                "  adb shell mkdir -p ${SHARED_MODEL_DIR.absolutePath}\n" +
                "  adb push ${model.filename} ${SHARED_MODEL_DIR.absolutePath}/"
            )
        }

        Log.i(TAG, "Loading ${model.label} from ${modelFile.absolutePath}")

        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.GPU(),
            audioBackend = Backend.CPU(),
            maxNumTokens = MAX_TOKENS
        )
        val e = Engine(config)
        e.initialize()
        engine = e
        currentModel = model

        Log.i(TAG, "${model.label} loaded successfully.")
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

        // Transcribe and translate in *separate* Conversations so the audio-token
        // KV cache is reclaimed before the translate step allocates its own. On
        // E4B, keeping both in one Conversation left enough undrained native
        // state to OOM-kill the process after a few turns.
        val transcribeConv = e.createConversation()
        val transcription = try {
            val resp = transcribeConv.sendMessage(transcribeContents)
            resp.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        } finally {
            transcribeConv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }

        val sourceLang = detectLanguage(transcription)
        val targetLang = if (sourceLang == "es") "en" else "es"
        val targetName = if (targetLang == "es") "Spanish" else "English"

        val translateConv = e.createConversation()
        val translation = try {
            val resp = translateConv.sendMessage(buildTranslatePrompt(transcription, targetLang, targetName))
            resp.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        } finally {
            translateConv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }

        val result = TranslationResult(
            transcription = transcription,
            translation = translation,
            targetLanguage = targetLang
        )
        onPartialResult(translation)
        result
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

    /**
     * Builds the translate-step prompt. The explicit "only in $targetName"
     * constraint plus a one-shot example reduces multilingual drift (Gemma 4
     * is heavily multilingual and will otherwise occasionally substitute a
     * Hindi / Russian / Portuguese synonym for a word).
     */
    private fun buildTranslatePrompt(
        transcription: String,
        targetLang: String,
        targetName: String
    ): String {
        val example = if (targetLang == "es") {
            "Text: \"Let's try once more. This works better, otherwise it will be annoying.\"\n" +
            "Translation: \"Intentemos una vez más. Esto funciona mejor, de lo contrario será molesto.\""
        } else {
            "Text: \"Intentemos una vez más. Esto funciona mejor, de lo contrario será molesto.\"\n" +
            "Translation: \"Let's try once more. This works better, otherwise it will be annoying.\""
        }
        return """
            You are a professional translator. Translate the text into $targetName.
            Rules:
            - Respond in $targetName ONLY. Every word must be a valid $targetName word.
            - Do not mix in words, characters, or scripts from any other language.
            - Output ONLY the translation. No explanations, no original text, no labels.

            Example:
            $example

            Text: "$transcription"
            Translation:
        """.trimIndent()
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
