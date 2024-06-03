package com.aisct.android_camera.service//package com.aisct.android_camera

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
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.Image
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
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aisct.android_camera.MainActivity
import com.aisct.android_camera.R
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.aisct.android_camera.model.DataProcess
import com.aisct.android_camera.network.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.absoluteValue

class CamService_new : Service() {
    // UI
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private lateinit var mainBinding: ActivityMainBinding

    // Camera2-related stuff
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var textureView: TextureView? = null
    private lateinit var imageReader: ImageReader
    private var isOpened = false

    private lateinit var webSocketManager: WebSocketManager
    private lateinit var surface: Surface
    private lateinit var mediaCodec: MediaCodec
    private var shouldShowPreview = true
//    private val dataProcess = DataProcess(context = `this`)

    var spsPpsSet = false
    var sps: ByteArray? = null
    var pps: ByteArray? = null

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
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

    private fun initCamera(width: Int, height: Int) {
//        initOverlay()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camId: String? = null
        for (id in cameraManager!!.cameraIdList) {
            val characteristics = cameraManager!!.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            Log.d("tag_lc", "cameraManager!!.cameraIdList.toString() : " + characteristics.toString())
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                camId = id
                break
            }
        }
        Log.d("tag_lc", "camId : " + camId.toString())
        try {
            cameraManager!!.openCamera(camId!!, cameraStateCallback, null)
        } catch (e:SecurityException) {
            Log.d("tag_lc", "SecurityException" + e.toString())
        }
    }

    private fun createCaptureSession() {
        Log.d("tag_lc", "CamService - private fun createCaptureSession()")
        try {
            val targetSurfaces = ArrayList<Surface>()
//            targetSurfaces.add(surface)
            imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.YUV_420_888, 2)
            imageReader.setOnImageAvailableListener(imageListener, null)
            targetSurfaces.add(imageReader.surface)
            val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
//                addTarget(surface)
                addTarget(imageReader!!.surface)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }
            cameraDevice?.createCaptureSession(targetSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    initMediaCodec()
                    session.setRepeatingRequest(captureRequest.build(), null, null)
//                    session.setRepeatingRequest(captureRequest.build(), captureStateCallback, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d("tag_lc", session.toString())
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Log.d("tag_lc", e.toString())
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader?.acquireLatestImage()
        if (image != null) {
//            val yuvBytes = dataProcess.imageToNV21(image)
//            encodeToH264(yuvBytes)
            enqueueImageForEncoding(image)

//            val bitmap = dataProcess.imageToBitmap(image)
        }
        image?.close()
    }

    private fun encodeToH264(data: ByteArray) {
        var offset = 0
        val dataSize = data.size

        while (offset < dataSize) {
            val inputBufferId = mediaCodec.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                val inputBuffer: ByteBuffer = mediaCodec.getInputBuffer(inputBufferId) ?: continue
                inputBuffer.clear()

                val chunkSize = minOf(inputBuffer.capacity(), dataSize - offset)
                inputBuffer.put(data, offset, chunkSize)
                mediaCodec.queueInputBuffer(inputBufferId, 0, chunkSize, System.nanoTime() / 1000, 0)

                offset += chunkSize
            }
        }
    }

    private fun enqueueImageForEncoding(image: Image) {
        val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex) ?: return
            inputBuffer.clear()

            // Fill the input buffer with YUV data from the image
            fillYUVBuffer(image, inputBuffer)

            mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), System.nanoTime() / 1000, 0)
        }
    }

    private fun fillYUVBuffer(image: Image, inputBuffer: ByteBuffer) {
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[1].buffer
        val vPlane = planes[2].buffer

        inputBuffer.put(yPlane)
        inputBuffer.put(uPlane)
        inputBuffer.put(vPlane)
    }

    private fun initMediaCodec() {
        try {
            Log.d("tag_lc", "private fun initMediaCodec()")
            val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, IMAGE_WIDTH, IMAGE_HEIGHT)
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val capabilities = mediaCodec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            Log.d("tag_lc", "Color formats supported: ${capabilities.colorFormats.joinToString()}")
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    val inputBuffer = codec.getInputBuffer(index) ?: return
                    inputBuffer.clear()

                    // Prepare input buffer for encoding
//                    prepareInputBuffer(inputBuffer)

                    codec.queueInputBuffer(index, 0, inputBuffer.limit(), System.nanoTime() / 1000, 0)
                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    val outputBuffer = codec.getOutputBuffer(index) ?: return
                    val data = ByteArray(info.size)
                    outputBuffer.get(data)
                    outputBuffer.clear()

                    // Send the encoded data via WebSocket
                    webSocketManager.sendMessage(ByteString.of(*data))

                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.d("tag_lc", "MediaCodec error: ${e.message}")
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Log.d("tag_lc", "MediaCodec output format changed: $format")
                }
            }, null)
            mediaCodec.start()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun initOverlay() {
        val li = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        rootView = li.inflate(R.layout.overlay, null)
        textureView = rootView?.findViewById(R.id.texPreview)

        val type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm!!.addView(rootView, params)
    }

    override fun onCreate() {
        super.onCreate()
        val inflater = LayoutInflater.from(this)
        mainBinding = ActivityMainBinding.inflate(inflater, null, false)
        sendBroadcast(Intent(ACTION_START))
        startForeground()
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

    private fun start() {
        shouldShowPreview = false
//        webSocketManager = WebSocketManager(SERVER_URL, this)
        initCamera(1920, 1080)
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d("tag_lc", "CamService - override fun onBind")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopCamera()
            mediaCodec.stop()
            mediaCodec.release()
            webSocketManager.closeConnection()
        } catch (e: Exception) {
            Log.e("tag_lc", "CamService - Error on onDestroy", e)
        }
        if (rootView != null)
            wm?.removeView(rootView)
        sendBroadcast(Intent(ACTION_STOPPED))
    }

    private fun stopCamera() {
        Log.d("tag_lc", "CamService - private fun stopCamera()")
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader.close()
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
        val ACTION_START = "eu.sisik.backgroundcam.action.START"
        val ACTION_STOP = "eu.sisik.backgroundcam.action.STOP"
        val ACTION_STOPPED = "eu.sisik.backgroundcam.action.STOPPED"
        val ONGOING_NOTIFICATION_ID = 6660
        val CHANNEL_ID = "cam_service_channel_id"
        val CHANNEL_NAME = "cam_service_channel_name"

        private const val SERVER_URL = "ws://192.168.100.17:9999/ws/image"
//        private const val SERVER_URL = "ws://192.168.1.41:9999/ws/image"
        private const val IMAGE_MIME_TYPE = "video/avc"
        private const val IMAGE_WIDTH = 1920
        private const val IMAGE_HEIGHT = 1080
        private const val BUFFER_SIZE = 3
        private const val IMAGE_BIT_RATE = 3 * 1024 * 1024 // 3 Mbps
        private const val VIDEO_BITRATE = 5000000
        private const val FRAME_RATE = 25
        private const val I_FRAME_INTERVAL = 1
    }
}