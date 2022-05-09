package com.black.code.base.component

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.black.code.ui.MainActivity
import com.black.code.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
abstract class BaseSplashActivity : AppCompatActivity() {
    @Volatile private var isCanceled = false
    @Volatile private var isCompleted = false

    // 캡슐화?를 위해 인터페이스로 정의
    interface Splash {
        fun complete()
        fun isCanceled() : Boolean
    }

    // 익명 인터페이스 구현
    private val splash by lazy {
        object : Splash {
            override fun complete() {
                if (isCanceled) {
                    Log.d("Splash complete canceled")
                    return
                }
                isCompleted = true
                startNextActivity()
            }

            override fun isCanceled() : Boolean {
                return isCanceled
            }
        }
    }

    abstract val nextActivityClass : Class<out Activity>
    abstract fun onSplashStart(splash: Splash)
    abstract fun onSplashCanceled()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this::class.java.simpleName)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        Log.d(this::class.java.simpleName)
        super.onResume()
        isCanceled = false
        onSplashStart(splash)
    }

    override fun onPause() {
        Log.d(this::class.java.simpleName)
        if (!isCompleted) {
            Log.d("Splash cancel")
            splash.isCanceled()
            onSplashCanceled()
        }
        super.onPause()
    }

    /**
     * launchMode가 singleTop, singleTask인 경우,
     * Intent.FLAG_ACTIVITY_SINGLE_TOP로 Activity를 실행한 경우
     * onPause -> onNewIntent -> onResume 순서로 실행됨
     * https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
     */
    override fun onNewIntent(intent: Intent?) {
        Log.d(this::class.java.simpleName)
        super.onNewIntent(intent)
        // startMainActivity()에서 onNewIntent의 intent를 사용하도록 setIntent 호출
        setIntent(intent)
    }

    private fun startNextActivity() {
        val intent = Intent(this, nextActivityClass).apply {
            data = intent.data
            putExtras(intent)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(0, android.R.anim.fade_out);
    }
}