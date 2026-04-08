package com.pixeltranslator.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AudioCaptureManager {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var recorder: AudioRecord? = null

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
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }
}
