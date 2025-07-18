package com.example.movenetandroid

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.movenetandroid.pose.PoseProcessor
import com.example.movenetandroid.pose.SquatDepthAnalyzer
import com.example.movenetandroid.utils.ModelUtils
import com.example.movenetandroid.utils.VisualizationUtils
import com.example.movenetandroid.feedback.FeedbackUtils
import org.tensorflow.lite.Interpreter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class VideoPoseProcessor(
    private val context: Context,
    private val videoUri: Uri,
    private val onFrameProcessed: (Bitmap, String) -> Unit,
    private val onComplete: () -> Unit,
    private val onProgress: (Int) -> Unit
) {
    private lateinit var interpreter: Interpreter
    private lateinit var poseProcessor: PoseProcessor
    private lateinit var squatAnalyzer: SquatDepthAnalyzer
    private val executor = Executors.newFixedThreadPool(4) // Parallel processing
    private val processedFrames = AtomicInteger(0)

    fun processVideo() {
        Thread {
            try {
                interpreter = ModelUtils.createInterpreter(context, "movenet-lightning.tflite")
                poseProcessor = PoseProcessor(interpreter, 192)
                squatAnalyzer = SquatDepthAnalyzer()

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                
                // Optimize frame processing
                val frameIntervalMs = 50L // Process every 50ms instead of 33ms (20fps instead of 30fps)
                val totalFrames = (durationMs / frameIntervalMs).toInt().coerceAtLeast(1)
                var timeMs = 0L
                var frameCount = 0
                
                // Process frames progressively
                while (timeMs < durationMs) {
                    val frameBitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frameBitmap != null) {
                        val finalTimeMs = timeMs
                        val finalFrameCount = frameCount
                        
                        // Process frame in parallel
                        executor.submit {
                            try {
                                val keypoints = poseProcessor.processFrame(frameBitmap)
                                val (phase, angle) = squatAnalyzer.detectSquatPhase(keypoints, frameBitmap.height, frameBitmap.width)
                                val feedback = FeedbackUtils.getSquatFeedback(phase.name, angle.toDouble())
                                val overlayBitmap = VisualizationUtils.drawKeypointsOverlay(frameBitmap, keypoints)
                                
                                // Update UI immediately as frames are processed
                                onFrameProcessed(overlayBitmap, "Phase: ${phase.name}\nFeedback: $feedback")
                                
                                // Update progress
                                val processed = processedFrames.incrementAndGet()
                                val percent = (processed * 100 / totalFrames).coerceIn(0, 100)
                                onProgress(percent)
                                
                            } catch (e: Exception) {
                                // Handle processing errors gracefully
                                onFrameProcessed(frameBitmap, "Error processing frame: ${e.message}")
                            }
                        }
                    }
                    frameCount++
                    timeMs += frameIntervalMs
                }
                
                retriever.release()
                
                // Wait for all processing to complete
                executor.shutdown()
                while (!executor.isTerminated()) {
                    Thread.sleep(100)
                }
                
                onComplete()
                
            } catch (e: Exception) {
                onFrameProcessed(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), "Error: ${e.message}")
                onComplete()
            }
        }.start()
    }
} 