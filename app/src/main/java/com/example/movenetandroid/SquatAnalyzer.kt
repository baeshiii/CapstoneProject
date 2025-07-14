package com.example.movenetandroid

class SquatAnalyzer {

    enum class Phase {
        DESCENDING, BOTTOM, ASCENDING, NONE
    }

    private var squatPhase = Phase.NONE
    private var phaseFrames = 0

    fun analyzeDepth(
        keypoints: List<PoseDetectorHelper.Keypoint>,
        imageHeight: Int,
        imageWidth: Int
    ): List<String> {
        val issues = mutableListOf<String>()

        val leftHip = getCoords(keypoints, 11, imageHeight, imageWidth)
        val rightHip = getCoords(keypoints, 12, imageHeight, imageWidth)
        val leftKnee = getCoords(keypoints, 13, imageHeight, imageWidth)
        val rightKnee = getCoords(keypoints, 14, imageHeight, imageWidth)

        if (leftHip != null && rightHip != null && leftKnee != null && rightKnee != null) {
            val hipCenterY = (leftHip.second + rightHip.second) / 2f
            val kneeCenterY = (leftKnee.second + rightKnee.second) / 2f
            val depthRatio = (hipCenterY - kneeCenterY) / imageHeight.toFloat()

            val shouldAnalyze = squatPhase == Phase.BOTTOM ||
                    (squatPhase == Phase.DESCENDING && (
                            kotlin.math.abs(hipCenterY - kneeCenterY) < 50 || phaseFrames > 10
                            ))

            if (shouldAnalyze) {
                val tolerance = 0.05f * imageHeight

                if (hipCenterY < kneeCenterY) {
                    issues.add("❌ Squat depth insufficient: Go deeper.")
                } else if (kotlin.math.abs(hipCenterY - kneeCenterY) <= tolerance) {
                    // ✅ Good depth
                } else {
                    // ✅ Deep squat
                }
            }
        }

        return issues
    }

    private fun getCoords(
        keypoints: List<PoseDetectorHelper.Keypoint>,
        index: Int,
        imageHeight: Int,
        imageWidth: Int
    ): Pair<Float, Float>? {
        return if (index < keypoints.size && keypoints[index].score > 0.5f) {
            val x = keypoints[index].x / 192f * imageWidth
            val y = keypoints[index].y / 192f * imageHeight
            Pair(x, y)
        } else null
    }

    fun setSquatPhase(phase: Phase) {
        if (phase == squatPhase) {
            phaseFrames++
        } else {
            squatPhase = phase
            phaseFrames = 1
        }
    }
}