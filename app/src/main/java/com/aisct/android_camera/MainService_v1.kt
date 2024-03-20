package com.aisct.android_camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.app.NotificationCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainService_v1 : Service() {
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var cameraExecutor: ExecutorService
    private val CHANNEL_ID = "CameraServiceChannel"
    private val NOTIFICATION_ID = 12345678

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        isRunning = true
        createNotificationChannel()
    }

    private fun createNotification(): Notification {
        Log.d("tag_lc", "------- createNotification")
        val intent = Intent(this, MainActivity_old::class.java)
//            Intent(this, MainActivity::class.java).apply {
//            // 기존의 탑 액티비티를 재사용하고, 새 인스턴트를 onNewIntent()에 전달
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Service")
            .setContentText("Running camera service in foreground")
            .setSmallIcon(R.drawable.ic_photo)
            .setContentIntent(pendingIntent) // PendingIntent 설정
            .setAutoCancel(true) // 사용자가 탭하면 자동으로 알림을 삭제
            .build()
    }

    private fun createNotificationChannel() {
        Log.d("tag_lc", "------- createNotificationChannel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Camera Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun setupImageAnalysis() {
        Log.d("tag_lc", "------- setupImageAnalysis ------------")
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val analyzer = imageAnalyzer()
        imageAnalysis?.let {
            it.setAnalyzer(cameraExecutor, analyzer)
        }
//        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)
    }
//    private fun setupImageAnalysis() {
//        Log.d("tag_lc", "------- setupImageAnalysis ------------")
//        imageAnalysis = ImageAnalysis.Builder()
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .build()
//
//        val analyzer = imageAnalyzer()
//        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)
//        startAnalysis()
//    }
//    private fun startAnalysis() {
//        imageAnalysis?.let {
//            it.setAnalyzer(cameraExecutor, imageAnalyzer())
//        }
//    }

    inner class imageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            Log.d("tag_lc", "------- image ------------")
            // Implement your image analysis logic here
            // 여기서 프레임을 분석합니다.
            // 예를 들어, 이미지에서 객체를 감지하거나, 바코드를 스캔할 수 있습니다.
            image.close()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("tag_lc", "------- onStartCommand ------------")
        startForeground(NOTIFICATION_ID, createNotification())
        setupImageAnalysis()
        return START_STICKY
    }

    companion object {
        var isRunning = false
        const val ACTION_SHOW_NOTIFICATION = "com.aisct.android_camera.action.SHOW_NOTIFICATION"
    }
}