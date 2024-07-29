package com.black.core.component

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.os.postDelayed
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.databinding.ViewDataBinding
import com.black.core.util.Log

@SuppressLint("CustomSplashScreen")
abstract class SplashActivity<T : ViewDataBinding>  : BaseActivity<T>() {
    companion object {
        private const val TIMEOUT_SPLASH_MS = 3000L
    }

    @Volatile var keepOnSplashScreen = true // Splash 화면 유지 여부

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this::class.java.simpleName)
        /*
        Android 12 Splash 대응
        https://developer.android.com/guide/topics/ui/splash-screen/migrate
         */
        // 참고 : 스플래시 노출 중엔 Dialog 등 모든 UI가 노출되지 않음
        installSplashScreen().apply {
            // setKeepOnScreenCondition에 설정한 Block은 짧은 주기로 계속 호출되면서 상태를 확인한다.
            setKeepOnScreenCondition { keepOnSplashScreen }
            setOnExitAnimationListener { provider ->
                // 페이드인 애니메이션 적용
                ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).apply {
                    duration = 300L
                    doOnEnd { provider.remove() }
                }.start()
            }
        }

        // 예외 상황 방지를 위해 최대 3초 동안만 Splash 화면 유지
        Handler(Looper.getMainLooper())
            .postDelayed(TIMEOUT_SPLASH_MS) {
                keepOnSplashScreen = false
            }
        super.onCreate(savedInstanceState)
    }
}