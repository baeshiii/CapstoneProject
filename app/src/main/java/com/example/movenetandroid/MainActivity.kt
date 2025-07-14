package com.example.movenetandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.movenetandroid.databinding.ActivityMainBinding
import java.util.concurrent.Executors

fun Bitmap.flipHorizontally(): Bitmap {
    val matrix = Matrix().apply {
        preScale(-1f, 1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var poseDetectorHelper: PoseDetectorHelper
    private lateinit var squatAnalyzer: SquatAnalyzer
    private val executor = Executors.newSingleThreadExecutor()

    private var previousHipY: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        poseDetectorHelper = PoseDetectorHelper(this)
        squatAnalyzer = SquatAnalyzer()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                //.setTargetResolution(Size(720, 1280))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor, ImageAnalysis.Analyzer { imageProxy ->
                processImageProxy(imageProxy)
            })

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() // ✅ This is always non-null

        // Rotate
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap

        // Flip for front camera
        val processedBitmap = rotatedBitmap.flipHorizontally()

        val keypoints = poseDetectorHelper.detectPose(processedBitmap)

        // Squat phase detection
        val leftHip = keypoints.getOrNull(11)
        val rightHip = keypoints.getOrNull(12)

        if (leftHip != null && rightHip != null && leftHip.score > 0.5f && rightHip.score > 0.5f) {
            val currentHipY = (leftHip.y + rightHip.y) / 2f

            previousHipY?.let { prevY ->
                val dy = currentHipY - prevY
                val threshold = 2f

                val phase = when {
                    dy > threshold -> SquatAnalyzer.Phase.DESCENDING
                    dy < -threshold -> SquatAnalyzer.Phase.ASCENDING
                    else -> SquatAnalyzer.Phase.BOTTOM
                }

                squatAnalyzer.setSquatPhase(phase)
            }

            previousHipY = currentHipY
        }

        val squatFeedback = squatAnalyzer.analyzeDepth(
            keypoints,
            binding.previewView.height,
            binding.previewView.width
        )

        runOnUiThread {
            binding.overlayView.updateKeypoints(keypoints)

            val hipsDetected = (leftHip?.score ?: 0f) > 0.5f
            val kneesDetected = (keypoints.getOrNull(13)?.score ?: 0f) > 0.5f &&
                    (keypoints.getOrNull(14)?.score ?: 0f) > 0.5f

            binding.feedbackTextView.text = when {
                squatFeedback.isNotEmpty() -> squatFeedback.joinToString("\n")
                hipsDetected && kneesDetected -> "✅ Good squat depth!"
                else -> "👀 Waiting for user..."
            }
        }

        imageProxy.close()
    }
}
