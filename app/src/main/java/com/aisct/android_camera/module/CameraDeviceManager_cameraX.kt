package com.aisct.android_camera.module

import android.content.Context
import androidx.camera.core.ImageAnalysis
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object CameraDeviceManager_cameraX {
    private var imageReader: ImageReader? = null
    private var targetSurfaces: ArrayList<Surface>? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    fun startCameraPreview(previewView: PreviewView, context: Context, lifecycleOwner: LifecycleOwner) {
        // 카메라 프리뷰를 시작하는 로직 구현
        Log.d("tag_lc", "textureView.width : " + previewView.width + " + " + previewView.height)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            } catch(e: Exception) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getAnalysisUseCase(analyzer: ImageAnalysis.Analyzer): ImageAnalysis {
        Log.d("tag_lc", "CamService - fun getAnalysisUseCase()")
        return ImageAnalysis.Builder()
            .build().also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }
    }

    fun setupImageAnalysis(context: Context, analyzer: ImageAnalysis.Analyzer) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            if (imageAnalysis == null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .build().also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }
            }

            try {
                cameraProvider.unbindAll() // Optionally unbind existing use cases before binding new ones
                // Note: Not binding image analysis to a lifecycle here because services don't have a LifecycleOwner
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopImageAnalysis() {
        imageAnalysis?.clearAnalyzer()
        // Optionally, clear or shutdown other resources related to image analysis
    }

    fun removeImageReader() {
        targetSurfaces?.remove(imageReader!!.surface)
    }

    fun stopCamera() {
        Log.d("tag_lc", "CamService - private fun stopCamera()")
        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}