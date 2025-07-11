package com.example.movenetandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PoseDetectorHelper(context: Context) {

    private var interpreter: Interpreter

    companion object {
        private const val MODEL_NAME = "movenet-lightning.tflite"
        private const val INPUT_WIDTH = 192
        private const val INPUT_HEIGHT = 192
        private const val NUM_KEYPOINTS = 17
        private const val NUM_VALUES_PER_KEYPOINT = 3
    }

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_NAME)
        interpreter = Interpreter(model)
    }

    fun preprocess(bitmap: Bitmap): TensorImage {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val tensorImage = TensorImage.fromBitmap(scaledBitmap)
        return tensorImage
    }

    fun detectPose(bitmap: Bitmap): List<Keypoint> {
        val input = preprocess(bitmap).buffer

        val outputShape = arrayOf(1, 1, NUM_KEYPOINTS, NUM_VALUES_PER_KEYPOINT)
        val output = Array(outputShape[0]) {
            Array(outputShape[1]) {
                Array(outputShape[2]) {
                    FloatArray(outputShape[3])
                }
            }
        }

        interpreter.run(input, output)

        val keypoints = mutableListOf<Keypoint>()
        for (i in 0 until NUM_KEYPOINTS) {
            val y = output[0][0][i][0] * bitmap.height
            val x = output[0][0][i][1] * bitmap.width
            val score = output[0][0][i][2]
            keypoints.add(Keypoint(x, y, score))
        }

        return keypoints
    }

    data class Keypoint(val x: Float, val y: Float, val score: Float)
}
