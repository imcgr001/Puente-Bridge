package com.pixeltranslator.app.ml

import android.content.Context
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
 * Supports English and Spanish with proper pronunciation out of the box.
 * Named KokoroTTSManager to keep the existing wiring — can be swapped
 * back to ONNX Kokoro once espeak-ng is bundled for Android.
 */
class KokoroTTSManager(private val context: Context) {

    companion object {
        private const val TAG = "TTS"
    }

    private var tts: TextToSpeech? = null
    private var initialized = false

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

        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { cont.resume(Unit) }
                    override fun onError(utteranceId: String?) { cont.resume(Unit) }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translate_tts")
            }
        }
    }

    fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
