package com.example.movenetandroid.pose

class RepetitionCounter {
    private var repetitionCount = 0
    private var achievedGoodDepth = false
    private var onRepetitionCountChanged: ((Int) -> Unit)? = null

    fun updatePhase(phase: SquatDepthAnalyzer.Phase, feedback: String) {
        when (phase) {
            SquatDepthAnalyzer.Phase.BOTTOM -> {
                if (feedback.contains("Good Squat Depth")) {
                    achievedGoodDepth = true
                }
            }
            SquatDepthAnalyzer.Phase.STANDING -> {
                if (achievedGoodDepth) {
                    repetitionCount++
                    achievedGoodDepth = false
                    onRepetitionCountChanged?.invoke(repetitionCount)
                }
            }
            else -> {
            }
        }
    }

    fun getRepetitionCount(): Int = repetitionCount

    fun resetCount() {
        repetitionCount = 0
        achievedGoodDepth = false
        onRepetitionCountChanged?.invoke(repetitionCount)
    }

    fun setOnRepetitionCountChangedListener(listener: (Int) -> Unit) {
        onRepetitionCountChanged = listener
    }
} 