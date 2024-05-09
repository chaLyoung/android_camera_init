//package com.aisct.android_camera
//
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.content.IntentFilter
//import android.os.Bundle
//import android.util.DisplayMetrics
//import android.util.Log
//import android.view.View
//import android.widget.Toast
//import androidx.activity.result.ActivityResultLauncher
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.view.PreviewView
//import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import com.aisct.android_camera.databinding.ActivityMainBinding
//import com.aisct.android_camera.service.CamService as CamService
//
//
//class MainActivity_aud : AppCompatActivity() {
//    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
//    private lateinit var previewView: PreviewView
//    private var camService: CamService? = null
//    private val mainBinding: ActivityMainBinding by lazy {
//        ActivityMainBinding.inflate(layoutInflater)
//    }
//    private var mHeight: Int = 0
//    private var mWidth:Int = 0
//    var mCameraId = CAMERA_BACK
//    companion object
//    {
//        const val CAMERA_BACK = "0"
//        const val CAMERA_FRONT = "1"
//    }
//
////    private val connection = object : ServiceConnection {
////        override fun onServiceConnected(className: ComponentName, service: IBinder) {
////            val binder = service as CamService.LocalBinder
////            camService = binder.getService()
////            setupPreviewView(binder.getImageReaderSurface())
////        }
////
////        override fun onServiceDisconnected(arg0: ComponentName) {
////            camService = null
////        }
////    }
////
////    private fun setupPreviewView(surface: Surface) {
////        previewView.surfaceProvider
////        // Here you would set up a way to display the image data from the service
////    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("tag_lc", "MainActivity onDestroy()")
////        unbindService(connection)
//    }
//
//    private val receiver = object: BroadcastReceiver() {
//        override fun onReceive(p0: Context?, p1: Intent?) {
//            Log.d("tag_lc", "onReceive" + p0.toString())
//            Log.d("tag_lc", "onReceive" + p1.toString())
//            when (p1?.action) {
//                CamService.ACTION_STOPPED -> flipButtonVisibility(false)
//                CamService.SEND_START -> flipButtonVisibility(true)
//            }
//        }
//    }
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Log.d("tag_lc", "MainActivity - override fun onCreate()")
//        setContentView(mainBinding.root)
////        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
////            if (result.resultCode == Activity.RESULT_OK) {
////                val data: Intent? = result.data
////                // 여기서 결과 데이터를 처리합니다.
////                Log.d("tag_lc", "activityResultLauncher" + result.data.toString())
////            }
////        }
//        LocalBroadcastManager.getInstance(this).registerReceiver(
//            receiver, IntentFilter("AlertServiceFilter")
//        );
//        initView()
//    }
//
//    override fun onStart() {
//        super.onStart()
//        Log.d("tag_lc", "MainActivity - override fun onStart()")
//    }
//
//    override fun onResume() {
//        super.onResume()
//        registerReceiver(receiver, IntentFilter(CamService.ACTION_STOPPED), RECEIVER_EXPORTED)
////        val running = isServiceRunning(this, CamService::class.java)
//        val running =  if (CamService.IS_START_SEND) true else false
//        flipButtonVisibility(running)
//    }
//
//    override fun onPause() {
//        super.onPause()
//        Log.d("tag_lc", "MainActivity - override fun onPause()")
//        unregisterReceiver(receiver)
//    }
//
//    private fun initView() {
//        Log.d("tag_lc", "private fun initView()")
//        with(DisplayMetrics()){
//            windowManager.defaultDisplay.getMetrics(this)
//            mHeight = heightPixels
//            mWidth = widthPixels
//        }
//
//        mainBinding.butStart.setOnClickListener {
//            Log.d("tag_lc", "mainBinding.butStart click")
//            if (!CamService.IS_START_SEND) {
//                notifyService(CamService.SEND_START)
//                Toast.makeText(
//                    this,
//                    "background service start",
//                    Toast.LENGTH_SHORT
//                ).show()
////                finish()
//            }
//        }
//
//        mainBinding.butStop.setOnClickListener {
//            Log.d("tag_lc", "mainBinding.butStop click")
//            notifyService(CamService.ACTION_STOP)
//        }
//        notifyService(CamService.ACTION_START)
//    }
//
//    private fun notifyService(action: String) {
//        val intent = Intent(this, CamService::class.java)
//        intent.action = action
//        startService(intent)
//    }
//
//    private fun flipButtonVisibility(running: Boolean) {
//        Log.d("tag_lc", "private fun flipButtonVisibility : " + running.toString())
//        mainBinding.butStart.visibility =  if (running) View.GONE else View.VISIBLE
//        mainBinding.butStop.visibility =  if (running) View.VISIBLE else View.GONE
////        mainBinding.butStartPreview.visibility =  if (running) View.GONE else View.VISIBLE
//    }
//}