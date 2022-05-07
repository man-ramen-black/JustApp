package com.black.code.ui

import android.os.Handler
import android.os.Looper
import com.black.code.base.component.BaseSplashActivity

/**
 * https://lanace.github.io/articles/right-way-on-splash/
 * https://velog.io/@pish11010/Android-Splash-Screen-%EA%B5%AC%ED%98%84
 **/
class SplashActivity : BaseSplashActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onSplashStart(splash: Splash) {
        doSomething {
            splash.complete()
        }
    }

    override fun onSplashCanceled() {
        cancelDoSomething()
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