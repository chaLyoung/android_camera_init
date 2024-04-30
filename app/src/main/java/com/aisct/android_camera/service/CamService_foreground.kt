package com.aisct.android_camera.service//package com.aisct.android_camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aisct.android_camera.MainActivity
import com.aisct.android_camera.R
import com.aisct.android_camera.databinding.ActivityMainBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.math.absoluteValue


class CamService_foreground : Service() {
    // UI
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private lateinit var mainBinding: ActivityMainBinding

    // Camera2-related stuff
    private var cameraManager: CameraManager? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var imageReader: ImageReader? = null
    private var isOpened = false

    private lateinit var webSocket: WebSocket
    private lateinit var surface: Surface
    private lateinit var mediaCodec: MediaCodec
    private lateinit var mediaMuxer: MediaMuxer
    // You can start service in 2 modes - 1.) with preview 2.) without preview (only bg processing)
    private var shouldShowPreview = true

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {}
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {}
    }

    private fun initWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://192.168.100.17:9999/ws/image").build()
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                // 연결 성공 시, 로그 출력
                Log.d("tag_lc", "WebSocket connection opened")
                isOpened = true
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // 서버로부터 연결 종료 요청 시, 로그 출력
                Log.d("tag_lc", "WebSocket connection closing!!!!!!!!!!!!!!! : $reason")
                isOpened = false
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // 연결 종료 시, 로그 출력
                Log.d("tag_lc", "WebSocket connection closed!!!!!!!!!!!!!!!!!!!!! : $reason")
                isOpened = false
                Log.d("tag_lc", "WebSocket connection failed" + webSocket.toString())
                initWebSocket()
            }
            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: okhttp3.Response?
            ) {
                Log.d("tag_lc", "WebSocket connection failed", t)
                isOpened = false
                initWebSocket()
            }
        }
//        client.dispatcher.executorService.shutdown()
        webSocket = client.newWebSocket(request, wsListener)
    }

    private fun initMediaCodec() {
        try {
//            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, IMAGE_WIDTH, IMAGE_HEIGHT)x
            val mediaFormat = MediaFormat.createVideoFormat(IMAGE_MIME_TYPE, IMAGE_WIDTH, IMAGE_HEIGHT)
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)

            mediaCodec = MediaCodec.createEncoderByType(IMAGE_MIME_TYPE)
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = mediaCodec.createInputSurface()
            mediaCodec.start()
            createCaptureSession()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createCaptureSession() {
        Log.d("tag_lc", "CamService - private fun createCaptureSession()")
        try {
            val targetSurfaces = listOf(surface)
            cameraDevice?.createCaptureSession(targetSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(surface)
                        set(CaptureRequest.JPEG_ORIENTATION, 90)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    }
                    Log.d("tag_lc", "CamService - CameraCaptureSession.StateCallback()")
                    session.setRepeatingRequest(captureRequest.build(), captureCallback, null)
//                    handler!!.post { sendVideoData() }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d("tag_lc", session.toString())
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.d("tag_lc", e.toString())
        }
    }

    private fun sendVideoData() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                val data = ByteArray(bufferInfo.size)
                outputBuffer?.get(data)
                Log.d("tag_lc", "----------------------- send -----------------")
//                webSocket.send(ByteString.of(*data))
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            initMediaCodec()
        }
        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            currentCameraDevice.close()
            cameraDevice = null
        }
        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            currentCameraDevice.close()
            cameraDevice = null
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d("tag_lc", "CamService - override fun onBind")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        val inflater = LayoutInflater.from(this)
        mainBinding = ActivityMainBinding.inflate(inflater, null, false)
        sendBroadcast(Intent(ACTION_START))
        startForeground()
    }

    private fun start() {
        shouldShowPreview = false
        initWebSocket()
        initCamera(1920, 1080)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaCodec.stop()
            mediaCodec.release()
            mediaMuxer.stop()
            mediaMuxer.release()
            stopCamera()
        } catch (e: Exception) {
            Log.e("tag_lc", "CamService - Error on onDestroy", e)
        }
        if (rootView != null)
            wm?.removeView(rootView)
        sendBroadcast(Intent(ACTION_STOPPED))
    }

    private fun initCamera(width: Int, height: Int) {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camId: String? = null
        for (id in cameraManager!!.cameraIdList) {
            val characteristics = cameraManager!!.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                camId = id
                break
            }
        }
        previewSize = chooseSupportedSize(camId!!, width, height)
        try {
            cameraManager!!.openCamera(camId, stateCallback, null)
        } catch (e:SecurityException) {
            Log.d("tag_lc", "SecurityException" + e.toString())
        }
    }

    private fun chooseSupportedSize(camId: String, textureViewWidth: Int, textureViewHeight: Int): Size {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Get all supported sizes for TextureView
        val characteristics = manager.getCameraCharacteristics(camId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = map!!.getOutputSizes(SurfaceTexture::class.java)
        // We want to find something near the size of our TextureView
        val texViewArea = textureViewWidth * textureViewHeight
        val texViewAspect = textureViewWidth.toFloat()/textureViewHeight.toFloat()
        val nearestToFurthestSz = supportedSizes.sortedWith(compareBy(
            // First find something with similar aspect
            {
                val aspect = if (it.width < it.height) it.width.toFloat() / it.height.toFloat()
                else it.height.toFloat()/it.width.toFloat()
                (aspect - texViewAspect).absoluteValue
            },
            // Also try to get similar resolution
            {
                (texViewArea - it.width * it.height).absoluteValue
            }
        ))
        Log.d("tag_lc", "CamService - " + nearestToFurthestSz.toString())
//        if (nearestToFurthestSz.isNotEmpty())
//            return nearestToFurthestSz[0]
        return Size(1920, 1080)
    }

    private fun stopCamera() {
        Log.d("tag_lc", "CamService - private fun stopCamera()")
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForeground() {
        Log.d("tag_lc", "CamService - private fun startForeground()")
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE)
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getText(R.string.app_name))
            .setContentText(getText(R.string.app_name))
            .setSmallIcon(R.drawable.ic_photo)
            .setContentIntent(pendingIntent)
            .setTicker(getText(R.string.app_name))
            .setContentIntent(pendingIntent) // PendingIntent 설정
            .setAutoCancel(true) // 사용자가 탭하면 자동으로 알림을 삭제
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }
    companion object {
        val TAG = "CamService"
        val ACTION_START = "eu.sisik.backgroundcam.action.START"
        val ACTION_STOP = "eu.sisik.backgroundcam.action.STOP"
        val ACTION_STOPPED = "eu.sisik.backgroundcam.action.STOPPED"
        val ONGOING_NOTIFICATION_ID = 6660
        val CHANNEL_ID = "cam_service_channel_id"
        val CHANNEL_NAME = "cam_service_channel_name"

        private const val SERVER_URL = "ws://your_websocket_server_url"
        private const val IMAGE_MIME_TYPE = "video/avc"
        private const val IMAGE_WIDTH = 1920
        private const val IMAGE_HEIGHT = 1080
        private const val BUFFER_SIZE = 3
        private const val IMAGE_BIT_RATE = 3 * 1024 * 1024 // 3 Mbps
        private const val VIDEO_BITRATE = 2000000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
    }
}