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
        private const val AUTO_STOP_TRAILING_SILENCE_MS = 1_700
        private const val AUTO_STOP_SPEECH_HANGOVER_MS = 350
        private const val AUTO_STOP_MIN_SPEECH_MS = 400
        private const val AUTO_STOP_MAX_CAPTURE_MS = 30_000
        private const val AUTO_STOP_RMS_THRESHOLD = 220.0
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

    /**
     * Reads PCM until speech has started and then a trailing-silence window is
     * observed. This is intentionally amplitude-based rather than ASR-based:
     * it keeps the mic UX responsive without running another model in parallel
     * with Gemma.
     */
    suspend fun collectAudioUntilSilence(): ByteArray = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(minBufferSize)
        val record = recorder ?: return@withContext ByteArray(0)
        val bytesPerMs = SAMPLE_RATE * 2 / 1000 // 16-bit mono PCM
        val trailingSilenceBytes = AUTO_STOP_TRAILING_SILENCE_MS * bytesPerMs
        val hangoverBytes = AUTO_STOP_SPEECH_HANGOVER_MS * bytesPerMs
        val minSpeechBytes = AUTO_STOP_MIN_SPEECH_MS * bytesPerMs
        val maxCaptureBytes = AUTO_STOP_MAX_CAPTURE_MS * bytesPerMs
        var speechStarted = false
        var speechBytes = 0
        var trailingBytes = 0
        var quietRunBytes = 0
        var totalBytes = 0

        while (isActive &&
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING &&
            totalBytes < maxCaptureBytes
        ) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue

            totalBytes += read
            val speaking = rms(buffer, read) >= AUTO_STOP_RMS_THRESHOLD
            if (speaking) {
                speechStarted = true
                quietRunBytes = 0
                trailingBytes = 0
            } else if (speechStarted) {
                quietRunBytes += read
                if (quietRunBytes > hangoverBytes) {
                    trailingBytes += read
                }
            }

            if (speechStarted) {
                output.write(buffer, 0, read)
                speechBytes += read
            }

            if (speechStarted &&
                speechBytes >= minSpeechBytes &&
                trailingBytes >= trailingSilenceBytes
            ) {
                break
            }
        }
        output.toByteArray()
    }

    private fun rms(buffer: ByteArray, byteCount: Int): Double {
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < byteCount) {
            val lo = buffer[i].toInt() and 0xff
            val hi = buffer[i + 1].toInt()
            val sample = (hi shl 8) or lo
            sumSquares += (sample * sample).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return 0.0
        return Math.sqrt(sumSquares / samples)
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
