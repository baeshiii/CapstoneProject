package com.example.movenetandroid.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2

class SquatDepthAnalyzer {
    enum class Phase { STANDING, DESCENDING, BOTTOM, ASCENDING, UNKNOWN }

    enum class SpineAlignment {
        NEUTRAL,
        FLEXED,
        EXTENDED,
        UNKNOWN
    }

    private var squatPhase: Phase = Phase.STANDING
    private var phaseFrames: Int = 0
    private val hipPositions = mutableListOf<Float>()
    private val maxHistory = 10
    private val movementThreshold = 5f
    private val standingThreshold = 50f // Hip well above knee
    private val bottomThreshold = 30f    // Hip at or below knee

    // Spine analyzer integration
    private val spineAnalyzer = SpineAnalyzer()

    fun detectSquatPhase(
        keypoints: Array<FloatArray>,
        imageHeight: Int,
        imageWidth: Int
    ): Pair<Phase, Float> {
        // Get keypoint coordinates
        val leftHip = getKeypointCoords(keypoints, 11, imageHeight, imageWidth)
        val rightHip = getKeypointCoords(keypoints, 12, imageHeight, imageWidth)
        val leftKnee = getKeypointCoords(keypoints, 13, imageHeight, imageWidth)
        val rightKnee = getKeypointCoords(keypoints, 14, imageHeight, imageWidth)
        val leftAnkle = getKeypointCoords(keypoints, 15, imageHeight, imageWidth)
        val rightAnkle = getKeypointCoords(keypoints, 16, imageHeight, imageWidth)

        if (leftHip != null && rightHip != null && leftKnee != null && rightKnee != null && leftAnkle != null && rightAnkle != null) {
            // Calculate knee angles
            val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
            val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
            // Define a threshold for "standing" (e.g., 165 degrees)
            val standingAngleThreshold = 165.0
            val isStanding = (leftKneeAngle != null && leftKneeAngle > standingAngleThreshold) &&
                    (rightKneeAngle != null && rightKneeAngle > standingAngleThreshold)

            val hipY = (leftHip.second + rightHip.second) / 2f
            val kneeY = (leftKnee.second + rightKnee.second) / 2f
            val hipKneeDiff = hipY - kneeY
            hipPositions.add(hipY)
            if (hipPositions.size > maxHistory) hipPositions.removeAt(0)

            // Use new standing detection
            val isBottom = !isStanding && hipKneeDiff <= bottomThreshold && isAtBottom()

            val newPhase = when {
                isStanding -> Phase.STANDING
                isBottom -> Phase.BOTTOM
                else -> squatPhase // MOVING or keep previous
            }

            if (newPhase == squatPhase) phaseFrames++ else { squatPhase = newPhase; phaseFrames = 0 }

            return Pair(newPhase, hipKneeDiff)
        }
        return Pair(Phase.UNKNOWN, 0f)
    }

    // Enhanced feedback function that includes comprehensive spine analysis
    fun getComprehensiveFeedback(
        phase: Phase,
        hipY: Float,
        kneeY: Float,
        keypoints: Array<FloatArray>,
        imageHeight: Int,
        imageWidth: Int
    ): String {
        val baseFeedback = getSquatFeedback(phase, hipY, kneeY)

        // Only show spine analysis when actually squatting (not standing)
        if (phase == Phase.STANDING) {
            return baseFeedback
        }

        // Get spine analysis only during squat movement
        val spineAnalysis = spineAnalyzer.analyzeSpine(keypoints, imageHeight, imageWidth)

        // Only include spine feedback if we have valid analysis
        return if (spineAnalysis.alignment != SpineAnalyzer.SpineAlignment.UNKNOWN) {
            val spineFeedback = when (spineAnalysis.riskLevel) {
                SpineAnalyzer.RiskLevel.LOW -> "✓ ${spineAnalysis.feedback}"
                SpineAnalyzer.RiskLevel.MEDIUM -> "⚠️ ${spineAnalysis.feedback}"
                SpineAnalyzer.RiskLevel.HIGH -> "🚨 ${spineAnalysis.feedback}"
                SpineAnalyzer.RiskLevel.CRITICAL -> "🚨 ${spineAnalysis.feedback}"
            }
            "$baseFeedback | $spineFeedback"
        } else {
            baseFeedback
        }
    }

    // Get detailed spine analysis for advanced feedback
    fun getDetailedSpineAnalysis(
        keypoints: Array<FloatArray>,
        imageHeight: Int,
        imageWidth: Int
    ): SpineAnalyzer.SpineAnalysis {
        return spineAnalyzer.analyzeSpine(keypoints, imageHeight, imageWidth)
    }

    // New feedback function using hipY and kneeY
    fun getSquatFeedback(phase: Phase, hipY: Float, kneeY: Float): String {
        if (phase == Phase.BOTTOM) {
            return if (hipY >= kneeY) {
                "Good Squat Depth!"
            } else {
                "Go Lower!"
            }
        }
        return "Start Squatting"
    }

    // Helper to check if current hip position is a local minimum (bottom)
    private fun isAtBottom(): Boolean {
        if (hipPositions.size < 3) return false
        val last = hipPositions.last()
        val prev = hipPositions[hipPositions.size - 2]
        val prev2 = hipPositions[hipPositions.size - 3]
        // Local minimum: previous was descending, now stable or ascending
        return (prev > last && prev2 > prev) || (prev < last && prev2 < prev)
    }

    private fun detectMovementDirection(): String {
        if (hipPositions.size < 3) return "unknown"
        val recent = hipPositions.takeLast(5)
        if (recent.size < 3) return "unknown"
        var totalMovement = 0f
        for (i in 1 until recent.size) {
            totalMovement += recent[i] - recent[i - 1]
        }
        val avgMovement = totalMovement / (recent.size - 1)
        return when {
            avgMovement > movementThreshold -> "descending"
            avgMovement < -movementThreshold -> "ascending"
            else -> "stable"
        }
    }

    private fun getKeypointCoords(
        keypoints: Array<FloatArray>,
        index: Int,
        imageHeight: Int,
        imageWidth: Int
    ): Pair<Int, Int>? {
        return if (keypoints[index][2] > 0.15f) {
            val x = (keypoints[index][1] * imageWidth).toInt()
            val y = (keypoints[index][0] * imageHeight).toInt()
            Pair(x, y)
        } else null
    }

    fun calculateAngle(p1: Pair<Int, Int>?, p2: Pair<Int, Int>?, p3: Pair<Int, Int>?): Double? {
        if (p1 == null || p2 == null || p3 == null) return null
        val v1x = (p1.first - p2.first).toDouble()
        val v1y = (p1.second - p2.second).toDouble()
        val v2x = (p3.first - p2.first).toDouble()
        val v2y = (p3.second - p2.second).toDouble()
        val dot = v1x * v2x + v1y * v2y
        val norm1 = sqrt(v1x * v1x + v1y * v1y)
        val norm2 = sqrt(v2x * v2x + v2y * v2y)
        val cosAngle = (dot / (norm1 * norm2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }
} 