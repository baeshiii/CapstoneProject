package com.example.movenetandroid.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object VisualizationUtils {
    fun drawKeypointsOverlay(
        bitmap: Bitmap,
        keypoints: Array<FloatArray>,
        threshold: Float = 0.15f
    ): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            color = Color.MAGENTA
            style = Paint.Style.FILL
            strokeWidth = 8f
        }
        for (i in keypoints.indices) {
            val kp = keypoints[i]
            if (kp[2] > threshold) {
                val x = kp[1] * bitmap.width
                val y = kp[0] * bitmap.height
                canvas.drawCircle(x, y, 10f, paint)
            }
        }
        // Draw skeleton lines with different colors for each body part
        val skeleton = arrayOf(
            intArrayOf(0, 1), intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(2, 4), // Head to shoulders
            intArrayOf(0, 5), intArrayOf(0, 6), intArrayOf(5, 7), intArrayOf(7, 9), // Torso and left arm
            intArrayOf(6, 8), intArrayOf(8, 10), // Right arm
            intArrayOf(5, 6), intArrayOf(5, 11), intArrayOf(6, 12), // Torso to hips
            intArrayOf(11, 12), intArrayOf(11, 13), intArrayOf(13, 15), // Left leg
            intArrayOf(12, 14), intArrayOf(14, 16) // Right leg
        )
        // Define colors for each part
        val partColors = listOf(
            Color.YELLOW, // Head/shoulders
            Color.YELLOW,
            Color.YELLOW,
            Color.YELLOW,
            Color.BLUE, // Left arm
            Color.RED, // Right arm
            Color.BLUE,
            Color.BLUE,
            Color.RED,
            Color.RED,
            Color.GREEN, // Torso
            Color.GREEN,
            Color.GREEN,
            Color.MAGENTA, // Left leg
            Color.MAGENTA,
            Color.MAGENTA,
            Color.CYAN, // Right leg
            Color.CYAN
        )
        for ((i, pair) in skeleton.withIndex()) {
            val kp1 = keypoints[pair[0]]
            val kp2 = keypoints[pair[1]]
            if (kp1[2] > threshold && kp2[2] > threshold) {
                val x1 = kp1[1] * bitmap.width
                val y1 = kp1[0] * bitmap.height
                val x2 = kp2[1] * bitmap.width
                val y2 = kp2[0] * bitmap.height
                val linePaint = Paint().apply {
                    color = partColors.getOrElse(i) { Color.GREEN }
                    style = Paint.Style.STROKE
                    strokeWidth = if (i in 10..12) 8f else 4f // Thicker for torso
                }
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }
        return output
    }
} 