package com.example.movenetandroid.pose

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PoseProcessor(
    private val interpreter: Interpreter,
    private val inputSize: Int
) {
    private var prevKeypoints: Array<FloatArray>? = null
    private val confidenceThreshold = 0.15f
    private val smoothingFactors = floatArrayOf(
        0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.65f, 0.65f, 0.6f, 0.6f, 0.55f, 0.55f, 0.65f, 0.65f, 0.6f, 0.6f, 0.55f, 0.55f
    )

    fun processFrame(bitmap: Bitmap): Array<FloatArray> {
        // Dynamically get input tensor info
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape() // e.g., [1, 192, 192, 3]
        val inputDataType = inputTensor.dataType()
        val inputBuffer = preprocessBitmap(bitmap, inputShape, inputDataType)
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1, 17, 3), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(inputBuffer.buffer, outputBuffer.buffer.rewind())
        val keypoints = extractKeypoints(outputBuffer)
        val smoothed = applySmoothing(keypoints, prevKeypoints)
        prevKeypoints = smoothed
        return smoothed
    }

    private fun preprocessBitmap(bitmap: Bitmap, inputShape: IntArray, inputDataType: org.tensorflow.lite.DataType): TensorBuffer {
        val inputSize = inputShape[1]
        val channels = inputShape[3]
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val inputBuffer: TensorBuffer
        if (inputDataType == org.tensorflow.lite.DataType.FLOAT32) {
            val floatValues = FloatArray(inputSize * inputSize * channels)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                floatValues[i * channels] = ((pixel shr 16) and 0xFF).toFloat() // R
                floatValues[i * channels + 1] = ((pixel shr 8) and 0xFF).toFloat() // G
                floatValues[i * channels + 2] = (pixel and 0xFF).toFloat() // B
            }
            inputBuffer = TensorBuffer.createFixedSize(inputShape, org.tensorflow.lite.DataType.FLOAT32)
            inputBuffer.loadArray(floatValues)
        } else if (inputDataType == org.tensorflow.lite.DataType.UINT8) {
            val byteValues = ByteArray(inputSize * inputSize * channels)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                byteValues[i * channels] = ((pixel shr 16) and 0xFF).toByte() // R
                byteValues[i * channels + 1] = ((pixel shr 8) and 0xFF).toByte() // G
                byteValues[i * channels + 2] = (pixel and 0xFF).toByte() // B
            }
            inputBuffer = TensorBuffer.createFixedSize(inputShape, org.tensorflow.lite.DataType.UINT8)
            val byteBuffer = java.nio.ByteBuffer.wrap(byteValues)
            inputBuffer.loadBuffer(byteBuffer)
        } else {
            throw IllegalArgumentException("Unsupported input data type: $inputDataType")
        }
        return inputBuffer
    }

    private fun extractKeypoints(outputBuffer: TensorBuffer): Array<FloatArray> {
        val arr = outputBuffer.floatArray
        val keypoints = Array(17) { FloatArray(3) }
        for (i in 0 until 17) {
            keypoints[i][0] = arr[i * 3]
            keypoints[i][1] = arr[i * 3 + 1]
            keypoints[i][2] = arr[i * 3 + 2]
        }
        return keypoints
    }

    private fun applySmoothing(current: Array<FloatArray>, prev: Array<FloatArray>?): Array<FloatArray> {
        if (prev == null) return current
        val smoothed = Array(17) { FloatArray(3) }
        for (i in 0 until 17) {
            val conf = current[i][2]
            val smoothing = smoothingFactors.getOrElse(i) { 0.6f }
            if (conf < confidenceThreshold) {
                smoothed[i] = current[i]
            } else {
                for (j in 0..2) {
                    smoothed[i][j] = smoothing * prev[i][j] + (1 - smoothing) * current[i][j]
                }
            }
        }
        return smoothed
    }
} 