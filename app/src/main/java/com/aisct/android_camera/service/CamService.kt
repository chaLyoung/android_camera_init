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
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aisct.android_camera.MainActivity
import com.aisct.android_camera.R
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.aisct.android_camera.model.DataProcess
import java.io.IOException
import java.util.Collections
import kotlin.math.absoluteValue
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.aisct.android_camera.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CamService : Service() {
    // UI
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private lateinit var mainBinding: ActivityMainBinding

    // Camera2-related stuff
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var textureView: TextureView? = null
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var session: OrtSession
    private var imageReader: ImageReader? = null
    private var isOpened = false
    private val dataProcess = DataProcess(context = this)
    private lateinit var surface: Surface
    private lateinit var mediaCodec: MediaCodec
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    // 코루틴 관련 변수 추가
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    var spsPpsSet = false
    var sps: ByteArray? = null
    var pps: ByteArray? = null

    private var shouldShowPreview = true

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
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

    private val captureStateCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
//            Log.d("tag_lc", "override fun onCaptureProgressed()")
            sendVideoData()
        }
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {}
    }

    private fun initMediaCodec() {
        try {
            Log.d("tag_lc", "private fun initMediaCodec()")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, IMAGE_WIDTH, IMAGE_HEIGHT)
            val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, IMAGE_WIDTH, IMAGE_HEIGHT)
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val capabilities = mediaCodec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            Log.d("tag_lc", "Color formats supported: ${capabilities.colorFormats.joinToString()}")
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = mediaCodec.createInputSurface()
            mediaCodec.start()
            createCaptureSession()
            Log.d("tag_lc", "private fun initMediaCodec() finish")
        } catch (e: IOException) {
            Log.d("tag_lc", "private fun initMediaCodec() error!!!")
            e.printStackTrace()
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader?.acquireLatestImage()
        if (image != null) {
            modelExecutor.execute {
                try {
                    // val yuvBytes = dataProcess.imageToNV21(image)
                    val bitmap = dataProcess.imageToBitmap(image)
                    val floatBuffer = dataProcess.bitmapToFloatBuffer(bitmap)
                    val inputName = session.inputNames.iterator().next() // session 이름
                    // 모델의 요구 입력값 [1 3 640 640] [배치 사이즈, 픽셀(RGB), 너비, 높이], 모델마다 크기는 다를 수 있음.
                    val shape = longArrayOf(
                        DataProcess.BATCH_SIZE.toLong(),
                        DataProcess.PIXEL_SIZE.toLong(),
                        DataProcess.INPUT_SIZE.toLong(),
                        DataProcess.INPUT_SIZE.toLong()
                    )
                    val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape)
                    val resultTensor = session.run(Collections.singletonMap(inputName, inputTensor))
                    val outputs = resultTensor.get(0).value as Array<*> // [1 84 8400]
                    val results = dataProcess.outputsToNPMSPredictions(outputs)
                    Log.d("tag_lc", results.toString())
                } catch (e: Exception) {
                    Log.d("tag_lc", "Error processing image " + e.toString())
                } finally {
                    Log.d("tag_lc", "image.close()")
                    image.close()
                }
            }
            Log.d("tag_lc", "Got image: " + image?.width + " x " + image?.height)
        }
    }

    private fun createCaptureSession() {
        Log.d("tag_lc", "CamService - private fun createCaptureSession()")
        try {
            val targetSurfaces = ArrayList<Surface>()
            imageReader = ImageReader.newInstance(
                MODEL_IMAGE_WIDTH, MODEL_IMAGE_HEIGHT,
                ImageFormat.YUV_420_888, 3
            )
            imageReader!!.setOnImageAvailableListener(imageListener, null)
//            targetSurfaces.add(surface)
            targetSurfaces.add(imageReader!!.surface)
            cameraDevice?.createCaptureSession(targetSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
//                        addTarget(surface)
//                        addTarget(previewSurface)
                        addTarget(imageReader!!.surface)
//                        set(CaptureRequest.JPEG_ORIENTATION, 0)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    }
                    Log.d("tag_lc", "CamService - CameraCaptureSession.StateCallback()")
                    session.setRepeatingRequest(captureRequest.build(), captureStateCallback, null)
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

    private fun sendVideoData() {
        val bufferInfo = MediaCodec.BufferInfo()
        if(isOpened) {
            try {
                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer?.get(data)
//                    webSocket.send(ByteString.of(*data))
//                    webSocketManager.sendMessage(ByteString.of(*data))
//                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("tag_lc", "Exception : $e")
            }
        } else {
//            Log.d("tag_lc", "----------------------- send is not opened -----------------")
        }
    }

    private fun initModelSession() {
        dataProcess.loadModel() // onnx 모델 불러오기
        dataProcess.loadLabel() // coco txt 파일 불러오기
        ortEnvironment = OrtEnvironment.getEnvironment()
        session = ortEnvironment.createSession(
            this.filesDir.absolutePath.toString() + "/" + DataProcess.FILE_NAME,
            OrtSession.SessionOptions()
        )
    }

    private fun initCamera(width: Int, height: Int) {
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
        try {
            cameraManager!!.openCamera(camId!!, cameraStateCallback, null)
        } catch (e:SecurityException) {
            Log.d("tag_lc", "SecurityException" + e.toString())
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
        initModelSession()
        webSocketManager = WebSocketManager(SERVER_URL, this)
        initCamera(1920, 1080)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaCodec.stop()
            mediaCodec.release()
            webSocketManager.closeConnection()
            stopCamera()
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

        private const val SERVER_URL = "ws://192.168.100.17:9999/ws/image"
        //        private const val SERVER_URL = "ws://192.168.1.41:9999/ws/image"
        private const val IMAGE_WIDTH = 1920
        private const val IMAGE_HEIGHT = 1080
        private const val MODEL_IMAGE_WIDTH = 640
        private const val MODEL_IMAGE_HEIGHT = 640
        private const val BUFFER_SIZE = 3
        private const val IMAGE_BIT_RATE = 3 * 1024 * 1024 // 3 Mbps
        private const val VIDEO_BITRATE = 5000000
        private const val FRAME_RATE = 25
        private const val I_FRAME_INTERVAL = 1
    }
}