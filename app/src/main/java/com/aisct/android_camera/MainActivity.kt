package com.aisct.android_camera

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view  = binding.root
//        setContentView(R.layout.activity_main)    // 이전 버전
        setContentView(view)
        setPermission() // 최초 카메라 권한 체크
    }

    private fun takeCapture() {
        // 기본 카메라 앱 실행
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.aisct.android_camera",
                        it
                    )
                }
            }
        }
    }

    /* 이미지 파일 생성 */
    private fun createImageFile(): File? {
        val timestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    }

    /* 테드 퍼미션 설정 */
    private fun setPermission() {
        val permission = object : PermissionListener {
            override fun onPermissionGranted() {
                Toast.makeText(this@MainActivity, "권한이 허용 되었습니다.", Toast.LENGTH_SHORT).show()
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Toast.makeText(this@MainActivity, "권한이 거부 되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.create()
            .setPermissionListener(permission)
            .setDeniedMessage("권한을 허용해주세요. [설정] > [앱 및 알림] > [고급] > [앱 권한]")
            .setPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            , android.Manifest.permission.CAMERA
                            , android.Manifest.permission.RECORD_AUDIO)
            .check()
    }
}

