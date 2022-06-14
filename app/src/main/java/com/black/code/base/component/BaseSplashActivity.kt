package com.black.code.base.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.black.code.util.Log

@SuppressLint("CustomSplashScreen")
abstract class BaseSplashActivity : AppCompatActivity() {
    @Volatile private var isCompleted = false
    @Volatile private var isCanceled = false
    private var keepOnSplashScreen = true

    // 객체 임의 생성을 막기 위해 추상화 클래스로 정의
    abstract class Splash(private val activity: Activity) {
        /**
         * Splash 처리 완료 또는 Dialog 등의 UI 노출 전 필수로 호출 필요
         * @param nextActivityClass SplashActivity 종료 후 해당 Activity 실행, null인 경우 startNextActivity 수동 호출 필요
         */
        abstract fun complete(nextActivityClass : Class<out Activity>?)
        abstract fun isCanceled() : Boolean

        /**
         * 다음 Activity 실행
         * complete(null) 호출한 경우 수동으로 호출 필요
         */
        fun startNextActivity(nextActivityClass : Class<out Activity>) {
            val intent = Intent(activity, nextActivityClass).apply {
                putExtras(activity.intent)
                data = activity.intent.data
            }

            with (activity) {
                startActivity(intent)
                finish()
                // Android 12 미만 FadeOut 애니메이션 적용
                // Android 12 이상에서는 Splash View에 가려져서 애니메이션이 보이지 않는다.
                overridePendingTransition(0, android.R.anim.fade_out)
            }
        }
    }

    // 익명 인터페이스 구현
    private val splash by lazy {
        object : Splash(this) {
            override fun complete(nextActivityClass : Class<out Activity>?) {
                if (isCanceled) {
                    Log.w("Splash canceled")
                    return
                }
                Log.d("splash complete")
                isCompleted = true

                if (nextActivityClass == null) {
                    keepOnSplashScreen = false
                } else {
                    /*
                    Android 12 미만에서 Splash 완료 즉시 다음 Activity로 이동하는 경우
                    Splash View가 해제되면서 Splash Activity 배경이 보이면서 깜빡이는 현상이 있기 때문에
                    keepOnSplashScreen을 그대로 유지한 상태에서 다음 Activity를 실행함
                     */
                    startNextActivity(nextActivityClass)
                }
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
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        Log.d(this::class.java.simpleName)
        super.onResume()
        if (!isCompleted) {
            isCanceled = false
            onSplashStart(splash)
        }
    }

    override fun onPause() {
        Log.d(this::class.java.simpleName)
        if (!isCompleted) {
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