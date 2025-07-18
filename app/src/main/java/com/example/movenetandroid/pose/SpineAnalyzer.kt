package com.example.movenetandroid.pose

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class SpineAnalyzer {

    enum class SpineAlignment {
        NEUTRAL,
        SLIGHT_FLEXION,    // Minor rounding (5-15°)
        MODERATE_FLEXION,  // Moderate rounding (15-25°)
        SEVERE_FLEXION,    // Severe rounding (>25°)
        SLIGHT_EXTENSION,  // Minor hyperlordosis (-5 to -15°)
        MODERATE_EXTENSION, // Moderate hyperlordosis (-15 to -25°)
        SEVERE_EXTENSION,  // Severe hyperlordosis (<-25°)
        UNKNOWN
    }

    enum class RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    data class SpineAnalysis(
        val alignment: SpineAlignment,
        val angle: Double,
        val riskLevel: RiskLevel,
        val feedback: String,
        val biomechanicalImpact: String
    )

    // Adjusted biomechanical thresholds - more realistic for real-world movement
    private val neutralThreshold = 20.0      // ±5° tolerance for neutral (more forgiving)
    private val slightFlexionThreshold = 35.0 // 5-15° slight flexion
    private val moderateFlexionThreshold = 45.0 // 15-25° moderate flexion
    private val slightExtensionThreshold = 10.0 // -5 to -15° slight extension
    private val moderateExtensionThreshold = 20.0 // -15 to -25° moderate extension

    private val spineHistory = mutableListOf<SpineAnalysis>()
    private val maxHistory = 5 // Reduced for more responsive feedback

    fun analyzeSpine(
        keypoints: Array<FloatArray>,
        imageHeight: Int,
        imageWidth: Int
    ): SpineAnalysis {
        val leftShoulder = getKeypointCoords(keypoints, 5, imageHeight, imageWidth)
        val rightShoulder = getKeypointCoords(keypoints, 6, imageHeight, imageWidth)
        val leftHip = getKeypointCoords(keypoints, 11, imageHeight, imageWidth)
        val rightHip = getKeypointCoords(keypoints, 12, imageHeight, imageWidth)

        if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
            val shoulderMidpoint = Pair(
                (leftShoulder.first + rightShoulder.first) / 2,
                (leftShoulder.second + rightShoulder.second) / 2
            )
            val hipMidpoint = Pair(
                (leftHip.first + rightHip.first) / 2,
                (leftHip.second + rightHip.second) / 2
            )

            val spineAngle = calculateSpineAngle(shoulderMidpoint, hipMidpoint)

            if (spineAngle != null) {
                val analysis = createSpineAnalysis(spineAngle)

                // Add to history for stability
                spineHistory.add(analysis)
                if (spineHistory.size > maxHistory) {
                    spineHistory.removeAt(0)
                }

                // Return most stable analysis from recent history
                return getStableAnalysis()
            }
        }

        return SpineAnalysis(
            alignment = SpineAlignment.UNKNOWN,
            angle = 0.0,
            riskLevel = RiskLevel.LOW,
            feedback = "Unable to analyze spine position",
            biomechanicalImpact = "Analysis unavailable"
        )
    }

    private fun createSpineAnalysis(angle: Double): SpineAnalysis {
        val absAngle = abs(angle)
        val alignment = when {
            absAngle <= neutralThreshold -> SpineAlignment.NEUTRAL
            absAngle <= slightFlexionThreshold -> SpineAlignment.SLIGHT_FLEXION
            absAngle <= moderateFlexionThreshold -> SpineAlignment.MODERATE_FLEXION
            absAngle > moderateFlexionThreshold -> SpineAlignment.SEVERE_FLEXION
            else -> SpineAlignment.NEUTRAL
        }

        val riskLevel = when (alignment) {
            SpineAlignment.NEUTRAL -> RiskLevel.LOW
            SpineAlignment.SLIGHT_FLEXION, SpineAlignment.SLIGHT_EXTENSION -> RiskLevel.LOW
            SpineAlignment.MODERATE_FLEXION, SpineAlignment.MODERATE_EXTENSION -> RiskLevel.MEDIUM
            SpineAlignment.SEVERE_FLEXION, SpineAlignment.SEVERE_EXTENSION -> RiskLevel.HIGH
            SpineAlignment.UNKNOWN -> RiskLevel.LOW
        }

        val feedback = getFeedback(alignment, angle)
        val biomechanicalImpact = getBiomechanicalImpact(alignment, angle)

        return SpineAnalysis(alignment, angle, riskLevel, feedback, biomechanicalImpact)
    }

    private fun getFeedback(alignment: SpineAlignment, angle: Double): String {
        return when (alignment) {
            SpineAlignment.NEUTRAL -> "Neutral spine maintained"
            SpineAlignment.SLIGHT_FLEXION -> "Minor forward lean"
            SpineAlignment.MODERATE_FLEXION -> "Moderate forward lean - focus on neutral alignment"
            SpineAlignment.SEVERE_FLEXION -> "Excessive forward lean - stop and reset position"
            SpineAlignment.SLIGHT_EXTENSION -> "Minor backward lean"
            SpineAlignment.MODERATE_EXTENSION -> "Moderate backward lean - reduce lumbar extension"
            SpineAlignment.SEVERE_EXTENSION -> "Excessive backward lean - stop and reset position"
            SpineAlignment.UNKNOWN -> "Unable to analyze spine position"
        }
    }

    private fun getBiomechanicalImpact(alignment: SpineAlignment, angle: Double): String {
        val absAngle = abs(angle)
        return when (alignment) {
            SpineAlignment.NEUTRAL -> "Optimal force distribution across spinal structures"
            SpineAlignment.SLIGHT_FLEXION -> "Minor increase in shear forces, monitor closely"
            SpineAlignment.MODERATE_FLEXION -> "Significant shear force increase, passive tissue loading"
            SpineAlignment.SEVERE_FLEXION -> "Critical: High risk of disc herniation, immediate correction needed"
            SpineAlignment.SLIGHT_EXTENSION -> "Minor posterior compressive stress increase"
            SpineAlignment.MODERATE_EXTENSION -> "Moderate posterior annulus compression (~8% increase)"
            SpineAlignment.SEVERE_EXTENSION -> "Critical: High posterior compressive stress (~16%+ increase)"
            SpineAlignment.UNKNOWN -> "Impact assessment unavailable"
        }
    }

    private fun getStableAnalysis(): SpineAnalysis {
        if (spineHistory.isEmpty()) {
            return SpineAnalysis(
                alignment = SpineAlignment.UNKNOWN,
                angle = 0.0,
                riskLevel = RiskLevel.LOW,
                feedback = "No spine data available",
                biomechanicalImpact = "Analysis unavailable"
            )
        }

        // Return the most recent analysis for now
        // Could implement more sophisticated stability algorithms here
        return spineHistory.last()
    }

    private fun calculateSpineAngle(
        shoulderMidpoint: Pair<Int, Int>,
        hipMidpoint: Pair<Int, Int>
    ): Double? {
        val dx = shoulderMidpoint.first - hipMidpoint.first
        val dy = hipMidpoint.second - shoulderMidpoint.second // Y axis: down is positive, so invert
        if (dx == 0 && dy == 0) return null
        val angleRadians = atan2(dx.toDouble(), dy.toDouble())
        val angleDegrees = Math.toDegrees(angleRadians)
        return angleDegrees // 0 = vertical, positive = forward lean, negative = backward lean
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

    fun getSpineHistory(): List<SpineAnalysis> = spineHistory.toList()

    fun clearHistory() {
        spineHistory.clear()
    }
}