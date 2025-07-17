package com.example.movenetandroid.pose

class RepetitionCounter {
    private var repetitionCount = 0
    private var achievedGoodDepth = false
    private var onRepetitionCountChanged: ((Int) -> Unit)? = null
    
    fun updatePhase(phase: SquatAnalyzer.Phase, feedback: String) {
        when (phase) {
            SquatAnalyzer.Phase.BOTTOM -> {
                // Check if user achieved good depth
                if (feedback.contains("Good Squat Depth")) {
                    achievedGoodDepth = true
                }
            }
            SquatAnalyzer.Phase.STANDING -> {
                // Count rep if user achieved good depth and is now standing
                if (achievedGoodDepth) {
                    repetitionCount++
                    achievedGoodDepth = false
                    onRepetitionCountChanged?.invoke(repetitionCount)
                }
            }
            else -> {
                // For other phases, don't change the state
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