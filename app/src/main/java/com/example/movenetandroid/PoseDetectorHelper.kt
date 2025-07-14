package com.example.movenetandroid

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage

class PoseDetectorHelper(context: Context) {

    private var interpreter: Interpreter

    companion object {
        private const val MODEL_NAME = "movenet-lightning.tflite"
        private const val INPUT_WIDTH = 192
        private const val INPUT_HEIGHT = 192
        private const val NUM_KEYPOINTS = 17
        private const val NUM_VALUES_PER_KEYPOINT = 3
        private const val CONFIDENCE_THRESHOLD = 0.15f
    }

    // Smoothing factors for each keypoint (based on your Python configuration)
    private val keypointSmoothingFactors = floatArrayOf(
        0.7f, 0.7f, 0.7f, 0.7f, 0.7f,
        0.65f, 0.65f, 0.6f, 0.6f, 0.55f,
        0.55f, 0.65f, 0.65f, 0.6f, 0.6f,
        0.55f, 0.55f
    )

    // Store previous frame keypoints for smoothing
    private var previousKeypoints: List<Keypoint>? = null

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_NAME)
        interpreter = Interpreter(model)
    }

    fun detectPose(bitmap: Bitmap): List<Keypoint> {
        // Resize image to model input (192x192) and prepare input tensor
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val input = TensorImage.fromBitmap(scaledBitmap).buffer

        // Allocate output buffer, shape: [1, 1, 17, 3]
        val output = Array(1) {
            Array(1) {
                Array(NUM_KEYPOINTS) {
                    FloatArray(NUM_VALUES_PER_KEYPOINT)
                }
            }
        }

        // Run inference
        interpreter.run(input, output)

        // Obtain raw keypoints; note: model outputs are normalized relative to INPUT_WIDTH/HEIGHT
        val currentKeypoints = mutableListOf<Keypoint>()
        for (i in 0 until NUM_KEYPOINTS) {
            val y = output[0][0][i][0] * INPUT_HEIGHT
            val x = output[0][0][i][1] * INPUT_WIDTH
            val score = output[0][0][i][2]
            Log.d("Keypoint", "Raw keypoint [$i]: x=$x, y=$y, score=$score")
            currentKeypoints.add(Keypoint(x, y, score))
        }

        // Apply smoothing if previous keypoints exist
        val smoothedKeypoints = if (previousKeypoints != null) {
            val newKeypoints = mutableListOf<Keypoint>()
            for (i in 0 until NUM_KEYPOINTS) {
                val current = currentKeypoints[i]
                val previous = previousKeypoints!![i]
                // Only smooth if the current confidence is high enough, otherwise keep previous
                if (current.score < CONFIDENCE_THRESHOLD) {
                    newKeypoints.add(previous)
                } else {
                    val factor = keypointSmoothingFactors[i]
                    val smoothedX = factor * previous.x + (1 - factor) * current.x
                    val smoothedY = factor * previous.y + (1 - factor) * current.y
                    newKeypoints.add(Keypoint(smoothedX, smoothedY, current.score))
                }
            }
            newKeypoints
        } else {
            currentKeypoints
        }

        // Update previous keypoints for next frame
        previousKeypoints = smoothedKeypoints
        return smoothedKeypoints
    }

    data class Keypoint(val x: Float, val y: Float, val score: Float)
}