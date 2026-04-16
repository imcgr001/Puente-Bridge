package com.pixeltranslator.multi.ml

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.pixeltranslator.multi.ui.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps LiteRT-LM [Engine] to run Gemma 4 E2B/E4B on-device for any-language
 * speech-to-speech translation.
 *
 * Each turn uses TWO separate Conversations:
 *   1. Transcribe + auto-detect source language ("LANG: xx\nTEXT: ...").
 *   2. Translate transcription into the caller-supplied target language.
 *
 * Conversations are split so the audio-token KV cache is released before the
 * translate step allocates its own, and each teardown drains native finalizers
 * before returning — both are necessary to keep E4B from OOM-killing the
 * process after a few turns.
 */
class GemmaTranslatorManager(private val context: Context) {

    enum class ModelSize(
        val filename: String,
        val label: String,           // technical, used in status messages
        val friendlyLabel: String,   // user-facing chip label
        val sizeLabel: String        // GB, shown as subtext under the chip
    ) {
        E2B(
            "gemma-4-E2B-it.litertlm",
            "Gemma 4 E2B (2.6 GB)",
            "Faster",
            "2.6 GB"
        ),
        E4B(
            "gemma-4-E4B-it.litertlm",
            "Gemma 4 E4B (3.7 GB)",
            "Higher Accuracy",
            "3.7 GB"
        )
    }

    companion object {
        private const val TAG = "GemmaTranslator"
        private const val MAX_TOKENS = 1536
        /**
         * Shared model directory used by both this app and the bilingual app
         * so they don't each need their own multi-GB copy of the weights.
         * Requires MANAGE_EXTERNAL_STORAGE — other apps' `Android/data/` dirs
         * are inaccessible even with that permission.
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
        // See app-module notes: the prior Engine's native/KV memory is freed
        // asynchronously by finalizers. Three gc passes + delays is what it
        // takes on Pixel 10 Pro to reclaim E4B state before the new allocation.
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

    /**
     * Transcribes speech and auto-detects which of two candidate languages
     * was spoken. The detection is NOT done by the model — Gemma's self-
     * reported LANG tag defaults to "en" under acoustic ambiguity regardless
     * of what it actually transcribed. Instead we take Gemma's transcription
     * (which is reliable) and score it with a Kotlin-side heuristic over
     * script ranges, diacritics, and stopwords, picking whichever of the
     * two candidates wins.
     */
    suspend fun transcribeAndDetect(
        pcmAudio: ByteArray,
        candidateA: Language,
        candidateB: Language
    ): Pair<Language, String> = withContext(Dispatchers.IO) {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before transcribeAndDetect()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text(buildTranscribePrompt(candidateA, candidateB))
        )
        val conv = e.createConversation()
        val transcription = try {
            conv.sendMessage(contents).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
                .trim('"')
        } finally {
            conv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }
        Log.i(TAG, "Transcribe: $transcription")

        val detected = Language.detectFromText(transcription, candidateA, candidateB)
        Log.i(
            TAG,
            "Detected ${detected.code} (A=${candidateA.code} score=${Language.scoreCandidate(transcription, candidateA)}, " +
                "B=${candidateB.code} score=${Language.scoreCandidate(transcription, candidateB)})"
        )
        detected to transcription
    }

