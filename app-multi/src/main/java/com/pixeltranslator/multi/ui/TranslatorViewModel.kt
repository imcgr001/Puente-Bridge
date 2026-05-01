package com.pixeltranslator.multi.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.lifecycle.viewModelScope
import com.pixeltranslator.multi.audio.AudioCaptureManager
import com.pixeltranslator.multi.ml.ExternalLanguageId
import com.pixeltranslator.multi.ml.GemmaTranslatorManager
import com.pixeltranslator.multi.ml.GemmaTranslatorManager.ModelSize
import com.pixeltranslator.multi.ml.TextTranslator
import com.pixeltranslator.multi.ml.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationTurn(
    val transcription: String,
    val translation: String,
    val sourceLanguage: Language?,  // null when auto-detect identified a language outside our 13
    val sourceDisplayName: String,  // always populated — falls back to ISO code for exotic languages
    val targetLanguage: Language,
    val spokenAloud: Boolean,  // false → TTS voice wasn't available; translation shown as text only
    val isImageTurn: Boolean = false,  // true for photo OCR/translation turns; no TTS is expected
    val thumbnailJpeg: ByteArray? = null,  // set for image-translation turns; downsampled JPEG for bubble preview
    val lowConfidence: Boolean = false,       // detector confidence below threshold
    val qualityUnverified: Boolean = false,   // source is outside our curated 13 (ML-Kit-only support)
    val translationSuspect: Boolean = false,  // output-side sanity check failed — translation ≠ target language
    val unexpectedEnglish: Boolean = false,   // auto-detect identified English — likely audio-encoder hallucination from a non-English speaker
    val confusableSink: Boolean = false,      // auto-detect identified a language that often absorbs its neighbors (Hindi, Russian, Arabic, etc.)
    val outOfPairLanguageName: String? = null,  // photo OCR detected a language NOT in the configured A/B pair (display name)
)

/**
 * Bilingual conversation config. Per turn, the target is whichever of the two
 * is NOT the detected source; if detection fails or returns a language that is
 * neither [languageA] nor [languageB], we default to translating into
 * [languageA]. The two languages can't be equal.
 */
