package com.black.core.component

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.databinding.ViewDataBinding
import com.black.core.util.Log

@SuppressLint("CustomSplashScreen")
abstract class BaseSplashActivity<T : ViewDataBinding>  : BaseActivity<T>() {
    @Volatile private var isCanceled = false
    @Volatile private var keepOnSplashScreen = true

    // 객체 임의 생성을 막기 위해 추상화 클래스로 정의
    interface Splash {
        /**
         * Splash 처리 완료 또는 Dialog 등의 UI 노출 전 필수로 호출 필요
         */
        fun hide()
        fun isCanceled() : Boolean
    }

    // 익명 인터페이스 구현
    private val splash by lazy {
        object : Splash {
            override fun hide() {
                if (isCanceled) {
                    Log.w("Splash canceled")
                    return
                }
                Log.d("splash complete")
                keepOnSplashScreen = false
            }

            override fun isCanceled() : Boolean {
                return isCanceled
            }
        }
    }

    abstract fun onSplashStart(splash: Splash)
    abstract fun onSplashCanceled()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this::class.java.simpleName)
        /*
        Android 12 Splash 대응
        https://developer.android.com/guide/topics/ui/splash-screen/migrate
         */
        installSplashScreen().apply {
            // setKeepOnScreenCondition에 설정한 Block은 짧은 주기로 계속 호출되면서 상태를 확인한다.
            setKeepOnScreenCondition { keepOnSplashScreen }
            setOnExitAnimationListener { provider ->
                ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).apply {
                    duration = 300L
                    doOnEnd { provider.remove() }
                }.start()
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (keepOnSplashScreen) {
            isCanceled = false
            onSplashStart(splash)
        }
    }

    override fun onPause() {
        if (keepOnSplashScreen) {
            Log.d("Splash cancel")
            isCanceled = true
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
        // startNextActivity()에서 onNewIntent의 intent를 사용하도록 setIntent 호출
        setIntent(intent)
    }
}