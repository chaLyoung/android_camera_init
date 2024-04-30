package com.aisct.android_camera.service//package com.aisct.android_camera

import ImageUploadService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.PixelFormat
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
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
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
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.absoluteValue

class CamService_socket_frame : Service() {
    // UI
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var textureView: TextureView? = null
    private lateinit var mainBinding: ActivityMainBinding

    // Camera2-related stuff
    private var cameraManager: CameraManager? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var isOpened = false

    // WebSocket 클라이언트 선언
    private lateinit var webSocket: WebSocket

    private var mediaCodec: MediaCodec? = null
    private val videoMimeType = "video/avc"
    private val videoWidth = 1920
    private val videoHeight = 1080
    private val videoFrameRate = 30
    private val videoBitRate = 3 * 1024 * 1024 // 3 Mbps
    private val videoIFrameInterval = 2


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

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            initCam(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    // WebSocket 연결 초기화
    private fun initWebSocket() {
            val client = OkHttpClient()
            val request = Request.Builder().url("ws://192.168.1.41:9999/ws/image").build()
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
                    // 연결 실패 시, 로그 출력
                    Log.d("tag_lc", "WebSocket connection failed", t)
                    isOpened = false
//                    initWebSocket() // 서비스 꺼져 있을때 start 누르면 계속 요청 너무 많이 함... 10초에 한번씩 재요청 하는걸로 바꿀까 함
                }
            }
            // WebSocket 연결
            webSocket = client.newWebSocket(request, wsListener)
        }


    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader?.acquireLatestImage()
        if(image != null) {
            val buffer = image?.planes?.first()?.buffer
            val bytes = ByteArray(buffer!!.capacity())
            buffer.get(bytes)
            sendImageToServer(bytes)
        }
        image?.close()
    }

    private fun sendImageToServer(imageData: ByteArray) {
        val byteString = ByteString.of(*imageData)
        if(webSocket != null && isOpened) {
            Log.d("tag_lc", "WebSocket connection sendImageToServer : " + isOpened.toString())
            webSocket.send(byteString)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCaptureSession()
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

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        if (rootView != null)
            wm?.removeView(rootView)
        sendBroadcast(Intent(ACTION_STOPPED))
    }

    private fun start() {
        shouldShowPreview = false
        initWebSocket()
        initCam(1920, 1080)
    }

    private fun initCam(width: Int, height: Int) {
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

    private fun initMediaCodec() {
        try {
            val format = MediaFormat.createVideoFormat(videoMimeType, videoWidth, videoHeight)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, videoIFrameInterval)

            mediaCodec = MediaCodec.createEncoderByType(videoMimeType)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.createInputSurface()
            mediaCodec?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaCodec", e)
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

    private fun createCaptureSession() {
        Log.d("tag_lc", "CamService - private fun createCaptureSession()")
        try {
            // Prepare surfaces we want to use in capture session
            val targetSurfaces = ArrayList<Surface>()
            // Prepare CaptureRequest that can be used with CameraCaptureSession
            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                if (shouldShowPreview) {
                    val texture = textureView!!.surfaceTexture!!
                    texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                    val previewSurface = Surface(texture)
                    targetSurfaces.add(previewSurface)
                    addTarget(previewSurface)
                }
                imageReader = ImageReader.newInstance(
                    previewSize!!.getWidth(), previewSize!!.getHeight(),
                    ImageFormat.JPEG, 2
//                    ImageFormat.YUV_420_888, 2
                )
                imageReader!!.setOnImageAvailableListener(imageListener, null)
                targetSurfaces.add(imageReader!!.surface)
                addTarget(imageReader!!.surface)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
                Log.d("tag_lc", "CamService - imageReader")
                // Set some additional parameters for the request
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }

            // Prepare CameraCaptureSession
            cameraDevice!!.createCaptureSession(targetSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }
                        captureSession = cameraCaptureSession
                        try {
                            // Now we can start capturing
                            Log.d("tag_lc", "CamService - CameraCaptureSession.StateCallback()")
                            captureRequest = requestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(captureRequest!!, captureCallback, null)

                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "createCaptureSession", e)
                        }
                    }
                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(TAG, "createCaptureSession()")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCaptureSession", e)
        }
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


    companion object {
        val TAG = "CamService"
        val ACTION_START = "eu.sisik.backgroundcam.action.START"
        val ACTION_STOP = "eu.sisik.backgroundcam.action.STOP"
        val ACTION_STOPPED = "eu.sisik.backgroundcam.action.STOPPED"
        val ONGOING_NOTIFICATION_ID = 6660
        val CHANNEL_ID = "cam_service_channel_id"
        val CHANNEL_NAME = "cam_service_channel_name"

    }
}