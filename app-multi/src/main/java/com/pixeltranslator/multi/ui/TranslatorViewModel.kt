package com.pixeltranslator.multi.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
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
    val lowConfidence: Boolean = false,       // detector confidence below threshold
    val qualityUnverified: Boolean = false,   // source is outside our curated 13 (ML-Kit-only support)
    val translationSuspect: Boolean = false,  // output-side sanity check failed — translation ≠ target language
    val unexpectedEnglish: Boolean = false,   // auto-detect identified English — likely audio-encoder hallucination from a non-English speaker
    val confusableSink: Boolean = false       // auto-detect identified a language that often absorbs its neighbors (Hindi, Russian, Arabic, etc.)
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
    }

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val tts = TtsManager(application)
    private val languageId = ExternalLanguageId()
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
                isDirectTranslation = prefs.getBoolean(KEY_DIRECT_TRANSLATION, false)
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
                _uiState.update { it.copy(status = "Ready", isModelLoaded = true) }
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
                    put("lowConf", t.lowConfidence)
                    put("qualUnv", t.qualityUnverified)
                    put("trSusp", t.translationSuspect)
                    put("unexEn", t.unexpectedEnglish)
                    put("confSink", t.confusableSink)
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
                result.add(
                    ConversationTurn(
                        transcription = obj.getString("transcription"),
                        translation = obj.getString("translation"),
                        sourceLanguage = srcLang,
                        sourceDisplayName = srcName,
                        targetLanguage = tgt,
                        spokenAloud = obj.getBoolean("spoken"),
                        lowConfidence = obj.optBoolean("lowConf", false),
                        qualityUnverified = obj.optBoolean("qualUnv", false),
                        translationSuspect = obj.optBoolean("trSusp", false),
                        unexpectedEnglish = obj.optBoolean("unexEn", false),
                        confusableSink = obj.optBoolean("confSink", false)
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
        recordingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecording = true,
                    status = "Listening...",
                    pendingDirectTarget = directTarget
                )
            }
            audioCapture.startRecording()
            capturedAudio = audioCapture.collectAudio()
        }
    }

    fun onPushToTalkReleased() {
        audioCapture.stopRecording()

        viewModelScope.launch {
            recordingJob?.join()
            val audio = capturedAudio
            if (audio.isEmpty() || !isAudioMeaningful(audio)) {
                // Silently return to Ready. Passing near-silent audio to Gemma
                // produces prompt-echo hallucinations like
                // "English: The speaker is using English."
                _uiState.update { it.copy(isRecording = false, status = "Ready") }
                return@launch
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

                // Direct-translation mode: Gemma 4 AST — audio goes in with
                // a target-language prompt and translated text comes out in
                // one call. ~2× faster than the transcribe+translate path,
                // but we have no source transcription to show and must
                // commit to the target up front. In auto-detect we pick
                // English; in paired mode we pick B (user flips A/B to
                // change direction).
                if (s.isDirectTranslation) {
                    // Direct-mode direction:
                    //   auto on  → always English (fixed-target AST).
                    //   paired   → target was chosen by which mic the speaker
                    //              pressed (A→B or B→A). pendingDirectTarget
                    //              holds that choice; fall back to B if
                    //              somehow unset.
                    val directTarget: Language = if (s.isAutoDetect) {
                        Language.ENGLISH
                    } else {
                        s.pendingDirectTarget ?: s.languageB
                    }
                    val directTranslation = gemma.translateSpeechDirect(audio, directTarget)
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
                    return@launch
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
                    target = Language.ENGLISH

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
                    target = if (source == s.languageA) s.languageB else s.languageA
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

                // Structural check: auto-detect mode exists for non-English
                // input (operator speaks English, wants to translate what the
                // other party said). If auto-detect identifies English, it's
                // almost certainly Gemma's audio encoder hallucinating from
                // unparseable foreign phonemes — we've seen this fire for
                // Burmese ("Hello Nicole, I'm a Transcribe."), Farsi phonetic
                // approximations, Pashto, etc. Text-level signals can't catch
                // this because the hallucinated English IS valid English.
                val unexpectedEnglish = s.isAutoDetect && source == Language.ENGLISH

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
                    confusableSink = confusableSink
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
    }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
        tts.close()
        languageId.close()
    }
}
