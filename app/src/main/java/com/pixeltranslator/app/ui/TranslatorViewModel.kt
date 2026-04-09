package com.pixeltranslator.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixeltranslator.app.audio.AudioCaptureManager
import com.pixeltranslator.app.ml.GemmaTranslatorManager
import com.pixeltranslator.app.ml.GemmaTranslatorManager.ModelSize
import com.pixeltranslator.app.ml.KokoroTTSManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationTurn(
    val transcription: String,
    val translation: String
)

data class TranslatorUiState(
    val status: String = "Initializing...",
    val turns: List<ConversationTurn> = emptyList(),
    val isRecording: Boolean = false,
    val isModelLoaded: Boolean = false,
    val showDisclaimer: Boolean = false,
    val currentModel: ModelSize = ModelSize.E2B
)

const val DISCLAIMER_TEXT =
    "Esta es una herramienta de traducci\u00f3n con inteligencia artificial. " +
    "Las traducciones pueden contener errores. Si tiene alguna inquietud, " +
    "por favor h\u00e1gala saber y se le proporcionar\u00e1 un int\u00e9rprete humano.\n\n" +
    "C\u00f3mo funciona la conversaci\u00f3n: la otra persona hablar\u00e1 y luego " +
    "la herramienta traducir\u00e1 en voz alta. Cuando est\u00e9n listos para " +
    "que usted hable, le apuntar\u00e1n con el tel\u00e9fono y podr\u00e1 hablar " +
    "hasta que termine. Intente ser breve y conciso.\n\n" +
    "Si en alg\u00fan momento desea un int\u00e9rprete humano, simplemente " +
    "d\u00edgalo cuando sea su turno para hablar.\n\n" +
    "Nada de esta conversaci\u00f3n se transmite por internet. Todo se " +
    "realiza en el tel\u00e9fono o dispositivo presente. Su voz y " +
    "esta conversaci\u00f3n nunca se graban ni se almacenan. Se eliminan " +
    "de inmediato."

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val kokoro = KokoroTTSManager(application)

    private val _uiState = MutableStateFlow(TranslatorUiState())
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var capturedAudio: ByteArray = ByteArray(0)

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(status = "Loading models...") }
            try {
                gemma.initialize()
                kokoro.initialize()
                _uiState.update { it.copy(status = "Ready", isModelLoaded = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = "Model load failed: ${e.message}") }
            }
        }
    }

    fun showDisclaimer() {
        _uiState.update { it.copy(showDisclaimer = true) }
    }

    fun playDisclaimer() {
        viewModelScope.launch {
            kokoro.speak(DISCLAIMER_TEXT, lang = "es")
        }
    }

    fun dismissDisclaimer() {
        _uiState.update { it.copy(showDisclaimer = false) }
    }

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
                val result = gemma.translateSpeech(audio) { /* partial results */ }

                val turn = ConversationTurn(
                    transcription = result.transcription,
                    translation = result.translation
                )
                _uiState.update {
                    it.copy(
                        turns = it.turns + turn,
                        status = "Speaking..."
                    )
                }

                // TTS the translation
                kokoro.speak(result.translation, lang = result.targetLanguage)
                _uiState.update { it.copy(status = "Ready") }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = "Error: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
        kokoro.close()
    }
}
