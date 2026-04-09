package com.pixeltranslator.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var recorder: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    )

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2
        )
        record.startRecording()
        recorder = record

        val sessionId = record.audioSessionId

        // Attach hardware noise suppression if available
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "NoiseSuppressor enabled")
            }
        } else {
            Log.i(TAG, "NoiseSuppressor not available on this device")
        }

        // Attach hardware echo cancellation if available —
        // suppresses TTS playback from bleeding back into the mic
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AcousticEchoCanceler enabled")
            }
        } else {
            Log.i(TAG, "AcousticEchoCanceler not available on this device")
        }
    }

    /**
     * Reads PCM data from the microphone until [stopRecording] is called.
     * Must be invoked from a coroutine — it suspends on Dispatchers.IO and
     * checks [isActive] each loop iteration for cancellation.
     */
    suspend fun collectAudio(): ByteArray = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(minBufferSize)
        val record = recorder ?: return@withContext ByteArray(0)

        while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                output.write(buffer, 0, read)
            }
        }
        output.toByteArray()
    }

    fun stopRecording() {
        noiseSuppressor?.release()
        noiseSuppressor = null
        echoCanceler?.release()
        echoCanceler = null
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }
}
