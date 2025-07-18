package com.example.movenetandroid

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.movenetandroid.pose.PoseProcessor
import com.example.movenetandroid.pose.SquatDepthAnalyzer
import com.example.movenetandroid.pose.RepetitionCounter
import com.example.movenetandroid.utils.ModelUtils
import org.tensorflow.lite.Interpreter
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.example.movenetandroid.pose.SpineAnalyzer

class MainActivity : AppCompatActivity() {
    private lateinit var feedbackTextView: TextView
    private lateinit var repetitionCounterTextView: TextView
    private lateinit var resetCounterButton: Button
    private lateinit var interpreter: Interpreter
    private lateinit var poseProcessor: PoseProcessor
    private lateinit var squatAnalyzer: SquatDepthAnalyzer
    private lateinit var repetitionCounter: RepetitionCounter
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var flipCameraButton: Button
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        feedbackTextView = findViewById(R.id.feedbackTextView)
        repetitionCounterTextView = findViewById(R.id.repetitionCounterTextView)
        resetCounterButton = findViewById(R.id.resetCounterButton)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        flipCameraButton = findViewById(R.id.flipCameraButton)

        // Initialize repetition counter
        repetitionCounter = RepetitionCounter()
        repetitionCounter.setOnRepetitionCountChangedListener { count ->
            mainHandler.post {
                repetitionCounterTextView.text = "Reps: $count"
            }
        }

        // Set up reset button
        resetCounterButton.setOnClickListener {
            repetitionCounter.resetCount()
        }

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            setupCameraAndFeedback()
        }

        flipCameraButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) 
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            setupCameraAndFeedback()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            setupCameraAndFeedback()
        } else {
            feedbackTextView.text = "Camera permission required"
        }
    }

    private fun setupCameraAndFeedback() {
        feedbackTextView.text = "Loading model and setting up camera..."
        
        Thread {
            try {
                feedbackTextView.post { feedbackTextView.text = "Loading TensorFlow model..." }
                interpreter = ModelUtils.createInterpreter(this, "movenet-lightning.tflite")
                
                feedbackTextView.post { feedbackTextView.text = "Initializing pose processor..." }
                poseProcessor = PoseProcessor(interpreter, 192)
                
                feedbackTextView.post { feedbackTextView.text = "Initializing squat analyzer..." }
                squatAnalyzer = SquatDepthAnalyzer()
                
                feedbackTextView.post { feedbackTextView.text = "Setting up camera..." }
                setupCamera()
                
            } catch (e: Exception) {
                feedbackTextView.post { feedbackTextView.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                // Set mirroring for overlay
                overlayView.isMirrored = (lensFacing == CameraSelector.LENS_FACING_FRONT)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                
                mainHandler.post { feedbackTextView.text = "Camera ready! Start squatting for feedback." }
                
            } catch (exc: Exception) {
                mainHandler.post { feedbackTextView.text = "Camera error: ${exc.message}" }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null && ::poseProcessor.isInitialized && ::squatAnalyzer.isInitialized) {
                try {
                    val keypoints = poseProcessor.processFrame(bitmap)
                    val (phase, angle) = squatAnalyzer.detectSquatPhase(keypoints, bitmap.height, bitmap.width)
                    
                    val feedback = squatAnalyzer.getComprehensiveFeedback(
                        phase, 
                        (keypoints[11][0] * bitmap.height + keypoints[12][0] * bitmap.height) / 2f, 
                        (keypoints[13][0] * bitmap.height + keypoints[14][0] * bitmap.height) / 2f,
                        keypoints,
                        bitmap.height,
                        bitmap.width
                    )
                    
                    // Update repetition counter with both phase and feedback
                    repetitionCounter.updatePhase(phase, feedback)
                    
                    overlayView.updateKeypoints(keypoints)
                    
                    mainHandler.post {
                        feedbackTextView.text = feedback
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        feedbackTextView.text = "Processing error: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                feedbackTextView.text = "Camera error: ${e.message}"
            }
        } finally {
            try {
                imageProxy.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                imageProxy.width, imageProxy.height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
            val yuvBytes = out.toByteArray()
            var bitmap = BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)

            if (bitmap == null) {
                return null
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            if (::interpreter.isInitialized) {
                interpreter.close()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}