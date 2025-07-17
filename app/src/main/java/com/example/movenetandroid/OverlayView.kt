package com.example.movenetandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.util.Log

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var keypoints: Array<FloatArray> = emptyArray()

    private val keypointColors = listOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.CYAN,
        Color.YELLOW, Color.LTGRAY, Color.DKGRAY, Color.rgb(255, 165, 0),
        Color.rgb(128, 0, 128), Color.rgb(0, 128, 128), Color.rgb(255, 20, 147),
        Color.rgb(0, 255, 127), Color.rgb(75, 0, 130), Color.rgb(139, 69, 19),
        Color.rgb(173, 216, 230), Color.rgb(255, 192, 203)
    )

    private val keypointPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(147, 0, 211) // Purple/Violet color
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // MoveNet keypoint pairs for skeleton drawing
    private val jointPairs = listOf(
        0 to 1,  // nose to left eye
        0 to 2,  // nose to right eye
        1 to 3,  // left eye to left ear
        2 to 4,  // right eye to right ear
        0 to 5,  // nose to left shoulder
        0 to 6,  // nose to right shoulder
        5 to 7,  // left shoulder to left elbow
        7 to 9,  // left elbow to left wrist
        6 to 8,  // right shoulder to right elbow
        8 to 10, // right elbow to right wrist
        5 to 6,  // left shoulder to right shoulder
        5 to 11, // left shoulder to left hip
        6 to 12, // right shoulder to right hip
        11 to 12, // left hip to right hip
        11 to 13, // left hip to left knee
        13 to 15, // left knee to left ankle
        12 to 14, // right hip to right knee
        14 to 16  // right knee to right ankle
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (keypoints.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Draw skeleton lines first
        for ((startIdx, endIdx) in jointPairs) {
            if (startIdx < keypoints.size && endIdx < keypoints.size) {
                val startKp = keypoints[startIdx]
                val endKp = keypoints[endIdx]
                
                // Check confidence threshold
                if (startKp[2] > 0.3f && endKp[2] > 0.3f) {
                    // Flip the X coordinate to correct the mirroring
                    val startX = (1f - startKp[1]) * viewWidth
                    val startY = startKp[0] * viewHeight
                    val endX = (1f - endKp[1]) * viewWidth
                    val endY = endKp[0] * viewHeight
                    
                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
            }
        }

        // Draw keypoints
        for ((i, kp) in keypoints.withIndex()) {
            if (kp[2] > 0.3f) { // Confidence threshold
                // Flip the X coordinate to correct the mirroring
                val drawX = (1f - kp[1]) * viewWidth
                val drawY = kp[0] * viewHeight

                keypointPaint.color = keypointColors[i % keypointColors.size]
                canvas.drawCircle(drawX, drawY, 6f, keypointPaint)
            }
        }
    }

    fun updateKeypoints(newKeypoints: Array<FloatArray>) {
        keypoints = newKeypoints
        invalidate()
    }
}
