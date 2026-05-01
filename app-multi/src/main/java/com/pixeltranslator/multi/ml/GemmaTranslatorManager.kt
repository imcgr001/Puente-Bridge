package com.pixeltranslator.multi.ml

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.pixeltranslator.multi.ui.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val sizeLabel: String,       // GB, shown as subtext under the chip
        val maxTokens: Int           // LiteRT-LM KV/cache budget
    ) {
        E2B(
            "gemma-4-E2B-it.litertlm",
            "Gemma 4 E2B (2.6 GB)",
            "Faster",
            "2.6 GB",
            1536
        ),
        E4B(
            "gemma-4-E4B-it.litertlm",
            "Gemma 4 E4B (3.7 GB)",
            "Higher Accuracy",
            "3.7 GB",
            1024
        )
    }

    companion object {
        private const val TAG = "GemmaTranslator"

        // Per-task sampling configurations. Defaults in LiteRT-LM are the
        // Gemma 4 generic chat settings (temp=1.0 / topK=64 / topP=0.95),
        // which is too stochastic for ASR — high-temperature sampling on
        // short utterances lets Gemma drift past the real transcription
        // and paraphrase the prompt as filler (observed: "muy bien, I
        // need to transcribe the following segment..."). Near-greedy
        // settings eliminate that.
        private val ASR_SAMPLER = SamplerConfig(
            topK = 1,
            topP = 1.0,
            temperature = 0.0,
            seed = 0
        )
        // Translation benefits from a touch of diversity (synonym
        // selection), but not much — still firmly low-temp.
        private val TRANSLATION_SAMPLER = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.3,
            seed = 0
        )
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
    private val inferenceMutex = Mutex()
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
        logMemorySnapshot("before EngineConfig", model)

        val mainBackend = Backend.GPU()
        val visionBackend = if (model == ModelSize.E4B) Backend.CPU() else Backend.GPU()
        val audioBackend = Backend.CPU()

        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = mainBackend,
            // Keep E4B's optional vision tower off the GPU. The common path is
            // speech; reserving GPU memory for photo OCR at startup pushes E4B
            // close enough to the native allocator limit that ART's recompiler
            // thread can fail mmap() and abort the process.
            visionBackend = visionBackend,
            audioBackend = audioBackend,
            maxNumTokens = model.maxTokens
        )
        Log.i(
            TAG,
            "Creating LiteRT-LM Engine: model=${model.name}, mainBackend=GPU, " +
                "visionBackend=${if (model == ModelSize.E4B) "CPU" else "GPU"}, " +
                "audioBackend=CPU, maxTokens=${model.maxTokens}"
        )
        logMemorySnapshot("before Engine()", model)
        val e = Engine(config)
        Log.i(TAG, "LiteRT-LM Engine constructed for ${model.name}")
        logMemorySnapshot("after Engine()", model)
        Log.i(TAG, "Initializing LiteRT-LM Engine for ${model.name}")
        e.initialize()
        Log.i(TAG, "LiteRT-LM Engine initialized for ${model.name}")
        logMemorySnapshot("after initialize()", model)
        engine = e
        currentModel = model

        Log.i(TAG, "${model.label} loaded successfully.")
    }

    private fun logMemorySnapshot(stage: String, model: ModelSize) {
        val runtime = Runtime.getRuntime()
        val javaUsedMb = (runtime.totalMemory() - runtime.freeMemory()).toMb()
        val javaTotalMb = runtime.totalMemory().toMb()
        val javaMaxMb = runtime.maxMemory().toMb()
        val nativeAllocatedMb = Debug.getNativeHeapAllocatedSize().toMb()
        val nativeHeapMb = Debug.getNativeHeapSize().toMb()
        val memInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        activityManager?.getMemoryInfo(memInfo)
        Log.i(
            TAG,
            "Memory[$stage]: model=${model.name}, java=${javaUsedMb}/${javaTotalMb}MB " +
                "(max=${javaMaxMb}MB), native=${nativeAllocatedMb}/${nativeHeapMb}MB, " +
                "systemAvail=${memInfo.availMem.toMb()}MB, " +
                "systemTotal=${memInfo.totalMem.toMb()}MB, " +
                "threshold=${memInfo.threshold.toMb()}MB, lowMemory=${memInfo.lowMemory}"
        )
    }

    private fun Long.toMb(): Long = this / (1024L * 1024L)

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
    ): Pair<Language, String> = withContext(Dispatchers.IO) { inferenceMutex.withLock {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before transcribeAndDetect()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text(buildTranscribePrompt(candidateA, candidateB))
        )
        val conv = e.createConversation(ConversationConfig(samplerConfig = ASR_SAMPLER))
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
    } }

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
    suspend fun transcribeOpen(pcmAudio: ByteArray): String = withContext(Dispatchers.IO) { inferenceMutex.withLock {
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
        val conv = e.createConversation(ConversationConfig(samplerConfig = ASR_SAMPLER))
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
    } }

    /** OCR-only call. Returns the text visible in the image in its original language. */
    suspend fun readImage(imageBytes: ByteArray): String = withContext(Dispatchers.IO) { inferenceMutex.withLock {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before readImage()")

        val promptText =
            "Read any text visible in this image and output it verbatim, in its original language " +
            "and native script. Output only the text. No labels, no translation, no explanations. " +
            "If no text is visible, output exactly: (no text)."
        val contents = Contents.of(
            Content.ImageBytes(imageBytes),
            Content.Text(promptText)
        )
        val conv = e.createConversation(ConversationConfig(samplerConfig = ASR_SAMPLER))
        try {
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
    } }


    /**
     * Direct audio → target-language translation (Gemma 4 "AST"). One
     * Conversation, one inference — audio is sent with a "translate into
     * $targetName" prompt and the output is the translation directly. No
     * intermediate transcription is produced or shown.
     *
     * Use case: ~2× faster than the transcribe+translate pipeline because
     * only one Gemma call runs. The cost is that we commit to the target
     * up front, so this mode only makes sense when the direction is known
     * (auto-detect: target is always English; paired mode: target is B).
     */
    suspend fun translateSpeechDirect(
        pcmAudio: ByteArray,
        target: Language
    ): String = withContext(Dispatchers.IO) { inferenceMutex.withLock {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before translateSpeechDirect()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val promptText =
            "Translate the following speech segment into ${target.displayName} " +
            "(${target.nativeName}). Output only the translation, in ${target.displayName}'s native script."
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text(promptText)
        )
        val conv = e.createConversation(ConversationConfig(samplerConfig = TRANSLATION_SAMPLER))
        try {
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
    } }

    /**
     * Paired-mode direct translation with Gemma-internal language ID: Gemma
     * hears the audio, internally decides which of [pairA] or [pairB] was
     * spoken, then translates into the OTHER one in a single AST call. This
     * gives automatic direction without a separate LID pass or alternation
     * heuristic, at the cost of trusting Gemma's acoustic LID (which is good
     * for high-resource pairs like EN↔ES but can misfire on close cousins).
     *
     * The model is instructed to emit a `LANG: xx` header followed by the
     * translation text so we know which target locale to use for TTS. Parse
     * failures fall back to [pairB] as the target.
     */
    suspend fun translateSpeechDirectPaired(
        pcmAudio: ByteArray,
        pairA: Language,
        pairB: Language
    ): DirectResult = withContext(Dispatchers.IO) { inferenceMutex.withLock {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before translateSpeechDirectPaired()")

        val wavBytes = wrapPcmAsWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val promptText = """
            The speaker's language is either ${pairA.displayName} (${pairA.nativeName}) or ${pairB.displayName} (${pairB.nativeName}).
            Translate the speech into whichever of these two languages the speaker is NOT using.
            If the speaker used ${pairA.displayName}, translate into ${pairB.displayName}.
            If the speaker used ${pairB.displayName}, translate into ${pairA.displayName}.

            Reply in exactly this format, with nothing else:
            LANG: xx
            TEXT: <translation>

            Where xx is the ISO 639-1 code (${pairA.code} or ${pairB.code}) of the language you OUTPUT.
        """.trimIndent()
        val contents = Contents.of(
            Content.AudioBytes(wavBytes),
            Content.Text(promptText)
        )
        val conv = e.createConversation(ConversationConfig(samplerConfig = TRANSLATION_SAMPLER))
        val raw = try {
            conv.sendMessage(contents).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        } finally {
            conv.close()
            System.gc()
            System.runFinalization()
            delay(150)
        }

        // Parse the LANG: xx / TEXT: ... format. Tolerate whitespace, extra
        // punctuation, and missing tags. If LANG is missing or unrecognized,
        // default the target to pairB — matches the existing alternation
        // fallback behavior.
        val langMatch = Regex("""LANG:\s*([A-Za-z]{2})""", RegexOption.IGNORE_CASE).find(raw)
        val textMatch = Regex("""TEXT:\s*([\s\S]+)""", RegexOption.IGNORE_CASE).find(raw)
        val detectedCode = langMatch?.groupValues?.get(1)?.lowercase()
        val detectedLang = when (detectedCode) {
            pairA.code.lowercase() -> pairA
            pairB.code.lowercase() -> pairB
            else -> null
        }
        val translation = textMatch?.groupValues?.get(1)?.trim()?.trim('"')
            ?: raw.trim().trim('"')
        DirectResult(
            target = detectedLang ?: pairB,
            translation = translation
        )
    } }

    data class DirectResult(val target: Language, val translation: String)

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
     * Used when the caller has only a free-form source-language hint.
     * Gemma handles most common languages even without a typed enum hint.
     */
    suspend fun translate(
        text: String,
        sourceDisplayName: String?,
        target: Language
    ): String = withContext(Dispatchers.IO) { inferenceMutex.withLock {
        val e = engine
            ?: throw IllegalStateException("Call initialize() before translate()")

        val conv = e.createConversation(ConversationConfig(samplerConfig = TRANSLATION_SAMPLER))
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
    } }

    /**
     * Builds the transcribe prompt. Gemma 4 E2B/E4B supports BOTH ASR
     * (speech-to-text) and AST (automated speech translation) via the same
     * audio pathway — the task is selected purely by prompt wording. Our
     * earlier prompt ("The speaker is using either A or B. Listen for that
     * language's phonemes") was subtly ambiguous — "listen for X" can be
     * read as an AST hint with one of the two as the target. When Gemma
     * resolved it toward AST, we'd get English-text output from Spanish
     * audio, which then poisoned downstream direction detection.
     *
     * This wording mirrors Google's own documented ASR example and is
     * explicit that the task is transcription, NOT translation.
     */
    /**
     * Transcribe prompt, adapted from Google's documented Gemma 4 ASR
     * recipe (ai.google.dev/gemma/docs/capabilities/audio). We keep the
     * bullet-point formatting guidance from the official example and add
     * a single sentence naming the two-language pair so the audio encoder
     * anchors to the right phoneme set.
     */
    private fun buildTranscribePrompt(a: Language, b: Language): String = """
        Transcribe the following speech segment in its original language. The speaker used either ${a.displayName} (${a.nativeName}) or ${b.displayName} (${b.nativeName}).

        Follow these specific instructions for formatting the answer:
        * Only output the transcription, with no newlines.
        * When transcribing numbers, write the digits, i.e. write 1.7 and not one point seven, and write 3 instead of three.
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
