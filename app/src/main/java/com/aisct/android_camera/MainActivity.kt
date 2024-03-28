package com.aisct.android_camera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.aisct.android_camera.databinding.ActivityMainBinding
import com.aisct.android_camera.service.CamService
import com.aisct.android_camera.util.isServiceRunning


class MainActivity : AppCompatActivity() {
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var previewView: PreviewView
    private val mainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private var mHeight: Int = 0
    private var mWidth:Int = 0
    var mCameraId = CAMERA_BACK
    companion object
    {
        const val CAMERA_BACK = "0"
        const val CAMERA_FRONT = "1"
    }
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            when (p1?.action) {
                CamService.ACTION_STOPPED -> flipButtonVisibility(false)
                CamService.ACTION_START -> flipButtonVisibility(true)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("tag_lc", "MainActivity - override fun onCreate()")
        setContentView(mainBinding.root)
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
        with(DisplayMetrics()){
            windowManager.defaultDisplay.getMetrics(this)
            mHeight = heightPixels
            mWidth = widthPixels
        }

        mainBinding.butStart.setOnClickListener {
            Log.d("tag_lc", "mainBinding.butStart click")
            if (!isServiceRunning(this, CamService::class.java)) {
                notifyService(CamService.ACTION_START)
                Toast.makeText(
                    this,
                    "background service start",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

        mainBinding.butStop.setOnClickListener {
            Log.d("tag_lc", "mainBinding.butStop click")
            notifyService(CamService.ACTION_STOP)
        }
    }

    private fun notifyService(action: String) {
        val intent = Intent(this, CamService::class.java)
        intent.action = action
        startService(intent)
    }

    private fun flipButtonVisibility(running: Boolean) {
        Log.d("tag_lc", "private fun flipButtonVisibility")
        mainBinding.butStart.visibility =  if (running) View.GONE else View.VISIBLE
        mainBinding.butStop.visibility =  if (running) View.VISIBLE else View.GONE
//        mainBinding.butStartPreview.visibility =  if (running) View.GONE else View.VISIBLE
    }
}