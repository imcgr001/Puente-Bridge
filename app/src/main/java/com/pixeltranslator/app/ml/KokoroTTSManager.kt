package com.pixeltranslator.app.ml

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * TTS manager using Android's built-in TextToSpeech engine.
 *
 * Requests audio focus before speaking so notifications and other
 * sounds don't interrupt the translation output.
 */
class KokoroTTSManager(private val context: Context) {

    companion object {
        private const val TAG = "TTS"
    }

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    suspend fun initialize() = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                Log.i(TAG, "TextToSpeech initialized.")
            } else {
                Log.e(TAG, "TextToSpeech init failed: $status")
            }
            cont.resume(Unit)
        }
    }

    suspend fun speak(text: String, lang: String = "en", speed: Float = 0.9f) {
        if (!initialized) return
        val engine = tts ?: return

        val locale = if (lang == "es") Locale("es", "ES") else Locale.US
        engine.language = locale
        engine.setSpeechRate(speed)

        // Request audio focus to prevent notifications from interrupting
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

        // Set the same audio attributes on TTS
        engine.setAudioAttributes(attrs)

        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        releaseAudioFocus()
                        cont.resume(Unit)
                    }
                    override fun onError(utteranceId: String?) {
                        releaseAudioFocus()
                        cont.resume(Unit)
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translate_tts")
            }
        }
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
