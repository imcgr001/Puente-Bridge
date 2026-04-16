package com.pixeltranslator.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixeltranslator.app.audio.AudioCaptureManager
import com.pixeltranslator.app.ml.GemmaTranslatorManager
import com.pixeltranslator.app.ml.GemmaTranslatorManager.ModelSize
import com.pixeltranslator.app.ml.KokoroTTSManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    val currentModel: ModelSize = ModelSize.E2B,
    val needsStoragePermission: Boolean = false
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

    companion object {
        private const val PREFS = "app_translator_prefs"
        private const val KEY_MODEL = "selected_model"
    }

    private val audioCapture = AudioCaptureManager()
    private val gemma = GemmaTranslatorManager(application)
    private val kokoro = KokoroTTSManager(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pendingTurnsFile = File(application.cacheDir, "pending_turns.json")

    private val _uiState = MutableStateFlow(
        run {
            val savedModel = prefs.getString(KEY_MODEL, null)
                ?.let { name -> ModelSize.entries.firstOrNull { it.name == name } }
                ?: ModelSize.E2B
            val pendingTurns = consumePendingTurns()
            TranslatorUiState(
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
                kokoro.initialize()
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
     * drawn from its prompt context.
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

    /**
     * Switches the active model by persisting the choice and restarting the
     * app process. LiteRT-LM's native GPU/KV allocations aren't reliably
     * released in-process even after `close()` + aggressive GC, so an
     * in-session switch from E4B back to E2B reliably native-crashes. Killing
     * and relaunching lets the OS reclaim everything at the process level.
     */
    fun switchModel(model: ModelSize) {
        if (model == _uiState.value.currentModel) return
        if (isLoading) return
        isLoading = true

        prefs.edit().putString(KEY_MODEL, model.name).commit()
        saveTurnsForRestart(_uiState.value.turns)

        _uiState.update {
            it.copy(
                currentModel = model,
                isModelLoaded = false,
                status = "Switching to ${model.label}…"
            )
        }

        viewModelScope.launch {
            delay(400)
            val app = getApplication<Application>()
            val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (intent != null) app.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    /**
     * Caches the current conversation to survive the model-switch process
     * restart. Written only in [switchModel]; consumed and deleted on the
     * next launch. Normal app close / OS kill still wipe history — this is
     * scoped to intentional restarts only.
     */
    private fun saveTurnsForRestart(turns: List<ConversationTurn>) {
        try {
            val arr = JSONArray()
            for (t in turns) {
                arr.put(JSONObject().apply {
                    put("transcription", t.transcription)
                    put("translation", t.translation)
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
                result.add(
                    ConversationTurn(
                        transcription = obj.getString("transcription"),
                        translation = obj.getString("translation")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("TranslatorViewModel", "Failed to restore turns after restart", e)
        } finally {
            pendingTurnsFile.delete()
        }
        return result
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
                // Silently skip — near-silent audio otherwise provokes
                // Gemma into hallucinating from its prompt context.
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
