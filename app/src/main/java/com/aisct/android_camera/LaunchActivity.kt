package com.aisct.android_camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission

class LaunchActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TedPermission.create()
            .setPermissions(Manifest.permission.INTERNET
                , Manifest.permission.POST_NOTIFICATIONS
                , Manifest.permission.RECORD_AUDIO
                , Manifest.permission.CAMERA
                , Manifest.permission.SYSTEM_ALERT_WINDOW
                , Manifest.permission.FOREGROUND_SERVICE)
            .setPermissionListener(object : PermissionListener{
                override fun onPermissionGranted() {
                    startActivity(Intent(this@LaunchActivity, MainActivity::class.java))
                    finish()
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    for(i in deniedPermissions!!) {
                        Toast.makeText(applicationContext, i, Toast.LENGTH_SHORT).show()
                    }
                }

            })
            .setDeniedMessage("앱을 실행하려면 권한을 허가하셔야합니다.")
            .check()
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

//    private fun setPermission() {
//        val permission = object : PermissionListener {
//            override fun onPermissionGranted() {
//                Toast.makeText(this@LaunchActivity, "권한이 허용 되었습니다.", Toast.LENGTH_SHORT).show()
//            }
//
//            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
//                Toast.makeText(this@LaunchActivity, "권한이 거부 되었습니다.", Toast.LENGTH_SHORT).show()
//            }
//        }
//        TedPermission.create()
//            .setPermissionListener(permission)
//            .setDeniedMessage("권한을 허용해주세요. [설정] > [앱 및 알림] > [고급] > [앱 권한]")
//            .setPermissions(Manifest.permission.INTERNET
//                , Manifest.permission.POST_NOTIFICATIONS
//                , Manifest.permission.RECORD_AUDIO
//                , Manifest.permission.CAMERA
//                , Manifest.permission.SYSTEM_ALERT_WINDOW
//                , Manifest.permission.FOREGROUND_SERVICE)
//            .check()
//    }
}