package com.black.code.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * https://lanace.github.io/articles/right-way-on-splash/
 * https://velog.io/@pish11010/Android-Splash-Screen-%EA%B5%AC%ED%98%84
 **/
class SplashActivity : AppCompatActivity() {
    @Volatile private var isCanceled = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onResume() {
        isCanceled = false
        doSomething {
            startMainActivity()
        }
        super.onResume()
    }

    override fun onPause() {
        cancelMainActivity()
        cancelDoSomething()
        super.onPause()
    }

    private fun doSomething(callback: () -> Unit) {
        handler.postDelayed({
            callback()
        }, 1000)
    }

    private fun cancelDoSomething() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startMainActivity() {
        if (isCanceled) {
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            data = intent.data
            putExtras(intent)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private fun cancelMainActivity() {
        isCanceled = true
    }
}