package com.aisct.android_camera.module

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner


class LifeCycleChecker : Application(), LifecycleEventObserver {
    var isForeground = false
    private val lifecycle by lazy { ProcessLifecycleOwner.get().lifecycle }

    override fun onCreate() {
        super.onCreate()
        Log.d("tag_lc", "옵저버 생성")
        lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d("tag_lc", "targetState : ${event.targetState} event : $event")
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                isForeground = false
                Log.d("tag_lc", "앱이 백그라운드로 전환")
            }
            Lifecycle.Event.ON_START -> {
                isForeground = true
                Log.d("tag_lc", "앱이 포그라운드로 전환")
            }
            else -> {}
        }
    }
}