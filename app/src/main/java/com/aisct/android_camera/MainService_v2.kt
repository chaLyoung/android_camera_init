package com.aisct.android_camera

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.lifecycle.LifecycleOwner
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.provider.TedPermissionProvider.context


class MainService_v2 : Service() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private val handler = Handler(Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        startForegroundService()
        startCamera()
        Log.d("tag_lc", "스따뜌ㅜnnnnnnnnn")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            Log.d("tag_lc", "cameraProviderFuture.addListener::::::::::::::::")
            val imageAnalysis = ImageAnalysis.Builder()
//                .setTargetResolution(Size(1280, 720)) // Set your desired resolution
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                        // 각 프레임에 대한 분석을 여기서 수행합니다.
                        // image에서 비트맵이나 이미지 데이터를 가져와 처리할 수 있습니다.
                        // image.close()를 호출하여 이미지를 해제해야 합니다.
                        Log.d("tag_lc", "startCamera::::::::::::::::::::::::::::::")
                        image.close()
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis)

            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service")
            .setContentText("Capturing camera frames in the background")
            .setSmallIcon(R.drawable.ic_photo)
            .setChannelId(CHANNEL_ID)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ForegroundVideoCapture"
        private const val CHANNEL_NAME = "Video Capture"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }

}