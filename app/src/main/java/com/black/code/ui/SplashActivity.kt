package com.black.code.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.black.code.base.component.BaseSplashActivity
import com.black.code.util.Util

/**
 * https://lanace.github.io/articles/right-way-on-splash/
 * https://velog.io/@pish11010/Android-Splash-Screen-%EA%B5%AC%ED%98%84
 **/
class SplashActivity : BaseSplashActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onSplashStart(splash: Splash) {
        doSomething {
            splash.complete(MainActivity::class.java)
        }
    }

    override fun onSplashCanceled() {
        cancelDoSomething()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 설치 시 화면을 켬
        Util.turnScreenOn(this)
    }

    private fun doSomething(callback: () -> Unit) {
        handler.postDelayed({
            callback()
        }, 1000)
    }

    private fun cancelDoSomething() {
        handler.removeCallbacksAndMessages(null)
    }
}