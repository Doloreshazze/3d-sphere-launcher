package com.playeverywhere.spherelauncher.audio

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A simple heuristic VoiceMatcher.
 * True speaker verification requires complex ML models.
 * This class uses the timing and volume envelope (RMS dB) 
 * provided by SpeechRecognizer to create a basic "Voice Print"
 * based on how the user speaks the words during enrollment.
 */
class VoiceMatcher {

    @Serializable
    data class VoicePrint(
        val wakeWordText: String,
        val appWordText: String,
        val packageName: String,
        val durationMs: Long,
        val rmsEnvelope: List<Float>
    )

    private val enrolledPrints = mutableListOf<VoicePrint>()

    fun enrollVoicePrint(print: VoicePrint) {
        enrolledPrints.add(print)
    }

    fun getEnrolledPrints(): List<VoicePrint> = enrolledPrints.toList()

    fun match(
        spokenText: String,
        durationMs: Long,
        rmsEnvelope: List<Float>
    ): VoicePrint? {
        val lowerSpoken = spokenText.lowercase()

        // 1. First, check if the text contains the wake word and app word
        val matchingPrints = enrolledPrints.filter { print ->
            val wake = print.wakeWordText.trim().lowercase()
            val app = print.appWordText.trim().lowercase()
            // We use contains to allow matching within a larger sentence, e.g. "старт навигатор пожалуйста"
            wake.isNotEmpty() && app.isNotEmpty() && lowerSpoken.contains(wake) && lowerSpoken.contains(app)
        }

        if (matchingPrints.isEmpty()) return null

        // 2. Check the "Voice Print" (timing and RMS envelope heuristic)
        // We will accept it if the duration is within 40% and the envelope shape is vaguely similar.
        for (print in matchingPrints) {
            val durationDiff = abs(print.durationMs - durationMs)
            val maxAllowedDiff = print.durationMs * 0.5 // 50% tolerance for duration

            if (durationDiff <= maxAllowedDiff) {
                if (print.rmsEnvelope.isNotEmpty() && rmsEnvelope.isNotEmpty()) {
                    val similarity = calculateEnvelopeSimilarity(print.rmsEnvelope, rmsEnvelope)
                    if (similarity >= 0.2f) {
                        return print
                    }
                } else {
                    return print
                }
            }
        }

        return null
    }

    internal fun calculateEnvelopeSimilarity(e1: List<Float>, e2: List<Float>): Float {
        if (e1.isEmpty() || e2.isEmpty()) return 1.0f

        val targetSize = 10
        val resampled1 = resample(e1, targetSize)
        val resampled2 = resample(e2, targetSize)

        val max1 = resampled1.maxOrNull() ?: 1f
        val max2 = resampled2.maxOrNull() ?: 1f

        val norm1 = if (max1 > 0f) resampled1.map { it / max1 } else resampled1
        val norm2 = if (max2 > 0f) resampled2.map { it / max2 } else resampled2

        var mse = 0f
        for (i in 0 until targetSize) {
            val diff = norm1[i] - norm2[i]
            mse += diff * diff
        }
        mse /= targetSize

        return (1.0f - mse).coerceIn(0f, 1f)
    }

    private fun resample(input: List<Float>, targetSize: Int): List<Float> {
        if (input.size == targetSize) return input
        val result = FloatArray(targetSize)
        val step = input.size.toFloat() / targetSize.toFloat()
        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceIn(0, input.size - 1)
            result[i] = input[index]
        }
        return result.toList()
    }

    fun clear() {
        enrolledPrints.clear()
    }
}
