package com.example.movenetandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.util.Log

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var keypoints: List<PoseDetectorHelper.Keypoint> = emptyList()

    // Paint for drawing keypoints (dots)
    private val keypointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    // Paint for drawing skeleton lines
    private val linePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // Define the joint pairs to connect with lines (indices based on MoveNet's 17 keypoints)
    private val jointPairs = listOf(
        0 to 1,  // Nose - Left Eye
        0 to 2,  // Nose - Right Eye
        1 to 3,  // Left Eye - Left Ear
        2 to 4,  // Right Eye - Right Ear
        0 to 5,  // Nose - Left Shoulder
        0 to 6,  // Nose - Right Shoulder
        5 to 7,  // Left Shoulder - Left Elbow
        7 to 9,  // Left Elbow - Left Wrist
        6 to 8,  // Right Shoulder - Right Elbow
        8 to 10, // Right Elbow - Right Wrist
        5 to 6,  // Left Shoulder - Right Shoulder
        5 to 11, // Left Shoulder - Left Hip
        6 to 12, // Right Shoulder - Right Hip
        11 to 12,// Left Hip - Right Hip
        11 to 13,// Left Hip - Left Knee
        13 to 15,// Left Knee - Left Ankle
        12 to 14,// Right Hip - Right Knee
        14 to 16 // Right Knee - Right Ankle
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The model outputs keypoints in a 192x192 space, so compute scaling factors:
        val scaleX = width / 192f
        val scaleY = height / 192f

        // Draw skeleton lines between connected joints
        for ((startIdx, endIdx) in jointPairs) {
            if (startIdx in keypoints.indices && endIdx in keypoints.indices &&
                keypoints[startIdx].score > 0.4f && keypoints[endIdx].score > 0.4f) {
                val startX = keypoints[startIdx].x * scaleX
                val startY = keypoints[startIdx].y * scaleY
                val endX = keypoints[endIdx].x * scaleX
                val endY = keypoints[endIdx].y * scaleY
                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }

        // Draw each keypoint as a circle
        for ((i, kp) in keypoints.withIndex()) {
            if (kp.score > 0.4f && !kp.x.isNaN() && !kp.y.isNaN()) {
                val drawX = kp.x * scaleX
                val drawY = kp.y * scaleY
                canvas.drawCircle(drawX, drawY, 10f, keypointPaint)
                Log.d("OverlayView", "Draw keypoint [$i] at ($drawX, $drawY) score=${kp.score}")
            }
        }
    }

    fun updateKeypoints(newKeypoints: List<PoseDetectorHelper.Keypoint>) {
        keypoints = newKeypoints
        invalidate()
    }
}
