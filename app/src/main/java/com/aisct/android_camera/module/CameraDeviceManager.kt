package com.aisct.android_camera.module

import android.content.Context
import android.graphics.ImageFormat
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
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import com.aisct.android_camera.service.CamService
import com.gun0912.tedpermission.provider.TedPermissionProvider.context
import kotlin.math.absoluteValue

object CameraDeviceManager {
    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSize: Size? = null
    private var imageReader: ImageReader? = null
    private var targetSurfaces: ArrayList<Surface>? = null
    private lateinit var requestBuilder: CaptureRequest.Builder

    fun startCameraPreview(surfaceView: SurfaceView) {
        // 카메라 프리뷰를 시작하는 로직 구현
        // CameraDevice, CameraCaptureSession 초기화 및 프리뷰 Surface 설정
        Log.d("tag_lc", "textureView.width : " + surfaceView.width + " + " + surfaceView.height)
        openCamera(surfaceView)
        previewSize = chooseSupportedSize(surfaceView.width, surfaceView.height)
    }

    private fun openCamera(surfaceView: SurfaceView) {
        try {
            Log.d("tag_lc", "CameraDeviceManager openCamera()")
            cameraManager.openCamera("0", object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(surfaceView)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.d("tag_lc", "CameraDeviceManager openCamera() Camera access exception", e)
        } catch (e: SecurityException) {
            Log.d("tag_lc", "CameraDeviceManager openCamera() Security exception", e)
        }
    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun chooseSupportedSize(textureViewWidth: Int, textureViewHeight: Int): Size {
        val characteristics = cameraManager.getCameraCharacteristics("0")
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = map!!.getOutputSizes(SurfaceTexture::class.java)

        Log.d("tag_lc", "nearestToFurthestSz : " + textureViewWidth + " + " + textureViewHeight)
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
        Log.d("tag_lc", "nearestToFurthestSz : " + nearestToFurthestSz.toString())
        if (nearestToFurthestSz.isNotEmpty()) {
            Log.d("tag_lc", "nearestToFurthestSz.isNotEmpty() : " + nearestToFurthestSz[0])
            return nearestToFurthestSz[0]
        }

        return Size(1440, 1080)
    }

    private fun createCaptureSession(surfaceView: SurfaceView) {
        Log.d("tag_lc", "CameraDeviceManager - private fun createCaptureSession()")
        try {
            targetSurfaces = ArrayList()
            requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                Log.d("tag_lc", "CameraDeviceManager - createCaptureRequest")
                val texture = surfaceView.holder.surface
                targetSurfaces!!.add(texture)
                addTarget(texture)
//                imageReader = ImageReader.newInstance(
//                    previewSize!!.width, previewSize!!.height,
//                    ImageFormat.YUV_420_888, 2
//                )
//                imageReader!!.setOnImageAvailableListener(imageListener, null)
//                targetSurfaces.add(imageReader!!.surface)
//                addTarget(imageReader!!.surface)
                // Set some additional parameters for the request
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                prepareCameraCaptureSession()
            }
        } catch (e: CameraAccessException) {
            Log.e(CamService.TAG, "createCaptureSession", e)
        }
    }

    private fun prepareCameraCaptureSession() {
        // Prepare CameraCaptureSession
        cameraDevice!!.createCaptureSession(targetSurfaces!!,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    Log.d("tag_lc", "CameraDeviceManager - cameraCaptureSession onConfigured")
                    // The camera is already closed
                    if (null == cameraDevice) {
                        return
                    }
                    captureSession = cameraCaptureSession
                    try {
                        // Now we can start capturing
                        Log.d("tag_lc", "CameraDeviceManager - CameraCaptureSession.StateCallback()")
                        captureRequest = requestBuilder!!.build()
                        captureSession!!.setRepeatingRequest(captureRequest!!, captureCallback, null)
                    } catch (e: CameraAccessException) {
                        Log.e(CamService.TAG, "createCaptureSession", e)
                    }
                }
                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Log.e(CamService.TAG, "createCaptureSession()")
                }
            }, null
        )
    }

    fun setupImageReader(imageListener: ImageReader.OnImageAvailableListener) {
        Log.d("tag_lc", "CameraDeviceManager - fun setupImageReader()")
        try {
            imageReader = ImageReader.newInstance(
                previewSize!!.width, previewSize!!.height,
                ImageFormat.YUV_420_888, 2
            )
            imageReader!!.setOnImageAvailableListener(imageListener, null)
            targetSurfaces!!.add(imageReader!!.surface)
            requestBuilder.addTarget(imageReader!!.surface)
            prepareCameraCaptureSession()
        } catch (e: CameraAccessException) {
            Log.e(CamService.TAG, "createCaptureSession", e)
        }
    }

//    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
//        val image = reader?.acquireLatestImage()
//        Log.d("tag_lc", "Got image: " + image?.width + " x " + image?.height)
//        image?.close()
//    }

    fun removeImageReader() {
        targetSurfaces?.remove(imageReader!!.surface)
        requestBuilder.removeTarget(imageReader!!.surface)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            Log.d("tag_lc", "CamService - captureCallback onCaptureProgressed")
        }
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            Log.d("tag_lc", "CamService - captureCallback onCaptureCompleted")
        }
    }

    fun stopCamera() {
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
}