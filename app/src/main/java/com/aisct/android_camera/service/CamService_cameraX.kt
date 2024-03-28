//package com.aisct.android_camera.service
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.graphics.Color
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import android.view.View
//import android.view.WindowManager
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.ImageProxy
//import androidx.core.app.NotificationCompat
//import com.aisct.android_camera.MainActivity
//import com.aisct.android_camera.R
//import com.aisct.android_camera.module.CameraDeviceManager
//import kotlinx.coroutines.GlobalScope
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//
//
//class CamService_cameraX : Service() {
//    private var wm: WindowManager? = null
//    private var rootView: View? = null
//    private var imageAnalysis: ImageAnalysis? = null
//
//    override fun onBind(p0: Intent?): IBinder? {
//        Log.d("tag_lc", "CamService - override fun onBind")
//        return null
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        when(intent?.action) {
//            ACTION_START -> start()
//            ACTION_STOP -> {
//                CameraDeviceManager.stopImageAnalysis()
//                stopForeground(true)
//                stopSelf()
//            }
//        }
//
//        return super.onStartCommand(intent, flags, startId)
//    }
//
//    override fun onCreate() {
//        super.onCreate()
////        val inflater = LayoutInflater.from(this)
////        mainBinding = ActivityMainBinding.inflate(inflater, null, false)
//        startForeground()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        CameraDeviceManager.stopCamera()
//        if (rootView != null)
//            wm?.removeView(rootView)
//
//        sendBroadcast(Intent(ACTION_START))
//    }
//
//    val job: Job = GlobalScope.launch {
//        // 무한 루프 예제
//        while(isActive) { // isActive는 코루틴의 활성 상태를 확인
//            Log.d("tag_lc", "CamService - coroutine Event Running.....")
//            delay(2000) // 1초 대기
//        }
//    }
//
//    private fun start() {
////        sendBroadcast(Intent(ACTION_STOPPED))
////        Log.d("tag_lc", "CamService - private fun start()")
////        imageAnalysis = CameraDeviceManager.getAnalysisUseCase { imageProxy ->
////            processImage(imageProxy)
////            imageProxy.close()
////        }.also { analysis ->
////            Log.d("tag_lc", "processImage Image captured: analysis")
////            // ImageAnalysis를 lifecycle에 bind하기 위해선 MainActivity 또는 다른 LifecycleOwner 필요
////            // 이 예제에서는 별도의 lifecycle handling이 없으므로, CameraX가 이를 직접 관리하도록 합니다.
////        }
//        CameraDeviceManager.setupImageAnalysis(applicationContext, ::processImage)
//    }
//
//    private fun processImage(imageProxy: ImageProxy) {
//        Log.d("tag_lc", "processImage Image captured: ${imageProxy.width} x ${imageProxy.height}")
//    }
//
//
//    private fun startForeground() {
//        Log.d("tag_lc", "CamService - private fun startForeground()")
//        val pendingIntent: PendingIntent =
//            Intent(this, MainActivity::class.java).let { notificationIntent ->
//                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))
//            }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE)
//            channel.lightColor = Color.BLUE
//            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
//            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            nm.createNotificationChannel(channel)
//        }
//
//        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(getText(R.string.app_name))
//            .setContentText(getText(R.string.app_name))
//            .setSmallIcon(R.drawable.ic_photo)
//            .setContentIntent(pendingIntent)
//            .setTicker(getText(R.string.app_name))
//            .setContentIntent(pendingIntent) // PendingIntent 설정
//            .setAutoCancel(true) // 사용자가 탭하면 자동으로 알림을 삭제
//            .build()
//
//        startForeground(ONGOING_NOTIFICATION_ID, notification)
//    }
//
//    companion object {
//        val ACTION_START = "eu.sisik.backgroundcam.action.START"
//        val ACTION_STOP = "eu.sisik.backgroundcam.action.STOP"
//        val ACTION_STOPPED = "eu.sisik.backgroundcam.action.STOPPED"
//        val ONGOING_NOTIFICATION_ID = 6660
//        val CHANNEL_ID = "cam_service_channel_id"
//        val CHANNEL_NAME = "cam_service_channel_name"
//    }
//}