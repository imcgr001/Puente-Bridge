package com.pixeltranslator.multi.ml

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * TTS wrapper around Android's built-in [TextToSpeech]. Checks language
 * availability per-locale and exposes it to callers so the UI can show a
 * text-only fallback when a voice isn't installed.
 */
class TtsManager(private val context: Context) {

    companion object {
        private const val TAG = "TTS"
    }

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    suspend fun initialize() = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (!initialized) Log.e(TAG, "TextToSpeech init failed: $status")
            cont.resume(Unit)
        }
    }

    /**
     * Returns true if the given locale has an installed voice on this device.
     * Values of [TextToSpeech.LANG_MISSING_DATA] and [TextToSpeech.LANG_NOT_SUPPORTED]
     * both indicate the language can't be spoken.
     */
    fun isAvailable(locale: Locale): Boolean {
        val engine = tts ?: return false
        val result = engine.isLanguageAvailable(locale)
        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun stop() {
        tts?.stop()
        releaseAudioFocus()
    }

    suspend fun speak(text: String, locale: Locale, speed: Float = 0.95f): Boolean {
        if (!initialized) return false
        val engine = tts ?: return false
        if (!isAvailable(locale)) return false

        engine.language = locale
        engine.setSpeechRate(speed)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
        engine.setAudioAttributes(attrs)

        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val completed = AtomicBoolean(false)
                fun complete() {
                    if (completed.compareAndSet(false, true)) {
                        releaseAudioFocus()
                        cont.resume(Unit)
                    }
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = complete()
                    @Deprecated("Android framework still requires this callback override")
                    override fun onError(utteranceId: String?) = complete()
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = complete()
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translate_tts")
            }
        }
        return true
    }

    private fun releaseAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        releaseAudioFocus()
    }
}
