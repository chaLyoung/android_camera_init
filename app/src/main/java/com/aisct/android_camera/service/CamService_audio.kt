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
//import android.graphics.SurfaceTexture
//import android.hardware.camera2.CameraAccessException
//import android.hardware.camera2.CameraCaptureSession
//import android.hardware.camera2.CameraCharacteristics
//import android.hardware.camera2.CameraDevice
//import android.hardware.camera2.CameraManager
//import android.hardware.camera2.CaptureRequest
//import android.hardware.camera2.CaptureResult
//import android.hardware.camera2.TotalCaptureResult
//import android.media.ImageReader
//import android.media.MediaCodec
//import android.media.MediaCodecInfo
//import android.media.MediaFormat
//import android.view.OrientationEventListener
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import android.util.Size
//import android.view.LayoutInflater
//import android.view.Surface
//import android.view.View
//import android.view.WindowManager
//import androidx.core.app.NotificationCompat
//import com.aisct.android_camera.MainActivity_aud
//import com.aisct.android_camera.R
//import com.aisct.android_camera.databinding.ActivityMainBinding
//import com.aisct.android_camera.network.WebSocketManager
//import okio.ByteString
//import java.io.IOException
//import kotlin.math.absoluteValue
//
//class CamService_audio : Service() {
//    private var wm: WindowManager? = null
//    private var rootView: View? = null
//    private lateinit var mainBinding: ActivityMainBinding
//    private var cameraManager: CameraManager? = null
//    private var previewSize: Size? = null
//    private var cameraDevice: CameraDevice? = null
//    private var captureSession: CameraCaptureSession? = null
//
//    private var imageReader: ImageReader? = null
//
//    private lateinit var surface: Surface
//    private lateinit var mediaCodec: MediaCodec
//    private lateinit var sps: ByteArray
//    private lateinit var pps: ByteArray
//    private var codecConfigured = false
//    private var orientationListener: OrientationEventListener? = null
//    private var lastKnownDeviceOrientation = 0
//    private var shouldShowPreview = true
//    private lateinit var webSocketManager: WebSocketManager
////    private lateinit var mediaMuxer: MediaMuxer
//
//
//    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
//        override fun onCaptureProgressed(
//            session: CameraCaptureSession,
//            request: CaptureRequest,
//            partialResult: CaptureResult
//        ) {
//            sendVideoData()
//        }
//        override fun onCaptureCompleted(
//            session: CameraCaptureSession,
//            request: CaptureRequest,
//            result: TotalCaptureResult
//        ) {}
//    }
//
//    private fun initWebSocket() {
//    }
//
//    private fun initMediaCodec() {
//        try {
//            Log.d("tag_lc", "CamService - private fun initMediaCodec()")
//            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, IMAGE_WIDTH, IMAGE_HEIGHT)
//            val mediaFormat = MediaFormat.createVideoFormat(IMAGE_MIME_TYPE, IMAGE_WIDTH, IMAGE_HEIGHT)
//            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
//            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
//            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
//
//            mediaCodec = MediaCodec.createEncoderByType(IMAGE_MIME_TYPE)
//            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//            surface = mediaCodec.createInputSurface()
//            mediaCodec.start()
//            createCaptureSession()
//            extractCodecConfigData()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun extractCodecConfigData() {
//        val bufferInfo = MediaCodec.BufferInfo()
//        val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
//        if (outputBufferIndex >= 0) {
//            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
//                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
//                val configData = ByteArray(bufferInfo.size)
//                outputBuffer?.get(configData)
//                parseSPSandPPS(configData)
//                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
//                codecConfigured = true
//            }
//        }
//    }
//
//    private fun parseSPSandPPS(data: ByteArray) {
//        // SPS와 PPS를 파싱하여 저장
//        // 이 예제에서는 단순화를 위해 전체 데이터를 저장합니다.
//        sps = data.sliceArray(0 until data.size / 2) // 임의의 인덱스, 실제 구현 필요
//        pps = data.sliceArray(data.size / 2 until data.size)
//    }
//
//    private fun createCaptureSession() {
//        Log.d("tag_lc", "CamService - private fun createCaptureSession()")
//        try {
//            val targetSurfaces = listOf(surface)
//            Log.d("tag_lc", "listOf(surface) : " + targetSurfaces.toString())
//            cameraDevice?.createCaptureSession(targetSurfaces, object : CameraCaptureSession.StateCallback() {
//                override fun onConfigured(session: CameraCaptureSession) {
//                    captureSession = session
//                    val initialRotation = (cameraManager?.getCameraCharacteristics(cameraDevice!!.id)
//                        ?.get(CameraCharacteristics.SENSOR_ORIENTATION)!! + lastKnownDeviceOrientation + 270) % 360
//                    Log.d("tag_lc", "initialRotation : " + initialRotation.toString())
//                    val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
//                        addTarget(surface)
//                        set(CaptureRequest.JPEG_ORIENTATION, initialRotation)
//                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
//                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
//                    }
//                    Log.d("tag_lc", "CamService - CameraCaptureSession.StateCallback()")
//                    session.setRepeatingRequest(captureRequest.build(), captureCallback, null)
//                }
//
//                override fun onConfigureFailed(session: CameraCaptureSession) {
//                    Log.d("tag_lc", session.toString())
//                }
//            }, null)
//        } catch (e: CameraAccessException) {
//            Log.d("tag_lc", e.toString())
//        }
//    }
//
//    private fun sendVideoData() {
//        val bufferInfo = MediaCodec.BufferInfo()
//        if(webSocketManager.isConnected() && IS_START_SEND) {
//            val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
//            if (outputBufferIndex >= 0) {
//                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
//                val frameData = ByteArray(bufferInfo.size)
//                outputBuffer?.get(frameData)
//
//                // 버퍼에서 키 프레임 플래그를 확인합니다.
//                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
//                    // 키 프레임인 경우, SPS와 PPS 데이터를 함께 전송합니다.
//                    val dataWithHeaders = ByteArray(sps.size + pps.size + frameData.size)
//                    System.arraycopy(sps, 0, dataWithHeaders, 0, sps.size)
//                    System.arraycopy(pps, 0, dataWithHeaders, sps.size, pps.size)
//                    System.arraycopy(frameData, 0, dataWithHeaders, sps.size + pps.size, frameData.size)
//
//                    webSocketManager.sendMessage(ByteString.of(*dataWithHeaders))
//                } else {
//                    // 키 프레임이 아닌 경우, 프레임 데이터만 전송합니다.
//                    webSocketManager.sendMessage(ByteString.of(*frameData))
//                }
//                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
//            }
//        } else {
//        }
//    }
//
//    private fun initCamera(width: Int, height: Int) {
//        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        var camId: String? = null
//        for (id in cameraManager!!.cameraIdList) {
//            val characteristics = cameraManager!!.getCameraCharacteristics(id)
//            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
//            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
//                camId = id
//                break
//            }
//        }
//        previewSize = chooseSupportedSize(camId!!, width, height)
//        try {
//            cameraManager!!.openCamera(camId, stateCallback, null)
//        } catch (e:SecurityException) {
//            Log.d("tag_lc", "SecurityException" + e.toString())
//        }
//    }
//
//    private val stateCallback = object : CameraDevice.StateCallback() {
//        override fun onOpened(currentCameraDevice: CameraDevice) {
//            cameraDevice = currentCameraDevice
//            initMediaCodec()
//        }
//        override fun onDisconnected(currentCameraDevice: CameraDevice) {
//            currentCameraDevice.close()
//            cameraDevice = null
//        }
//        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
//            currentCameraDevice.close()
//            cameraDevice = null
//        }
//    }
//
//    private fun chooseSupportedSize(camId: String, textureViewWidth: Int, textureViewHeight: Int): Size {
//        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        // Get all supported sizes for TextureView
//        val characteristics = manager.getCameraCharacteristics(camId)
//        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//        val supportedSizes = map!!.getOutputSizes(SurfaceTexture::class.java)
//        // We want to find something near the size of our TextureView
//        val texViewArea = textureViewWidth * textureViewHeight
//        val texViewAspect = textureViewWidth.toFloat()/textureViewHeight.toFloat()
//        val nearestToFurthestSz = supportedSizes.sortedWith(compareBy(
//            // First find something with similar aspect
//            {
//                val aspect = if (it.width < it.height) it.width.toFloat() / it.height.toFloat()
//                else it.height.toFloat()/it.width.toFloat()
//                (aspect - texViewAspect).absoluteValue
//            },
//            // Also try to get similar resolution
//            {
//                (texViewArea - it.width * it.height).absoluteValue
//            }
//        ))
//        Log.d("tag_lc", "CamService - " + nearestToFurthestSz.toString())
////        if (nearestToFurthestSz.isNotEmpty())
////            return nearestToFurthestSz[0]
//        return Size(1920, 1080)
//    }
//
//    override fun onBind(p0: Intent?): IBinder? {
//        Log.d("tag_lc", "CamService - override fun onBind")
//        return null
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        Log.d("tag_lc", "CamService - override fun onStartCommand : " + intent.toString())
//        when(intent?.action) {
//            ACTION_START -> start()
//            ACTION_STOP -> {
//                IS_START_SEND = false
//            }
//            SEND_START -> {
//                IS_START_SEND = true
//                sendBroadcast(Intent(SEND_START))
//            }
//            ACTION_CANCEL -> {
//                stopSelf()
//            }
//        }
//        return super.onStartCommand(intent, flags, startId)
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        val inflater = LayoutInflater.from(this)
//        mainBinding = ActivityMainBinding.inflate(inflater, null, false)
//        startForeground()
//    }
//
//    private fun start() {
//        shouldShowPreview = false
//        webSocketManager = WebSocketManager(SERVER_URL)
//        initCamera(1920, 1080)
////        setupOrientationListener()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        try {
//            IS_START_SEND = false
//            mediaCodec.stop()
//            mediaCodec.release()
//            stopCamera()
//            orientationListener?.disable()
//        } catch (e: Exception) {
//            Log.e("tag_lc", "CamService - Error on onDestroy", e)
//        }
//        if (rootView != null)
//            wm?.removeView(rootView)
//        sendBroadcast(Intent(ACTION_STOPPED))
//    }
//
//    private fun stopCamera() {
//        Log.d("tag_lc", "CamService - private fun stopCamera()")
//        try {
//            captureSession?.close()
//            captureSession = null
//
//            cameraDevice?.close()
//            cameraDevice = null
//
//            imageReader?.close()
//            imageReader = null
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun startForeground() {
//        Log.d("tag_lc", "CamService - private fun startForeground()")
//        val pendingIntent: PendingIntent =
//            Intent(this, MainActivity_aud::class.java).let { notificationIntent ->
//                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))
//            }
//
//        val stopSelfIntent = Intent(this, CamService_audio::class.java).apply {
//            action = ACTION_CANCEL
//        }
//        val stopSelfPendingIntent = PendingIntent.getService(this, 0, stopSelfIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))
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
//            .addAction(R.drawable.ic_cancel, "Stop", stopSelfPendingIntent) // '종료' 액션 추가
//            .setTicker(getText(R.string.app_name))
//            .setContentIntent(pendingIntent) // PendingIntent 설정
//            .setAutoCancel(true) // 사용자가 탭하면 자동으로 알림을 삭제
//            .build()
//
//        startForeground(ONGOING_NOTIFICATION_ID, notification)
//    }
//    companion object {
//        val TAG = "CamService"
//        val ACTION_START = "ACTION_START"
//        val ACTION_STOP = "ACTION_STOP"
//        val SEND_START = "SEND_START"
//        val ACTION_STOPPED = "ACTION_STOPPED"
//        val ONGOING_NOTIFICATION_ID = 6660
//        val CHANNEL_ID = "cam_service_channel_id"
//        val CHANNEL_NAME = "cam_service_channel_name"
//        var IS_START_SEND = false
//        var ACTION_CANCEL = "ACTION_CANCEL_SERVICE"
//
//        private const val SERVER_URL = "ws://192.168.100.17:9999/ws/image"
////        private const val SERVER_URL = "ws://192.168.1.41:9999/ws/image"
//        private const val IMAGE_MIME_TYPE = "video/avc"
//        private const val IMAGE_WIDTH = 1920
//        private const val IMAGE_HEIGHT = 1080
//        private const val BUFFER_SIZE = 3
//        private const val IMAGE_BIT_RATE = 3 * 1024 * 1024 // 3 Mbps
//        private const val VIDEO_BITRATE = 5000000
//        private const val FRAME_RATE = 30
//        private const val I_FRAME_INTERVAL = 1
//    }
//}