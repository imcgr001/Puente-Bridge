package com.pixeltranslator.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixeltranslator.app.audio.AudioCaptureManager
import com.pixeltranslator.app.ml.GemmaTranslatorManager
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
    val isModelLoaded: Boolean = false
)

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