data class TranslatorUiState(
    val status: String = "Initializing...",
    val turns: List<ConversationTurn> = emptyList(),
    val isRecording: Boolean = false,
    val isModelLoaded: Boolean = false,
    val currentModel: ModelSize = ModelSize.E2B,
    val languageA: Language = Language.ENGLISH,
    val languageB: Language = Language.SPANISH,
    val ttsAvailableForA: Boolean = true,
    val ttsAvailableForB: Boolean = true,
    val needsStoragePermission: Boolean = false,
    val showDisclaimer: Boolean = false,
    val showAbout: Boolean = false,
    val isAutoDetect: Boolean = false,  // experimental: open-set lang detect, always translate to English
    val isDirectTranslation: Boolean = false,  // Gemma 4 AST: audio→target in one call, no transcription step
    val isExplicitDirection: Boolean = false,  // paired+direct mode: show two target-specific mics
    val isAutoStopMic: Boolean = false,  // tap mic once; stop automatically after trailing silence
    val showSettings: Boolean = false,  // bottom-sheet-style modal overlay for mode toggles
    val pendingDirectTarget: Language? = null,  // set when a direction-specific mic was pressed in paired+direct mode
    val isProcessing: Boolean = false   // a turn is in flight (transcribe/translate/TTS); mic button should be disabled
)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // v2: invalidated the earlier prefs store after a stale Arabic value
        // was observed surviving reinstalls via Android's auto-backup. Bump
        // this suffix whenever we need to force a fresh default pair.
        private const val PREFS = "multi_translator_prefs_v2"
        private const val KEY_LANG_A = "language_a_code"
        private const val KEY_LANG_B = "language_b_code"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_DIRECT_TRANSLATION = "direct_translation"
        private const val KEY_EXPLICIT_DIRECTION = "explicit_direction"
        private const val KEY_AUTO_STOP_MIC = "auto_stop_mic"
        private const val KEY_TRANSLATE_MODELS_PRELOADED = "translate_models_preloaded_v1"
    }

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val tts = TtsManager(application)
    private val languageId = ExternalLanguageId()
    private val textTranslator = TextTranslator()
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pendingTurnsFile = File(application.cacheDir, "pending_turns.json")

    private val _uiState = MutableStateFlow(
        run {
            val a = Language.fromCode(prefs.getString(KEY_LANG_A, null)) ?: Language.ENGLISH
            val b = Language.fromCode(prefs.getString(KEY_LANG_B, null))
                ?: if (a != Language.SPANISH) Language.SPANISH else Language.FRENCH
            val savedModel = prefs.getString(KEY_MODEL, null)
                ?.let { name -> ModelSize.entries.firstOrNull { it.name == name } }
                ?: ModelSize.E2B
            // Consume any turns saved immediately before a model-switch
            // restart so the conversation appears to persist across the swap.
            val pendingTurns = consumePendingTurns()
            TranslatorUiState(
                languageA = a,
                languageB = b,
                currentModel = savedModel,
                turns = pendingTurns,
                isDirectTranslation = prefs.getBoolean(KEY_DIRECT_TRANSLATION, false),
                isExplicitDirection = prefs.getBoolean(KEY_EXPLICIT_DIRECTION, false),
                isAutoStopMic = prefs.getBoolean(KEY_AUTO_STOP_MIC, false)
            )
        }
    )
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var capturedAudio: ByteArray = ByteArray(0)

    // Prevents concurrent initialize() calls — init() and the activity's
    // ON_RESUME observer can both invoke tryLoadModels before isModelLoaded
    // flips, and LiteRT-LM's native Engine constructor crashes if re-entered.
    @Volatile
    private var isLoading: Boolean = false

    init {
        tryLoadModels()
    }

    /**
     * On first app launch, pre-download ML Kit translator models for every
     * supported language paired with English. ML Kit pivots through English,
     * so 12 downloads cover any-to-any translation via the pivot. Once
     * complete, the app can translate fully offline — critical for the
     * disaster-response and field-clinic scenarios the app is designed for.
     *
     * Fire-and-forget on a background coroutine; a completion flag in prefs
     * prevents re-running on subsequent launches. Individual download
     * failures don't block the others or retry automatically — reinstall
     * or clear this flag to rerun.
     */
    private fun preloadTranslateModelsOnceIfSafe() {
        // E4B leaves little native-memory headroom. Preloading 12 ML Kit
        // translators at startup is a background convenience, not required for
        // speech translation, and it competes with LiteRT-LM's largest mmap()
        // window. E4B users still get lazy download/on-device caching on first
        // photo translation.
        if (_uiState.value.currentModel == ModelSize.E4B) return
        if (prefs.getBoolean(KEY_TRANSLATE_MODELS_PRELOADED, false)) return
        viewModelScope.launch {
            val isoCodes = Language.entries.map { it.code }
            val ok = textTranslator.preloadEnglishPivotModels(isoCodes)
            if (ok >= isoCodes.count { !it.equals("en", ignoreCase = true) }) {
                prefs.edit().putBoolean(KEY_TRANSLATE_MODELS_PRELOADED, true).apply()
                Log.i("TranslatorViewModel", "All ML Kit translate models preloaded")
            } else {
                Log.w("TranslatorViewModel", "Translate preload partial: $ok models downloaded")
            }
        }
    }

    /**
     * Re-check storage permission and (re)load models. Called from init and
     * from the MainActivity after the user returns from the all-files-access
     * settings page. No-op if already loaded OR if a load is already in flight.
     */
    fun tryLoadModels() {
        if (_uiState.value.isModelLoaded) return
        if (isLoading) return
        if (!hasAllFilesAccess()) {
            _uiState.update {
                it.copy(
                    needsStoragePermission = true,
                    status = "Grant all-files access to load models"
                )
            }
            return
        }
        _uiState.update { it.copy(needsStoragePermission = false) }
        isLoading = true
        viewModelScope.launch {
            _uiState.update { it.copy(status = "Loading models...") }
            try {
                gemma.initialize(_uiState.value.currentModel)
                tts.initialize()
                refreshTtsAvailability()
                _uiState.update {
                    it.copy(
                        status = "Ready",
                        isModelLoaded = true
                    )
                }
                preloadTranslateModelsOnceIfSafe()
            } catch (e: Exception) {
                _uiState.update { it.copy(status = "Model load failed: ${e.message}") }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Rejects accidentally-short or near-silent captures. Below either
     * threshold, Gemma will fabricate plausible-sounding "transcriptions"
     * drawn from its prompt context (e.g. echoing the "The speaker is
     * using English/Spanish" framing back as the user's supposed utterance).
     * Minimum 0.3 s of 16-bit/16 kHz PCM and RMS above 200/32767 (~-44 dB FS).
     */
    private fun isAudioMeaningful(pcm: ByteArray): Boolean {
        if (pcm.size < 9600) return false  // < 0.3 s at 16 kHz/16-bit
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var sumSquares = 0.0
        var count = 0
        while (buf.remaining() >= 2) {
            val s = buf.short.toInt()
            sumSquares += (s * s).toDouble()
            count++
        }
        if (count == 0) return false
        val rms = Math.sqrt(sumSquares / count)
        return rms > 200.0
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            true

    fun clearConversation() {
        _uiState.update { it.copy(turns = emptyList()) }
    }

    fun showDisclaimer() {
        _uiState.update { it.copy(showDisclaimer = true) }
    }

    fun dismissDisclaimer() {
        _uiState.update { it.copy(showDisclaimer = false) }
    }

    fun showAbout() {
        _uiState.update { it.copy(showAbout = true) }
    }

    fun dismissAbout() {
        _uiState.update { it.copy(showAbout = false) }
    }

    fun setAutoDetect(enabled: Boolean) {
        _uiState.update { it.copy(isAutoDetect = enabled) }
    }

    fun setDirectTranslation(enabled: Boolean) {
        _uiState.update { it.copy(isDirectTranslation = enabled) }
        prefs.edit().putBoolean(KEY_DIRECT_TRANSLATION, enabled).apply()
    }

    fun setExplicitDirection(enabled: Boolean) {
        _uiState.update { it.copy(isExplicitDirection = enabled) }
        prefs.edit().putBoolean(KEY_EXPLICIT_DIRECTION, enabled).apply()
    }

    fun setAutoStopMic(enabled: Boolean) {
        _uiState.update { it.copy(isAutoStopMic = enabled) }
        prefs.edit().putBoolean(KEY_AUTO_STOP_MIC, enabled).apply()
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    /**
     * Plays the pre-translated disclaimer aloud in [language] using Android
     * TTS. Used from the disclaimer screen so the other speaker (who may
     * not read) can hear the usage instructions and privacy promise in
     * their own language.
     */
    fun playDisclaimerAloud(language: Language) {
        viewModelScope.launch {
            tts.speak(Disclaimer.textFor(language), language.locale)
        }
    }

    /**
     * Switches the active model by persisting the choice and restarting the
     * app process. LiteRT-LM's native GPU/KV allocations aren't reliably
     * released in-process even after `close()` + aggressive GC, so an
     * in-session switch from E4B back to E2B reliably native-crashes. Killing
     * and relaunching lets the OS reclaim everything at the process level;
     * the relaunched app reads [KEY_MODEL] from prefs and cold-loads the new
     * choice.
     */
    fun switchModel(model: ModelSize) {
        if (model == _uiState.value.currentModel) return
        if (isLoading) return
        isLoading = true

        // Sync writes — must be durable before we kill the process.
        prefs.edit().putString(KEY_MODEL, model.name).commit()
        saveTurnsForRestart(_uiState.value.turns)

        _uiState.update {
            it.copy(
                currentModel = model,
                isModelLoaded = false,
                status = "Switching to ${model.friendlyLabel}…"
            )
        }

        viewModelScope.launch {
            // Brief delay so the user sees the "Switching…" status flash
            // rather than the screen just disappearing.
            kotlinx.coroutines.delay(400)
            val app = getApplication<Application>()
            val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (intent != null) app.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    /**
     * Serializes the current conversation turns to a cache file so the
     * post-restart process can restore them. Written only immediately before
     * an intentional model-switch exit — the file is deleted on the next
     * launch after being consumed, so normal app close / OS kills still wipe
     * history as the privacy story promises.
     */
    private fun saveTurnsForRestart(turns: List<ConversationTurn>) {
        try {
            val arr = JSONArray()
            for (t in turns) {
                arr.put(JSONObject().apply {
                    put("transcription", t.transcription)
                    put("translation", t.translation)
                    put("src", t.sourceLanguage?.code ?: JSONObject.NULL)
                    put("srcName", t.sourceDisplayName)
                    put("tgt", t.targetLanguage.code)
                    put("spoken", t.spokenAloud)
                    put("img", t.isImageTurn)
                    put("lowConf", t.lowConfidence)
                    put("qualUnv", t.qualityUnverified)
                    put("trSusp", t.translationSuspect)
                    put("unexEn", t.unexpectedEnglish)
                    put("confSink", t.confusableSink)
                    if (t.thumbnailJpeg != null) {
                        put("thumb", android.util.Base64.encodeToString(
                            t.thumbnailJpeg, android.util.Base64.NO_WRAP
                        ))
                    }
                })
            }
            pendingTurnsFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w("TranslatorViewModel", "Failed to persist turns for restart", e)
        }
    }

    private fun consumePendingTurns(): List<ConversationTurn> {
        if (!pendingTurnsFile.exists()) return emptyList()
        val result = mutableListOf<ConversationTurn>()
        try {
            val arr = JSONArray(pendingTurnsFile.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val srcCode = if (obj.isNull("src")) null else obj.getString("src")
                val srcLang = Language.fromCode(srcCode)
                val srcName = obj.optString("srcName", srcLang?.displayName ?: "Unknown")
                val tgt = Language.fromCode(obj.getString("tgt")) ?: Language.ENGLISH
                val thumbB64 = obj.optString("thumb", "")
                val thumbBytes = if (thumbB64.isNotEmpty()) {
                    try {
                        android.util.Base64.decode(thumbB64, android.util.Base64.NO_WRAP)
                    } catch (t: Throwable) { null }
                } else null
                result.add(
                    ConversationTurn(
                        transcription = obj.getString("transcription"),
                        translation = obj.getString("translation"),
                        sourceLanguage = srcLang,
                        sourceDisplayName = srcName,
                        targetLanguage = tgt,
                        spokenAloud = obj.getBoolean("spoken"),
                        isImageTurn = obj.optBoolean("img", thumbBytes != null),
                        lowConfidence = obj.optBoolean("lowConf", false),
                        qualityUnverified = obj.optBoolean("qualUnv", false),
                        translationSuspect = obj.optBoolean("trSusp", false),
                        unexpectedEnglish = obj.optBoolean("unexEn", false),
                        confusableSink = obj.optBoolean("confSink", false),
                        thumbnailJpeg = thumbBytes
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("TranslatorViewModel", "Failed to restore turns after restart", e)
        } finally {
            // Delete unconditionally — better to lose one restart's history
            // than to have a stale file quietly persist across sessions.
            pendingTurnsFile.delete()
        }
        return result
    }

    fun setLanguageA(language: Language) {
        val s = _uiState.value
        if (language == s.languageA) return
        // If the new A collides with B, swap B out to the previous A.
        val newB = if (language == s.languageB) s.languageA else s.languageB
        prefs.edit()
            .putString(KEY_LANG_A, language.code)
            .putString(KEY_LANG_B, newB.code)
            .apply()
        _uiState.update { it.copy(languageA = language, languageB = newB) }
        refreshTtsAvailability()
    }

    fun setLanguageB(language: Language) {
        val s = _uiState.value
        if (language == s.languageB) return
        val newA = if (language == s.languageA) s.languageB else s.languageA
        prefs.edit()
            .putString(KEY_LANG_A, newA.code)
            .putString(KEY_LANG_B, language.code)
            .apply()
        _uiState.update { it.copy(languageA = newA, languageB = language) }
        refreshTtsAvailability()
    }

    private fun refreshTtsAvailability() {
        val s = _uiState.value
        _uiState.update {
            it.copy(
                ttsAvailableForA = tts.isAvailable(s.languageA.locale),
                ttsAvailableForB = tts.isAvailable(s.languageB.locale)
            )
        }
    }

    /**
     * Begin recording. In paired+direct-translation mode each mic is bound
     * to a specific target language (A→B vs B→A), and the caller passes
     * [directTarget] to indicate which side's mic was pressed. In all other
     * modes the target is null and direction is decided downstream.
     */
    fun onPushToTalkPressed(directTarget: Language? = null) {
        if (_uiState.value.isRecording) return
        recordingJob = viewModelScope.launch {
            val autoStop = _uiState.value.isAutoStopMic
            _uiState.update {
                it.copy(
                    isRecording = true,
                    status = "Listening...",
                    pendingDirectTarget = directTarget
                )
            }
            audioCapture.startRecording()
            capturedAudio = if (autoStop) {
                audioCapture.collectAudioUntilSilence()
            } else {
                audioCapture.collectAudio()
            }
            if (autoStop) {
                audioCapture.stopRecording()
                finishCapturedAudio(capturedAudio)
            }
        }
    }

    fun onPushToTalkReleased() {
        audioCapture.stopRecording()

        viewModelScope.launch {
            recordingJob?.join()
            finishCapturedAudio(capturedAudio)
        }
    }

    private suspend fun finishCapturedAudio(audio: ByteArray) {
        if (!_uiState.value.isRecording) return
        if (audio.isEmpty() || !isAudioMeaningful(audio)) {
            // Silently return to Ready. Passing near-silent audio to Gemma
            // produces prompt-echo hallucinations like
            // "English: The speaker is using English."
            _uiState.update { it.copy(isRecording = false, status = "Ready") }
            return
        }

        _uiState.update {
            it.copy(
                isRecording = false,
                isProcessing = true,
                status = "Translating..."
            )
        }

        try {
            val s = _uiState.value

                // Direct-translation mode: Gemma 4 AST — audio goes in and
                // translated text comes out in one call. Paired mode defaults
                // to one mic via Gemma's pair-aware direct prompt; explicit
                // direction is an optional higher-control workflow with two
                // target-specific mics.
                if (s.isDirectTranslation) {
                    val (directTarget, directTranslation) = if (s.isAutoDetect) {
                        val target = s.languageA  // auto-detect target = operator's language
                        target to gemma.translateSpeechDirect(audio, target)
                    } else if (s.isExplicitDirection) {
                        val target = s.pendingDirectTarget ?: s.languageB
                        target to gemma.translateSpeechDirect(audio, target)
                    } else {
                        val result = gemma.translateSpeechDirectPaired(
                            audio,
                            pairA = s.languageA,
                            pairB = s.languageB
                        )
                        result.target to result.translation
                    }
                    val directSpokenAloud = tts.isAvailable(directTarget.locale)
                    // Source is whichever of the pair is NOT target. In auto
                    // mode we don't know (could be any language), so leave
                    // null and flag the UI appropriately.
                    val directSource = if (s.isAutoDetect) null
                        else if (directTarget == s.languageB) s.languageA else s.languageB
                    val directSourceName = directSource?.displayName ?: "Any language"
                    val directTurn = ConversationTurn(
                        transcription = "",  // no transcription step in this mode
                        translation = directTranslation,
                        sourceLanguage = directSource,
                        sourceDisplayName = directSourceName,
                        targetLanguage = directTarget,
                        spokenAloud = directSpokenAloud
                    )
                    _uiState.update {
                        it.copy(
                            turns = it.turns + directTurn,
                            status = when {
                                s.isAutoDetect -> "Ready"
                                directSpokenAloud -> "Speaking..."
                                else -> "Ready (no voice installed)"
                            }
                        )
                    }
                    if (directSpokenAloud && !s.isAutoDetect) {
                        tts.speak(directTranslation, directTarget.locale)
                        _uiState.update { it.copy(status = "Ready") }
                    }
                    return
                }

                // Two paths: open auto-detect (ML Kit language ID across ~110
                // languages, always translate to English) vs. binary A/B-anchored
                // detection (paired-mode production default). In auto-detect,
                // [source] can be null when the detected language isn't one of
                // our curated 13 — we still have a display name for the UI,
                // and Gemma can translate most recognized languages to English.
                val source: Language?
                val sourceDisplayName: String
                val target: Language
                val transcription: String
                var outOfPairWarning: String? = null
                var lowConfidence = false
                if (s.isAutoDetect) {
                    transcription = gemma.transcribeOpen(audio)
                    // Open-set detection via ML Kit (~110 languages), falling
                    // back to our hand-rolled Kotlin scorer if ML Kit can't
                    // identify the text confidently.
                    val mlResult = languageId.identify(transcription)
                    when (mlResult) {
                        is ExternalLanguageId.Result.Detected -> {
                            val mapped = Language.fromIso639(mlResult.code)
                            if (mapped != null) {
                                source = mapped
                                sourceDisplayName = mapped.displayName
                            } else {
                                // Recognized language but not in our curated 13
                                // (Czech, Polish, Persian, Turkish, etc.).
                                // Gemma can still translate it to English,
                                // but we haven't verified quality for this set.
                                source = null
                                sourceDisplayName = Language.displayNameForUnmapped(mlResult.code)
                            }
                            // Flag as low-confidence when ML Kit itself isn't
                            // strongly sure — correlates with borderline input
                            // that Gemma is likely also uncertain about.
                            lowConfidence = mlResult.confidence < 0.85f
                        }
                        is ExternalLanguageId.Result.Undetermined,
                        is ExternalLanguageId.Result.Error -> {
                            // Fall back to our scorer. Likely low-confidence;
                            // flag the turn.
                            val (detected, confidence) =
                                Language.detectFromAllLanguagesWithConfidence(transcription)
                            source = detected
                            sourceDisplayName = detected.displayName
                            lowConfidence = confidence < 4
                        }
                    }
                    target = s.languageA  // auto-detect always targets the operator's language

                    // Handoff helper: park the detected source in B so toggling
                    // auto OFF yields a ready-to-converse pair. Only applies when
                    // the detected language is one of our 13 AND we're confident.
                    val inSetSource = source
                    if (inSetSource != null && inSetSource != s.languageA && !lowConfidence) {
                        setLanguageB(inSetSource)
                    }
                } else {
                    val pair = gemma.transcribeAndDetect(
                        audio,
                        candidateA = s.languageA,
                        candidateB = s.languageB
                    )
                    source = pair.first
                    sourceDisplayName = pair.first.displayName
                    transcription = pair.second
                    target = if (s.isExplicitDirection) {
                        s.pendingDirectTarget
                            ?: if (source == s.languageA) s.languageB else s.languageA
                    } else {
                        if (source == s.languageA) s.languageB else s.languageA
                    }

                    // Advisory check: run ML Kit LID on the transcription.
                    // The binary scorer in transcribeAndDetect is constrained
                    // to {A, B} and will pick whichever scores higher even
                    // when neither language actually matches. If ML Kit is
                    // confident the language is NOT one of the configured
                    // pair, surface a warning chip — the source/target/
                    // translation aren't altered, the operator just sees
                    // that the audio probably wasn't in their pair.
                    val lidResult = languageId.identify(transcription)
                    val lidDetected = lidResult as? ExternalLanguageId.Result.Detected
                    val lidLang = lidDetected?.code?.let { Language.fromIso639(it) }
                    val lidConfident = (lidDetected?.confidence ?: 0f) >= 0.5f
                    if (lidConfident && lidDetected != null
                        && lidLang != s.languageA && lidLang != s.languageB
                    ) {
                        outOfPairWarning = lidLang?.displayName
                            ?: Language.displayNameForUnmapped(lidDetected.code)
                    }
                }

                val translation = if (source != null && source == target) transcription
                    else gemma.translate(transcription, sourceDisplayName, target)
                val spokenAloud = tts.isAvailable(target.locale)

                // Out-of-set source: we can translate via Gemma but quality
                // isn't verified for these languages. Flag so the UI warns.
                val qualityUnverified = s.isAutoDetect && source == null

                // Output-side sanity check: run ML Kit on the translation and
                // confirm it's in the target language. Catches Gemma returning
                // source-language text unchanged, or hallucinating a different
                // language entirely. Only runs when we have a translation (i.e.
                // source != target, so gemma.translate was actually invoked).
                val translationSuspect = if (source != target) {
                    val outputCheck = languageId.identify(translation)
                    outputCheck is ExternalLanguageId.Result.Detected
                        && outputCheck.code != target.code
                } else false

                // Structural check: auto-detect mode exists for non-operator
                // input (operator wants to translate what the other party
                // said). If auto-detect identifies the operator's own
                // language as the source, it's almost certainly Gemma's
                // audio encoder hallucinating from unparseable foreign
                // phonemes — observed for Burmese, Farsi, Pashto, etc.
                // Text-level signals can't catch this because the
                // hallucinated text in the operator's language IS valid.
                val unexpectedEnglish = s.isAutoDetect && source == s.languageA

                // Neighbor-language confusability warning: when auto-detect
                // lands on a "sink" language (Hindi, Russian, Arabic, etc.)
                // that Gemma's audio encoder tends to collapse sibling
                // languages into, surface a soft warning. The detection may
                // still be correct — but an operator who expected a close-
                // cousin language (Bengali, Serbian, Persian) should double-
                // check rather than trust the label.
                val confusableSink = s.isAutoDetect
                    && source != null
                    && Language.confusableNeighbors(source).isNotEmpty()

                val turn = ConversationTurn(
                    transcription = transcription,
                    translation = translation,
                    sourceLanguage = source,
                    sourceDisplayName = sourceDisplayName,
                    targetLanguage = target,
                    spokenAloud = spokenAloud,
                    lowConfidence = lowConfidence,
                    qualityUnverified = qualityUnverified,
                    translationSuspect = translationSuspect,
                    unexpectedEnglish = unexpectedEnglish,
                    confusableSink = confusableSink,
                    outOfPairLanguageName = outOfPairWarning
                )
                _uiState.update {
                    it.copy(
                        turns = it.turns + turn,
                        status = when {
                            // Auto-detect mode skips TTS: operator is listening
                            // to the other speaker and doesn't want the app to
                            // talk back. Translation appears on-screen only.
                            s.isAutoDetect -> "Ready"
                            spokenAloud -> "Speaking..."
                            else -> "Ready (no voice installed)"
                        }
                    )
                }

                if (spokenAloud && !s.isAutoDetect) {
                    tts.speak(translation, target.locale)
                    _uiState.update { it.copy(status = "Ready") }
                }
        } catch (e: Exception) {
            _uiState.update { it.copy(status = "Error: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    /**
     * Translate text visible in a picked/captured image. Gemma 4 handles OCR,
     * then Gemma text translation handles the primary translation. ML Kit is
     * retained as a fallback for blank/echoed Gemma translation output.
     */
    /** Gallery/photo-picker entry point. Reads bytes from the URI and delegates. */
    fun processImage(uri: Uri) {
        viewModelScope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("Could not read image")
                }
            } catch (e: Exception) {
                Log.e("TranslatorViewModel", "Image URI read failed", e)
                _uiState.update { it.copy(status = "Error: ${e.message}") }
                return@launch
            }
            processImageBytes(bytes)
        }
    }

    /** Camera-capture entry point. Raw JPEG bytes from the camera flow. */
    fun processImageBytes(bytes: ByteArray) {
        viewModelScope.launch {
            val s0 = _uiState.value
            if (!s0.isModelLoaded || s0.isProcessing) return@launch

            val thumb = withContext(Dispatchers.IO) { makeThumbnail(bytes) }
            val inferenceImage = withContext(Dispatchers.IO) {
                makeInferenceImage(bytes, s0.currentModel)
            }

            // Target = operator's language (A) in both auto and paired
            // modes. The image use case is "foreign sign → my language."
            val target = s0.languageA

            // Seed a placeholder turn with the thumbnail so the user sees
            // the image while Gemma runs. We'll swap in the real translation
            // when the inference returns.
            val placeholder = ConversationTurn(
                transcription = "",
                translation = "(translating...)",
                sourceLanguage = null,
                sourceDisplayName = "Photo",
                targetLanguage = target,
                spokenAloud = false,
                isImageTurn = true,
                thumbnailJpeg = thumb
            )
            val placeholderIndex = _uiState.value.turns.size
            _uiState.update {
                it.copy(
                    turns = it.turns + placeholder,
                    isProcessing = true,
                    status = "Translating image..."
                )
            }

            try {
                // Step 1: Gemma OCR — verbatim text in native script.
                val ocr = gemma.readImage(inferenceImage).trim().trim('"')
                if (ocr.isEmpty() || ocr.equals("(no text)", ignoreCase = true)) {
                    val emptyTurn = placeholder.copy(
                        transcription = "",
                        translation = "(no text detected)"
                    )
                    _uiState.update { s ->
                        val updated = s.turns.toMutableList().also {
                            if (placeholderIndex in it.indices) it[placeholderIndex] = emptyTurn
                        }
                        s.copy(turns = updated, status = "Ready")
                    }
                    return@launch
                }

                // Step 2: detect source language. We always run ML Kit LID
                // (open-set ~110 languages) and then decide:
                //   - Confident + in {A,B}: use LID's pick
                //   - Confident + not in {A,B}: use LID anyway, set the
                //     out-of-pair flag so the UI can warn. ML Kit translate
                //     supports many languages, so the translation usually
                //     still works — we just want to surface that the photo
                //     wasn't in either configured language.
                //   - Auto-detect mode: same logic, no out-of-pair concept
                //     since there is no pair.
                //   - Unsure: fall back to the binary A/B scorer with the
                //     existing tiebreak-to-B rule for short signs.
                val lidResult = languageId.identify(ocr)
                val lidDetected = lidResult as? ExternalLanguageId.Result.Detected
                val lidLang = lidDetected?.code?.let { Language.fromIso639(it) }
                val lidConfident = (lidDetected?.confidence ?: 0f) >= 0.5f

                val detectedSource: Language
                val detectedSourceIso: String
                var outOfPairWarning: String? = null

                if (lidConfident && lidDetected != null) {
                    if (s0.isAutoDetect ||
                        lidLang == s0.languageA ||
                        lidLang == s0.languageB
                    ) {
                        // Confident, and either we're in auto mode (no pair
                        // constraint) or the detected language IS one of A/B.
                        detectedSource = lidLang ?: target
                        detectedSourceIso = lidDetected.code
                    } else {
                        // Confident but the detected language is not in the
                        // configured pair. Use it anyway so the translation
                        // works, and surface a warning chip.
                        detectedSource = lidLang ?: target
                        detectedSourceIso = lidDetected.code
                        outOfPairWarning = lidLang?.displayName
                            ?: Language.displayNameForUnmapped(lidDetected.code)
                    }
                } else if (s0.isAutoDetect) {
                    // ML Kit was unsure and we're in auto mode — fall back
                    // to the broad-scorer pick.
                    val (detected, _) = Language.detectFromAllLanguagesWithConfidence(ocr)
                    detectedSource = detected
                    detectedSourceIso = detected.code
                } else {
                    // Paired mode + LID unsure — binary A/B scorer with the
                    // tiebreak-to-B for short signs (e.g. "ALTO").
                    val scoreA = Language.scoreCandidate(ocr, s0.languageA)
                    val scoreB = Language.scoreCandidate(ocr, s0.languageB)
                    val pick = if (scoreA > scoreB) s0.languageA else s0.languageB
                    detectedSource = pick
                    detectedSourceIso = pick.code
                }

                // Step 3: if source == target, OCR result IS the translation.
                // Otherwise prefer Gemma for the text translation. ML Kit is
                // fast but tends to produce odd literal errors on noisy OCR
                // snippets because it sees only raw text, not task context.
                val translation: String = if (detectedSourceIso.equals(target.code, ignoreCase = true)) {
                    ocr
                } else {
                    val sourceName = outOfPairWarning ?: detectedSource.displayName
                    val gemmaTranslation = runCatching {
                        gemma.translate(ocr, sourceName, target)
                    }.onFailure {
                        Log.w("TranslatorViewModel", "Gemma photo text translation failed", it)
                    }.getOrNull()

                    if (isUsablePhotoTranslation(gemmaTranslation, ocr, target)) {
                        gemmaTranslation!!.trim()
                    } else {
                        val translated = textTranslator.translate(
                            text = ocr,
                            sourceIso = detectedSourceIso,
                            targetIso = target.code
                        )
                        translated ?: gemmaTranslation?.takeIf { it.isNotBlank() }
                            ?: "(translation unavailable)"
                    }
                }

                val finalTurn = placeholder.copy(
                    transcription = ocr,
                    translation = translation,
                    sourceLanguage = detectedSource,
                    sourceDisplayName = outOfPairWarning ?: detectedSource.displayName,
                    outOfPairLanguageName = outOfPairWarning
                )
                _uiState.update { s ->
                    val updated = s.turns.toMutableList().also {
                        if (placeholderIndex in it.indices) it[placeholderIndex] = finalTurn
                    }
                    s.copy(turns = updated, status = "Ready")
                }
            } catch (e: Exception) {
                Log.e("TranslatorViewModel", "Image translation failed", e)
                val errorTurn = placeholder.copy(translation = "Error: ${e.message}")
                _uiState.update { s ->
                    val updated = s.turns.toMutableList().also {
                        if (placeholderIndex in it.indices) it[placeholderIndex] = errorTurn
                    }
                    s.copy(turns = updated, status = "Error: ${e.message}")
                }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    /**
     * Downsample the picked image to a bounded preview JPEG. This is large
     * enough for the tap-to-view photo preview while avoiding full Pixel
     * camera frames in conversation memory.
     */
    private fun makeThumbnail(raw: ByteArray): ByteArray? = try {
        val opts = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
        val targetLongEdge = 1024
        val longEdge = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= targetLongEdge) sample *= 2
        val decodeOpts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOpts)
            ?: return null
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
        bmp.recycle()
        out.toByteArray()
    } catch (t: Throwable) {
        Log.w("TranslatorViewModel", "Thumbnail generation failed", t)
        null
    }

    /**
     * Resize camera/gallery images before passing them to LiteRT-LM. OCR does
     * not need a full Pixel camera frame, and native vision preprocessing can
     * otherwise allocate large transient buffers next to the resident E4B model.
     */
    private fun makeInferenceImage(raw: ByteArray, model: ModelSize): ByteArray = try {
        val bounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val targetLongEdge = when (model) {
            // E2B has enough headroom to keep more detail for dense documents.
            ModelSize.E2B -> 2560
            // E4B is the memory-sensitive path. 1920px still preserves useful
            // OCR detail but avoids full Pixel camera frame allocations next
            // to the resident 3.7 GB model.
            ModelSize.E4B -> 1920
        }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0 || longEdge <= targetLongEdge) return raw

        var sample = 1
        while (longEdge / (sample * 2) >= targetLongEdge) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
            ?: return raw
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        out.toByteArray()
    } catch (t: Throwable) {
        Log.w("TranslatorViewModel", "Inference image resize failed", t)
        raw
    }

    private suspend fun isUsablePhotoTranslation(
        translation: String?,
        sourceText: String,
        target: Language
    ): Boolean {
        val cleaned = translation?.trim().orEmpty()
        if (cleaned.isBlank()) return false
        if (cleaned.equals("(translation unavailable)", ignoreCase = true)) return false
        if (normalizeForEchoCheck(cleaned) == normalizeForEchoCheck(sourceText)) return false

        val outputCheck = languageId.identify(cleaned)
        return outputCheck !is ExternalLanguageId.Result.Detected ||
            outputCheck.code == target.code ||
            outputCheck.confidence < 0.5f
    }

    private fun normalizeForEchoCheck(text: String): String =
        text.lowercase()
            .filter { it.isLetterOrDigit() }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
        tts.close()
        languageId.close()
        textTranslator.close()
    }
}
