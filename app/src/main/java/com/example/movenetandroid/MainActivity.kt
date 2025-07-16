package com.example.movenetandroid

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.movenetandroid.pose.PoseProcessor
import com.example.movenetandroid.pose.SquatAnalyzer
import com.example.movenetandroid.utils.ModelUtils
import com.example.movenetandroid.utils.VisualizationUtils
import com.example.movenetandroid.feedback.FeedbackUtils
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
import android.graphics.Rect
import android.widget.ImageView
import android.widget.FrameLayout
import android.graphics.Matrix
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.media.MediaMetadataRetriever
import android.widget.ProgressBar
import com.example.movenetandroid.VideoPoseProcessor
import android.app.AlertDialog
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import android.media.MediaCodecInfo
import com.example.movenetandroid.EglHelper

class MainActivity : AppCompatActivity() {
    private lateinit var feedbackTextView: TextView
    private lateinit var interpreter: Interpreter
    private lateinit var poseProcessor: PoseProcessor
    private lateinit var squatAnalyzer: SquatAnalyzer
    private lateinit var previewView: PreviewView
    private lateinit var overlayImageView: ImageView
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var flipCameraButton: Button
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var menuLayout: LinearLayout
    private lateinit var liveCameraButton: Button
    private lateinit var selectVideoButton: Button
    private lateinit var mainLayout: LinearLayout
    private var selectedVideoUri: Uri? = null
    private var processedOverlays: List<Pair<Bitmap, String>>? = null
    private var saveVideoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menu UI
        liveCameraButton = Button(this).apply { text = "Live Camera Feedback" }
        selectVideoButton = Button(this).apply { text = "Select Video" }
        menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(liveCameraButton)
            addView(selectVideoButton)
        }
        setContentView(menuLayout)

        // Main UI (camera/feedback) - initially hidden
        previewView = PreviewView(this)
        overlayImageView = ImageView(this)
        feedbackTextView = TextView(this)
        flipCameraButton = Button(this).apply { text = "Flip Camera" }
        val cameraFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            addView(previewView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(overlayImageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(cameraFrame)
            addView(flipCameraButton)
            addView(feedbackTextView)
        }

        // Live Camera Feedback button action
        liveCameraButton.setOnClickListener {
            // Direct camera setup
            setContentView(mainLayout)
            setupCameraAndFeedback()
        }
        // Select Video button action
        selectVideoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
            selectVideoLauncher.launch(intent)
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

    // Register for activity result to pick video
    private val selectVideoLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val uri = data?.data
            if (uri != null) {
                selectedVideoUri = uri
                // TODO: Process video for pose estimation
                showVideoSelectedPlaceholder()
            }
        }
    }

    private fun showVideoSelectedPlaceholder() {
        // Start processing video for pose estimation
        processSelectedVideo()
    }

    private fun processSelectedVideo() {
        if (selectedVideoUri == null) return
        // UI: show overlay and feedback like live camera
        val videoOverlay = ImageView(this)
        val feedbackText = TextView(this).apply {
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(videoOverlay, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ))
            addView(progressBar)
            addView(feedbackText)
        }
        setContentView(layout)

        val overlaysList = mutableListOf<Pair<Bitmap, String>>()
        val processor = VideoPoseProcessor(
            context = this,
            videoUri = selectedVideoUri!!,
            onFrameProcessed = { overlayBitmap, feedback ->
                runOnUiThread {
                    videoOverlay.setImageBitmap(overlayBitmap)
                    feedbackText.text = feedback
                }
                synchronized(overlaysList) {
                    overlaysList.add(Pair(overlayBitmap, feedback))
                }
            },
            onProgress = { percent ->
                runOnUiThread {
                    progressBar.progress = percent
                }
            },
            onComplete = {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    synchronized(overlaysList) {
                        processedOverlays = overlaysList.toList()
                    }
                    showSaveProcessedVideoDialog()
                }
            }
        )
        processor.processVideo()
    }

    private fun showSaveProcessedVideoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Save Processed Video")
            .setMessage("Do you want to save the processed video to your device?")
            .setPositiveButton("Yes") { _, _ ->
                launchSaveVideoPicker()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun launchSaveVideoPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            putExtra(Intent.EXTRA_TITLE, "processed_video.mp4")
        }
        saveVideoPickerLauncher.launch(intent)
    }

    private fun saveProcessedVideo(overlays: List<Pair<Bitmap, String>>, uri: Uri) {
        // Show a progress dialog
        val progressDialog = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(progressDialog)
        }
        runOnUiThread { setContentView(layout) }

        Thread {
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var pfd: ParcelFileDescriptor? = null
            var eglHelper: EglHelper? = null
            
            try {
                val width = overlays.first().first.width
                val height = overlays.first().first.height
                val frameRate = 30
                val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
                val bitRate = 4000_000
                
                val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                }
                
                codec = MediaCodec.createEncoderByType(mimeType)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = codec.createInputSurface()
                codec.start()

                pfd = contentResolver.openFileDescriptor(uri, "w")
                muxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var trackIndex = -1
                var muxerStarted = false

                eglHelper = EglHelper(width, height, inputSurface)
                val presentationTimeUsPerFrame = 1_000_000L / frameRate
                var presentationTimeUs = 0L

                // Send all frames to encoder
                for ((bitmap, _) in overlays) {
                    eglHelper.drawBitmap(bitmap)
                    eglHelper.setPresentationTime(presentationTimeUs * 1000)
                    eglHelper.swapBuffers()
                    presentationTimeUs += presentationTimeUsPerFrame
                }
                
                // Signal end of input
                codec.signalEndOfInputStream()

                val bufferInfo = MediaCodec.BufferInfo()
                var outputDone = false
                
                while (!outputDone) {
                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    when (outputBufferId) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw RuntimeException("Format changed twice")
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                            // Ignore for API >= 21
                        }
                        else -> {
                            if (outputBufferId >= 0) {
                                val encodedData = codec.getOutputBuffer(outputBufferId)
                                if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                }
                                codec.releaseOutputBuffer(outputBufferId, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                        }
                    }
                }
                
                runOnUiThread {
                    Toast.makeText(this, "Video saved successfully!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save video: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Proper cleanup
                try {
                    eglHelper?.release()
                    codec?.stop()
                    codec?.release()
                    muxer?.stop()
                    muxer?.release()
                    pfd?.close()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }.start()
    }

    private val saveVideoPickerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null && processedOverlays != null) {
                saveVideoUri = uri
                saveProcessedVideo(processedOverlays!!, saveVideoUri!!)
            }
        }
    }

    private fun setupCameraAndFeedback() {
        // Show loading state
        feedbackTextView.text = "Loading model and setting up camera..."
        
        // Load model and processors in background
        Thread {
            try {
                feedbackTextView.post { feedbackTextView.text = "Loading TensorFlow model..." }
                interpreter = ModelUtils.createInterpreter(this, "movenet-lightning.tflite")
                
                feedbackTextView.post { feedbackTextView.text = "Initializing pose processor..." }
                poseProcessor = PoseProcessor(interpreter, 192)
                
                feedbackTextView.post { feedbackTextView.text = "Initializing squat analyzer..." }
                squatAnalyzer = SquatAnalyzer()
                
                runOnUiThread {
                    try {
                        feedbackTextView.text = "Starting camera with pose analysis..."
                        startCameraWithPoseAnalysis()
                        
                        // Flip camera button
                        flipCameraButton.setOnClickListener {
                            try {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                startCameraWithPoseAnalysis()
                            } catch (e: Exception) {
                                feedbackTextView.text = "Error flipping camera: ${e.message}"
                            }
                        }
                        
                    } catch (e: Exception) {
                        feedbackTextView.text = "Error setting up camera: ${e.message}"
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    feedbackTextView.text = "Error loading model: ${e.message}\nPlease restart the app."
                }
            }
        }.start()
    }

    private fun startCameraWithPoseAnalysis() {
        try {
            feedbackTextView.text = "Initializing camera with pose analysis..."
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                    // Set up image analysis for real-time pose estimation
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysis.setAnalyzer(analysisExecutor, { imageProxy ->
                        processImageProxy(imageProxy)
                    })

                    // Unbind any existing use cases first
                    cameraProvider?.unbindAll()
                    
                    // Bind to lifecycle
                    cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                    )
                    feedbackTextView.text = "Camera ready! Start squatting for feedback."
                    
                } catch (exc: Exception) {
                    feedbackTextView.text = "Camera error: ${exc.message}"
                }
            }, ContextCompat.getMainExecutor(this))
            
        } catch (e: Exception) {
            feedbackTextView.text = "Error starting camera: ${e.message}"
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null && ::poseProcessor.isInitialized && ::squatAnalyzer.isInitialized) {
                try {
                    val keypoints = poseProcessor.processFrame(bitmap)
                    // Use SquatAnalyzer to get phase, angle, and feedback
                    val (phase, angle) = squatAnalyzer.detectSquatPhase(keypoints, bitmap.height, bitmap.width)
                    // Get feedback directly from SquatAnalyzer
                    val feedback = squatAnalyzer.getSquatFeedback(phase, (keypoints[11][0] * bitmap.height + keypoints[12][0] * bitmap.height) / 2f, (keypoints[13][0] * bitmap.height + keypoints[14][0] * bitmap.height) / 2f)

                    // Crop or pad bitmap to match previewView aspect ratio
                    val previewAspect = previewView.width.toFloat() / previewView.height
                    val bitmapAspect = bitmap.width.toFloat() / bitmap.height
                    var displayBitmap = bitmap
                    if (Math.abs(previewAspect - bitmapAspect) > 0.01) {
                        // Crop center
                        if (previewAspect > bitmapAspect) {
                            // Crop height
                            val newHeight = (bitmap.width / previewAspect).toInt()
                            val yOffset = (bitmap.height - newHeight) / 2
                            displayBitmap = Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, newHeight)
                        } else {
                            // Crop width
                            val newWidth = (bitmap.height * previewAspect).toInt()
                            val xOffset = (bitmap.width - newWidth) / 2
                            displayBitmap = Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, bitmap.height)
                        }
                    }
                    val overlayBitmap = VisualizationUtils.drawKeypointsOverlay(
                        Bitmap.createScaledBitmap(displayBitmap, previewView.width, previewView.height, false),
                        keypoints
                    )
                    mainHandler.post {
                        try {
                            overlayImageView.setImageBitmap(overlayBitmap)
                            feedbackTextView.text = "Phase: ${phase.name}\nFeedback: $feedback"
                        } catch (e: Exception) {
                            feedbackTextView.text = "UI update error: ${e.message}"
                        }
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
            // Convert YUV_420_888 to RGB Bitmap with correct orientation
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

            // Rotate the bitmap to match the display orientation
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            return bitmap
        } catch (e: Exception) {
            // Return null if image processing fails
            return null
        }
    }

    private fun testBasicCamera() {
        try {
            feedbackTextView.text = "Testing basic camera preview..."
            
            // Simple permission check
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                feedbackTextView.text = "Requesting camera permission..."
                val requestPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        startBasicCameraPreview()
                    } else {
                        feedbackTextView.text = "Camera permission denied. Please grant camera permission in settings."
                    }
                }
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                startBasicCameraPreview()
            }

            // Set up button listeners
            flipCameraButton.setOnClickListener {
                try {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    startBasicCameraPreview()
                } catch (e: Exception) {
                    feedbackTextView.text = "Error flipping camera: ${e.message}"
                }
            }
            
        } catch (e: Exception) {
            feedbackTextView.text = "Error in camera test: ${e.message}"
        }
    }

    private fun startBasicCameraPreview() {
        try {
            feedbackTextView.text = "Initializing camera..."
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview
                    )
                    
                    feedbackTextView.text = "Camera preview working! Click 'Squat Feedback' to load model."
                } catch (exc: Exception) {
                    feedbackTextView.text = "Camera error: ${exc.message}"
                }
            }, ContextCompat.getMainExecutor(this))
            
        } catch (e: Exception) {
            feedbackTextView.text = "Error starting camera: ${e.message}"
        }
    }

    private fun testCameraSetup() {
        try {
            // Test 1: Check camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val requestPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        testCameraProvider()
                    } else {
                        showError("Camera permission denied")
                    }
                }
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                testCameraProvider()
            }
        } catch (e: Exception) {
            showError("Camera test error: ${e.message}")
        }
    }

    private fun testCameraProvider() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    showSuccess("Camera provider working! Setting up full camera...")
                    setupFullCamera()
                } catch (exc: Exception) {
                    showError("Camera provider error: ${exc.message}")
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            showError("Camera provider test error: ${e.message}")
        }
    }

    private fun setupFullCamera() {
        try {
            // Initialize UI components
            previewView = PreviewView(this)
            overlayImageView = ImageView(this)
            feedbackTextView = TextView(this)
            flipCameraButton = Button(this).apply { text = "Flip Camera" }
            
            // Set up the camera frame layout
            val cameraFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1f
                )
                addView(previewView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                addView(overlayImageView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
            
            // Recreate main layout
            mainLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 64, 32, 32)
                addView(cameraFrame)
                addView(flipCameraButton)
                addView(feedbackTextView)
            }
            
            setContentView(mainLayout)
            testBasicCamera()
            
        } catch (e: Exception) {
            showError("Full camera setup error: ${e.message}")
        }
    }

    private fun showError(message: String) {
        val errorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(TextView(this@MainActivity).apply {
                text = "Error: $message"
                textSize = 18f
            })
            addView(Button(this@MainActivity).apply {
                text = "Go Back"
                setOnClickListener {
                    setContentView(menuLayout)
                }
            })
        }
        setContentView(errorLayout)
    }

    private fun showSuccess(message: String) {
        val successLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(TextView(this@MainActivity).apply {
                text = "Success: $message"
                textSize = 18f
            })
            addView(Button(this@MainActivity).apply {
                text = "Continue"
                setOnClickListener {
                    setupFullCamera()
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "Go Back"
                setOnClickListener {
                    setContentView(menuLayout)
                }
            })
        }
        setContentView(successLayout)
    }
} 