    /**
     * Open-detect transcription: no language hint given to the audio encoder.
     * Lets Gemma decide what language it heard and produce native-script
     * output. Used by the experimental auto-detect mode where the user
     * hasn't pre-committed to a language pair.
     *
     * Tradeoff: without anchoring, similar-phoneme languages may be forced
     * into English-shaped tokens (the original "muy bien → we're being"
     * failure mode). The reward is honest transcription for any of the 13
     * supported languages without a pre-selection step.
     */
    suspend fun transcribeOpen(pcmAudio: ByteArray): String = withContext(Dispatchers.IO) {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before transcribeOpen()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text(
                "Transcribe exactly what was said, in its original language. " +
                "Use that language's native script. Output ONLY the transcription. " +
                "No labels, no translation, no explanation."
            )
        )
        val conv = e.createConversation()
        val transcription = try {
            conv.sendMessage(contents).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
                .trim('"')
        } finally {
            conv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }
        Log.i(TAG, "TranscribeOpen: $transcription")
        transcription
    }

    /**
     * Translates [text] into [target]. [source] is an optional hint; passing
     * null lets Gemma infer the source from the text itself.
     */
    suspend fun translate(
        text: String,
        source: Language?,
        target: Language
    ): String = translate(text, source?.displayName, target)

    /**
     * Translates [text] into [target] with a free-form source language name.
     * Used in auto-detect mode when the detected source is an ML Kit language
     * outside our curated [Language] enum (e.g. Czech, Polish, Persian).
     * Gemma handles most common languages even without a typed hint.
     */
    suspend fun translate(
        text: String,
        sourceDisplayName: String?,
        target: Language
    ): String = withContext(Dispatchers.IO) {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before translate()")

        val conv = e.createConversation()
        try {
            conv.sendMessage(buildTranslatePrompt(text, sourceDisplayName, target))
                .contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        } finally {
            conv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }
    }

    /**
     * Builds the transcribe prompt. Gemma is told the audio is one of two
     * specific languages so its audio encoder anchors to the right phoneme
     * set. We no longer ask it for a language label — text-side detection
     * in [Language.detectFromText] is used instead.
     */
    private fun buildTranscribePrompt(a: Language, b: Language): String = """
        The speaker is using either ${a.displayName} (${a.nativeName}) or ${b.displayName} (${b.nativeName}). Listen carefully for that language's phonemes.

        Transcribe exactly what was said, in the original language.

        Output ONLY the transcription. No labels, no translation, no explanation.
    """.trimIndent()

    /**
     * Builds the translate-step prompt. Explicit target-language lock + a
     * one-shot example from a well-covered pair reduces Gemma's tendency to
     * substitute a same-meaning word from another language (multilingual
     * drift), which is worst on E2B.
     */
    private fun buildTranslatePrompt(
        transcription: String,
        sourceDisplayName: String?,
        target: Language
    ): String {
        val sourceClause = if (sourceDisplayName != null)
            "Source language: $sourceDisplayName."
        else
            "Source language: auto-detect."

        // One generic example using an English→target pair. Even when source
        // is something else, the example still anchors the output language.
        val example = if (target == Language.ENGLISH) {
            "Text: \"Hola, ¿cómo estás hoy?\"\n" +
            "Translation: \"Hello, how are you today?\""
        } else {
            val exampleTargets = mapOf(
                Language.SPANISH to "Hola, ¿cómo estás hoy?",
                Language.FRENCH to "Bonjour, comment allez-vous aujourd'hui ?",
                Language.GERMAN to "Hallo, wie geht es dir heute?",
                Language.PORTUGUESE to "Olá, como você está hoje?",
                Language.ITALIAN to "Ciao, come stai oggi?",
                Language.CHINESE to "你好,你今天怎么样?",
                Language.JAPANESE to "こんにちは、今日はどうですか?",
                Language.KOREAN to "안녕하세요, 오늘 어떻게 지내세요?",
                Language.HINDI to "नमस्ते, आज आप कैसे हैं?",
                Language.ARABIC to "مرحبًا، كيف حالك اليوم؟",
                Language.RUSSIAN to "Привет, как ты сегодня?"
            )
            "Text: \"Hello, how are you today?\"\n" +
            "Translation: \"${exampleTargets[target] ?: "Hello, how are you today?"}\""
        }

        return """
            You are a professional translator. Translate the text into ${target.displayName} (${target.nativeName}).
            $sourceClause
            Rules:
            - Respond in ${target.displayName} ONLY. Every word must be a valid ${target.displayName} word.
            - Do not mix in words, characters, or scripts from any other language.
            - Output ONLY the translation. No explanations, no original text, no labels.

            Example:
            $example

            Text: "$transcription"
            Translation:
        """.trimIndent()
    }

    private fun wrapPcmAsWav(
        pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(pcm.size)
        }
        return header.array() + pcm
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
