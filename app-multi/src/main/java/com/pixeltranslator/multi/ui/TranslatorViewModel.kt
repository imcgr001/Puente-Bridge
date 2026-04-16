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
    val sourceLanguage: Language?,
    val targetLanguage: Language,
    val spokenAloud: Boolean,  // false → TTS voice wasn't available; translation shown as text only
    val lowConfidence: Boolean = false  // auto-detect mode: text-side detector couldn't confidently identify language
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
    }

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val tts = TtsManager(application)
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
                turns = pendingTurns
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
                    put("tgt", t.targetLanguage.code)
                    put("spoken", t.spokenAloud)
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
                val tgt = Language.fromCode(obj.getString("tgt")) ?: Language.ENGLISH
                result.add(
                    ConversationTurn(
                        transcription = obj.getString("transcription"),
                        translation = obj.getString("translation"),
                        sourceLanguage = Language.fromCode(srcCode),
                        targetLanguage = tgt,
                        spokenAloud = obj.getBoolean("spoken")
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

    fun onPushToTalkPressed() {
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true, status = "Listening...") }
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

                // Two paths: open auto-detect (experimental — no language hint
                // to Gemma, score against all 13, always translate to English)
                // vs. binary A/B-anchored detection (production default).
                val source: Language
                val target: Language
                val transcription: String
                var lowConfidence = false
                if (s.isAutoDetect) {
                    transcription = gemma.transcribeOpen(audio)
                    val (detected, confidence) = Language.detectFromAllLanguagesWithConfidence(transcription)
                    source = detected
                    target = Language.ENGLISH
                    // Threshold of 4 catches transliterated out-of-set languages
                    // (Farsi spoken → "salam cheghad" with 0 score) and
                    // undetectable gibberish, without flagging genuine short
                    // English responses that match 1–2 stopwords.
                    lowConfidence = confidence < 4
                    // Handoff helper: park the detected source language in B
                    // (the greyed-out dropdown). When the operator toggles
                    // auto OFF, the pair is already English ↔ that language,
                    // ready for an actual back-and-forth conversation.
                    // Skip if source is just A or confidence too low — no useful pair.
                    if (source != s.languageA && !lowConfidence) {
                        setLanguageB(source)
                    }
                } else {
                    val pair = gemma.transcribeAndDetect(
                        audio,
                        candidateA = s.languageA,
                        candidateB = s.languageB
                    )
                    source = pair.first
                    transcription = pair.second
                    target = if (source == s.languageA) s.languageB else s.languageA
                }

                val translation = if (source == target) transcription
                    else gemma.translate(transcription, source, target)
                val spokenAloud = tts.isAvailable(target.locale)

                val turn = ConversationTurn(
                    transcription = transcription,
                    translation = translation,
                    sourceLanguage = source,
                    targetLanguage = target,
                    spokenAloud = spokenAloud,
                    lowConfidence = lowConfidence
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
    }
}
