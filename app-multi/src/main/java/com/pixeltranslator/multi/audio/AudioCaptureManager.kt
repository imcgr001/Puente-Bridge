package com.pixeltranslator.multi.audio

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

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "NoiseSuppressor enabled")
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AcousticEchoCanceler enabled")
            }
        }
    }

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
