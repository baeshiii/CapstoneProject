package com.example.movenetandroid.feedback

object FeedbackUtils {
    fun getSquatFeedback(phase: String, angle: Double?): String {
        return when (phase) {
            "STANDING" -> "Start your squat."
            "DESCENDING" -> "Lower down slowly."
            // If angle is null, can't determine depth. If angle > 90, above parallel. If angle <= 90, at or below parallel.
            "BOTTOM" -> when {
                angle == null -> "Keep going!"
                angle > 90 -> "Go a bit deeper."
                else -> "Good depth!"
            }
            "ASCENDING" -> "Stand up with control."
            else -> "Keep going!"
        }
    }
} 