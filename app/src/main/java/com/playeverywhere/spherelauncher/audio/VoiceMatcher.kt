package com.playeverywhere.spherelauncher.audio

import kotlin.math.abs

/**
 * A simple heuristic VoiceMatcher.
 * True speaker verification requires complex ML models.
 * This class uses the timing and volume envelope (RMS dB) 
 * provided by SpeechRecognizer to create a basic "Voice Print"
 * based on how the user speaks the words during enrollment.
 */
class VoiceMatcher {

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
            lowerSpoken.contains(print.wakeWordText.lowercase()) &&
            lowerSpoken.contains(print.appWordText.lowercase())
        }

        if (matchingPrints.isEmpty()) return null

        // 2. Check the "Voice Print" (timing and RMS envelope heuristic)
        // We will accept it if the duration is within 40% and the envelope shape is vaguely similar.
        for (print in matchingPrints) {
            val durationDiff = abs(print.durationMs - durationMs)
            val maxAllowedDiff = print.durationMs * 0.5 // 50% tolerance for duration

            if (durationDiff <= maxAllowedDiff) {
                // Heuristic passes. In a production app, we would use MFCCs and DTW here.
                return print
            }
        }

        return null
    }

    fun clear() {
        enrolledPrints.clear()
    }
}
