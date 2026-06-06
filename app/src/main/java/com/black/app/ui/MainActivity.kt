package com.black.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.os.postDelayed
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.black.app.deeplink.Deeplink
import com.black.app.deeplink.DeeplinkManager
import com.black.app.service.ForegroundService
import com.black.app.ui.navigation.AppNavHost
import com.black.app.ui.navigation.AppNavigator
import com.black.app.ui.navigation.AppRoute
import com.black.app.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TIMEOUT_SPLASH_MS = 3000L
    }

    @Inject lateinit var deeplinkManager: DeeplinkManager
    @Inject lateinit var appNavigator: AppNavigator

    private val permissionHelper = PermissionHelper(this)

    @Volatile private var keepOnSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        initializeSplashScreen()
        super.onCreate(savedInstanceState)

        // 앱 디버깅 시에 서비스가 실행되도록 설정
        ForegroundService.start(this, false)
        permissionHelper.init()

        setContent {
            BlackTheme {
                // Surface로 배경·콘텐츠 색(LocalContentColor) 적용
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    DisposableEffect(navController) {
                        appNavigator.bind(navController)
                        onDispose { appNavigator.unbind(navController) }
                    }
                    AppNavHost(navController)
                }
            }
        }

        if (savedInstanceState == null) {
            intent?.let { receivedIntent -> lifecycleScope.launch { deeplinkManager.receiveDeeplink(receivedIntent.data) } }
        }

        lifecycleScope.launch {
            doSomething()
            keepOnSplashScreen = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch { deeplinkManager.receiveDeeplink(intent.data) }
    }

    /** Android 12 Splash 대응(기존 SplashActivity 로직 이전) */
    private fun initializeSplashScreen() {
        installSplashScreen().apply {
            setKeepOnScreenCondition { keepOnSplashScreen }
            setOnExitAnimationListener { provider ->
                onSplashFinished()

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
    }

    private fun onSplashFinished() {
        requestPermission()

        // navController 바인딩 완료 후 collect 시작
        lifecycleScope.launch {
            deeplinkManager.getDeeplinkFlow(this)
                .map { Deeplink.parse(it) }
                .collect {
                    when (it) {
                        is Deeplink.Navigate -> {
                            appNavigator.navigate(it.route) {
                                launchSingleTop = true
                                restoreState = true
                                if (it.clearBackStack) {
                                    popUpTo<AppRoute.MainTab> { inclusive = false }
                                }
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    private suspend fun doSomething() = withContext(Dispatchers.IO) {
        delay(500)
    }

    private fun requestPermission() {
        val permissions = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            }
        ).toTypedArray()

        if (permissionHelper.checkPermissions(permissions).isGranted) {
            return
        }

        permissionHelper.requestPermissions(permissions) {}
    }
}
