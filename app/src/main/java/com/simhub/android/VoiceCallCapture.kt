package com.simhub.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

/**
 * Tries every audio source that *might* expose real call audio on this specific device.
 * Not all Android builds block this — Pixel and recent Samsung generally do,
 * many other OEMs (older Samsung, Xiaomi, Vivo, Oppo, etc.) don't.
 * This tells us definitively whether YOUR phone is one of the ones that works,
 * before we invest in wiring it into the live WebRTC audio path.
 *
 * Run this while a REAL call is actually active (answered, both sides talking) —
 * testing with no call in progress will always report silence/failure.
 */
object VoiceCallCapture {

    data class TestResult(
        val source: String,
        val initialized: Boolean,
        val gotNonSilentAudio: Boolean,
        val averageAmplitude: Int
    )

    private val candidateSources = listOf(
        "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
        "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
        "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION
    )

    @SuppressLint("MissingPermission")
    fun runAllTests(durationMs: Long = 3000): List<TestResult> {
        return candidateSources.map { (label, source) -> testSource(label, source, durationMs) }
    }

    @SuppressLint("MissingPermission")
    private fun testSource(label: String, source: Int, durationMs: Long): TestResult {
        val sampleRate = 8000 // standard telephony sample rate
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            return TestResult(label, initialized = false, gotNonSilentAudio = false, averageAmplitude = 0)
        }

        val recorder = try {
            AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize)
        } catch (e: Exception) {
            return TestResult(label, initialized = false, gotNonSilentAudio = false, averageAmplitude = 0)
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return TestResult(label, initialized = false, gotNonSilentAudio = false, averageAmplitude = 0)
        }

        val buffer = ShortArray(minBufferSize)
        var totalAmplitude = 0L
        var sampleCount = 0

        try {
            recorder.startRecording()
            val endTime = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < endTime) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        totalAmplitude += abs(buffer[i].toInt())
                        sampleCount++
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        val avgAmplitude = if (sampleCount > 0) (totalAmplitude / sampleCount).toInt() else 0
        // Anything above a small noise floor means we're actually hearing something,
        // not just silence/zeros (which is what a blocked source usually returns).
        val gotAudio = avgAmplitude > 50

        return TestResult(label, initialized = true, gotNonSilentAudio = gotAudio, averageAmplitude = avgAmplitude)
    }
}
