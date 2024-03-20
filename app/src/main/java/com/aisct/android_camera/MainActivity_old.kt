package com.aisct.android_camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.aisct.android_camera.databinding.ActivityMainOldBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity_old : AppCompatActivity() {
    private val mainBinding: ActivityMainOldBinding by lazy {
        ActivityMainOldBinding.inflate(layoutInflater)
    }
    private val REQUEST_PERMISSIONS = 1
    private var isPlaying = false;
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalysis: ImageAnalysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainBinding.root)
        checkPermission()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setEvenetListener()
        startCamera()
    }

    override fun onResume() {
        super.onResume()
        if (MainService.isRunning) {
            Log.d("tag_lc", "override fun onResume()!")
            stopCameraService()
            if (isPlaying) {
                Log.d("tag_lc", "override fun onResume()! isPlaying true")
//                startAnalysis()
                startCamera()
                startAnalysis()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("tag_lc", "override fun onPause()!")
        // 앱이 백그라운드로 전환될 때 서비스 시작
        if (!isChangingConfigurations) { // 화면 회전 등의 설정 변경이 아닐 때만 실행
            startCameraService()
        }
    }
//    override fun onStop() {
//        super.onStop()
//        // 앱이 백그라운드로 전환될 때 서비스 시작
//        if (!isChangingConfigurations) { // 화면 회전 등의 설정 변경이 아닐 때만 실행
//            startCameraService()
//        }
//    }

    inner class imageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            Log.d("tag_lc", "Image analyzed!")
            image.close()
        }
    }

    private fun startAnalysis() {
        imageAnalysis?.let {
            it.setAnalyzer(cameraExecutor, imageAnalyzer())
        }
    }

    private fun stopAnalysis() {
        imageAnalysis?.clearAnalyzer()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(mainBinding.previewView.surfaceProvider)
            }

            try {
                imageAnalysis = ImageAnalysis.Builder().build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis!!)
            } catch(e: Exception) {
                // 에러 처리
                Log.d("tag_lc", "error ocurred : startCamera()")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCameraService() {
        Log.d("tag_lc", "------- startCameraService")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("tag_lc", "------- startCameraService Build.VERSION.SDK_INT >= Build.VERSION_CODES.O")
            Intent(this, MainService::class.java).also { intent ->
                startForegroundService(intent)
            }
        }
    }

    private fun stopCameraService() {
        Log.d("tag_lc", "------- stopCameraService")
        Intent(this, MainService::class.java).also { intent ->
            stopService(intent)
        }
    }

    private fun setEvenetListener() {
        Log.d("tag_lc", "setEvenetListener()")
        mainBinding.captureIB.setOnClickListener {
            if (!isPlaying) {
                Log.d("tag_lc", "mainBinding.captureIB.isEnabled")
                mainBinding.captureIB.setImageResource(R.drawable.ic_stop)
//                startCameraService()
                startAnalysis()
            } else {
                Log.d("tag_lc", "mainBinding.captureIB.isEnabled NOT!")
                mainBinding.captureIB.setImageResource(R.drawable.ic_start)
//                stopCameraService()
                stopAnalysis()
            }
            isPlaying = !isPlaying
        }
    }

    private fun checkPermission() {
        Log.d("tag_lc", "------- checkPermission")
        var permission = mutableMapOf<String, String>()
        permission["internet"] = Manifest.permission.INTERNET
        permission["postNotification"] = Manifest.permission.POST_NOTIFICATIONS
        permission["recodeAudio"] = Manifest.permission.RECORD_AUDIO
        permission["camera"] = Manifest.permission.CAMERA
        permission["systemAlertWindow"] = Manifest.permission.SYSTEM_ALERT_WINDOW
        permission["foregroundService"] = Manifest.permission.FOREGROUND_SERVICE

        // 현재 권한 상태 검사
        var denied = permission.count { ContextCompat.checkSelfPermission(this, it.value)  == PackageManager.PERMISSION_DENIED }

        // 마시멜로 버전 이후
        if(denied > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestPermissions(permission.values.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d("tag_lc", "------- onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_PERMISSIONS) {
            /* 1. 권한 확인이 다 끝난 후 동의하지 않은것이 있다면 종료 */
//            var count = grantResults.count { it == PackageManager.PERMISSION_DENIED } // 동의 안한 권한의 개수
//            if(count != 0) {
//                Toast.makeText(applicationContext, "서비스의 필요한 권한입니다.\n권한에 동의해주세요.", Toast.LENGTH_SHORT).show()
//                finish()
//            }
            /* 2. 권한 요청을 거부했다면 안내 메시지 보여주며 앱 종료 */
            grantResults.forEach {
                if(it == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(applicationContext, "서비스의 필요한 권한입니다.\n권한에 동의해주세요.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    companion object {
        var isRunning = false
    }
}