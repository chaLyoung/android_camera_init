package com.aisct.android_camera

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 1
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private val mainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            when (p1?.action) {
                CamService.ACTION_STOPPED -> flipButtonVisibility(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("tag_lc", "MainActivity - override fun onCreate()")
        setContentView(mainBinding.root)
        checkPermission()
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                // 여기서 결과 데이터를 처리합니다.
                Log.d("tag_lc", "activityResultLauncher" + result.data.toString())
            }
        }
        initView()
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(receiver, IntentFilter(CamService.ACTION_STOPPED), RECEIVER_EXPORTED)

        val running = isServiceRunning(this, CamService::class.java)
        flipButtonVisibility(running)
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(receiver)
    }

    private fun initView() {
        Log.d("tag_lc", "private fun initView()")
        mainBinding.butStart.setOnClickListener {
            Log.d("tag_lc", "mainBinding.butStart click")
            if (!isServiceRunning(this, CamService::class.java)) {
                notifyService(CamService.ACTION_START)
                finish()
            }
        }

        mainBinding.butStartPreview.setOnClickListener {
            Log.d("tag_lc", "mainBinding.butStartPreview click")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {

                // Don't have permission to draw over other apps yet - ask user to give permission
                val settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                activityResultLauncher.launch(settingsIntent)
                return@setOnClickListener
            }

            if (!isServiceRunning(this, CamService::class.java)) {
                notifyService(CamService.ACTION_START_WITH_PREVIEW)
                finish()
            }
        }

        mainBinding.butStop.setOnClickListener {
            Log.d("tag_lc", "mainBinding.butStop click")
            stopService(Intent(this, CamService::class.java))
        }
    }

    private fun notifyService(action: String) {

        val intent = Intent(this, CamService::class.java)
        intent.action = action
        startService(intent)
    }

    private fun flipButtonVisibility(running: Boolean) {

        mainBinding.butStart.visibility =  if (running) View.GONE else View.VISIBLE
        mainBinding.butStartPreview.visibility =  if (running) View.GONE else View.VISIBLE
        mainBinding.butStop.visibility =  if (running) View.VISIBLE else View.GONE
    }

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
            .setPermissions(Manifest.permission.INTERNET
                , Manifest.permission.POST_NOTIFICATIONS
                , Manifest.permission.RECORD_AUDIO
                , Manifest.permission.CAMERA
                , Manifest.permission.SYSTEM_ALERT_WINDOW
                , Manifest.permission.FOREGROUND_SERVICE)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CODE_PERM_CAMERA -> {
                if (grantResults?.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.err_no_cam_permission), Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
    companion object {

        val CODE_PERM_SYSTEM_ALERT_WINDOW = 6111
        val CODE_PERM_CAMERA = 6112

    }
}