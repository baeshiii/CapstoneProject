package com.example.movenetandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var keypoints: List<PoseDetectorHelper.Keypoint> = emptyList()

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (kp in keypoints) {
            if (kp.score > 0.4f) {
                canvas.drawCircle(kp.x, kp.y, 10f, paint)
            }
        }
    }

    fun updateKeypoints(newKeypoints: List<PoseDetectorHelper.Keypoint>) {
        keypoints = newKeypoints
        invalidate()
    }
}
