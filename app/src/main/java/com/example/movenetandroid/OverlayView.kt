package com.example.movenetandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.util.Log

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // Change keypoints type to Array<FloatArray>
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
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val jointPairs = listOf(
        0 to 1, 0 to 2, 1 to 3, 2 to 4,
        0 to 5, 0 to 6, 5 to 7, 7 to 9,
        6 to 8, 8 to 10, 5 to 6, 5 to 11,
        6 to 12, 11 to 12, 11 to 13, 13 to 15,
        12 to 14, 14 to 16
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val modelInputSize = 192f

        val scale = minOf(viewWidth / modelInputSize, viewHeight / modelInputSize)
        val offsetX = (viewWidth - modelInputSize * scale) / 2
        val offsetY = (viewHeight - modelInputSize * scale) / 2

        for ((startIdx, endIdx) in jointPairs) {
            if (
                startIdx in keypoints.indices && endIdx in keypoints.indices &&
                keypoints[startIdx][2] > 0.4f && keypoints[endIdx][2] > 0.4f
            ) {
                val startX = keypoints[startIdx][1] * scale + offsetX
                val startY = keypoints[startIdx][0] * scale + offsetY
                val endX = keypoints[endIdx][1] * scale + offsetX
                val endY = keypoints[endIdx][0] * scale + offsetY
                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }

        // Draw each keypoint and label
        for ((i, kp) in keypoints.withIndex()) {
            if (kp[2] > 0.4f && !kp[1].isNaN() && !kp[0].isNaN()) {
                val drawX = kp[1] * scale + offsetX
                val drawY = kp[0] * scale + offsetY

                keypointPaint.color = keypointColors[i % keypointColors.size]
                canvas.drawCircle(drawX, drawY, 10f, keypointPaint)

                val label = "[${i}] %.2f".format(kp[2])
                canvas.drawText(label, drawX + 12f, drawY - 12f, textPaint)

                Log.d("OverlayView", "Keypoint [$i] at ($drawX, $drawY), score=${kp[2]}")
            }
        }
    }

    // Update to accept Array<FloatArray>
    fun updateKeypoints(newKeypoints: Array<FloatArray>) {
        keypoints = newKeypoints
        invalidate()
    }
}
