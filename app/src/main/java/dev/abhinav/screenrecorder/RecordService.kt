package dev.abhinav.screenrecorder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import androidx.core.content.getSystemService
import androidx.window.layout.WindowMetricsCalculator
import dev.abhinav.screenrecorder.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class RecordConfig(
    val resultCode: Int,
    val data: Intent,
): Parcelable

class RecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mediaRecorder by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            MediaRecorder()
        }
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outputFile by lazy {
        File(cacheDir, "screen_record.mp4")
    }
    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }
    private val mediaProjectionCallback = object : MediaProjection.Callback() {

        // Stop the service when the user stops the screen recording
        override fun onStop() {
            super.onStop()
            releaseResources()
            stopService()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_RECORDING -> {
                val notification = NotificationHelper.createNotification(applicationContext)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1, notification)
                }
                _isRunning.value = true
                startRecording(intent)
            }
            STOP_RECORDING -> {
               stopRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent) {
        val config =  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(KEY_RECORDING_CONFIG, RecordConfig::class.java)
        } else {
            intent.getParcelableExtra(KEY_RECORDING_CONFIG)
        }

        if (config == null) {
            return
        }

        mediaProjection = mediaProjectionManager?.getMediaProjection(config.resultCode, config.data)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        initializeRecorder()
        mediaRecorder.start()

        virtualDisplay = createVirtualDisplay()
    }

    private fun getWindowSize(): Pair<Int, Int> {
        val calculator = WindowMetricsCalculator.getOrCreate()
        val metrics = calculator.computeMaximumWindowMetrics(applicationContext)
        return metrics.bounds.width() to metrics.bounds.height()
    }

    private fun getScaledDimensions(maxWith: Int, maxHeight: Int, scaleFactor: Float = 0.8f): Pair<Int, Int> {
        val aspectRatio = maxWith / maxHeight.toFloat()
        var newWidth = (maxWith * scaleFactor).toInt()
        var newHeight = (newWidth / aspectRatio).toInt()

        if (newHeight > (maxHeight * scaleFactor)) {
            newHeight = (maxHeight * scaleFactor).toInt()
            newWidth = (newHeight * aspectRatio).toInt()
        }
        return newWidth to newHeight
    }

    private fun initializeRecorder() {
        val (width, height) = getWindowSize()
        val (scaledWidth, scaledHeight) = getScaledDimensions(width, height)
        with(mediaRecorder) {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(outputFile)
            setVideoSize(scaledWidth, scaledHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(BIT_RATE * 1000)
            setVideoFrameRate(FRAME_RATE)
            prepare()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        val (width, height) = getWindowSize()
        return mediaProjection?.createVirtualDisplay(
            "ScreenRecord",
            width,
            height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null
        )
    }

    private fun stopRecording() {
        mediaRecorder.stop()
        mediaRecorder.reset()
    }

    private fun stopService() {
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        serviceScope.coroutineContext.cancelChildren() //don't cancel scope itself, only cancel all coroutines launched in the service scope
    }

    private fun releaseResources() {
        mediaRecorder.release()
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private const val FRAME_RATE = 30
        private const val BIT_RATE = 512 //kilobits

        const val START_RECORDING = "start_recording"
        const val STOP_RECORDING = "stop_recording"
        const val KEY_RECORDING_CONFIG = "key_recording_config"

    }
}