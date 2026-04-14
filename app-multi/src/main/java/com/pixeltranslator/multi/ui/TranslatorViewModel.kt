package com.pixeltranslator.multi.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
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
    val spokenAloud: Boolean  // false → TTS voice wasn't available; translation shown as text only
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
    val needsStoragePermission: Boolean = false
)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS = "multi_translator_prefs"
        private const val KEY_LANG_A = "language_a_code"
        private const val KEY_LANG_B = "language_b_code"
    }

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val tts = TtsManager(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        run {
            val a = Language.fromCode(prefs.getString(KEY_LANG_A, null)) ?: Language.ENGLISH
            val b = Language.fromCode(prefs.getString(KEY_LANG_B, null))
                ?: if (a != Language.SPANISH) Language.SPANISH else Language.FRENCH
            TranslatorUiState(languageA = a, languageB = b)
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
                gemma.initialize()
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

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            true

    fun clearConversation() {
        _uiState.update { it.copy(turns = emptyList()) }
    }

    fun switchModel(model: ModelSize) {
        if (model == _uiState.value.currentModel) return
        viewModelScope.launch {
            _uiState.update { it.copy(isModelLoaded = false, status = "Loading ${model.label}...") }
            try {
                gemma.initialize(model)
                _uiState.update { it.copy(isModelLoaded = true, status = "Ready", currentModel = model) }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = "Error: ${e.message}") }
            }
        }
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
            if (audio.isEmpty()) {
                _uiState.update { it.copy(isRecording = false, status = "Ready") }
                return@launch
            }

            _uiState.update { it.copy(isRecording = false, status = "Translating...") }

            try {
                // Transcribe first (detects source), then pick the target as
                // "whichever of A/B is NOT the source". If detection lands
                // outside the pair, default to A.
                val s = _uiState.value
                val (source, transcription) = gemma.transcribeAndDetect(
                    audio,
                    candidateA = s.languageA,
                    candidateB = s.languageB
                )
                val target = if (source == s.languageA) s.languageB else s.languageA

                val translation = gemma.translate(transcription, source, target)
                val spokenAloud = tts.isAvailable(target.locale)

                val turn = ConversationTurn(
                    transcription = transcription,
                    translation = translation,
                    sourceLanguage = source,
                    targetLanguage = target,
                    spokenAloud = spokenAloud
                )
                _uiState.update {
                    it.copy(
                        turns = it.turns + turn,
                        status = if (spokenAloud) "Speaking..." else "Ready (no voice installed)"
                    )
                }

                if (spokenAloud) {
                    tts.speak(translation, target.locale)
                    _uiState.update { it.copy(status = "Ready") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = "Error: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
        tts.close()
    }
}
