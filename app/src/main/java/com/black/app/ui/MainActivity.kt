package com.black.app.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.black.app.deeplink.Deeplink
import com.black.app.deeplink.DeeplinkManager
import com.black.app.service.ForegroundServiceController
import com.black.app.ui.navigation.AppNavHost
import com.black.app.ui.navigation.AppNavigator
import com.black.app.ui.navigation.AppRoute
import com.black.app.util.ComponentExtensions.launch
import com.black.core.ui.theme.BlackTheme
import com.black.core.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var deeplinkManager: DeeplinkManager
    @Inject lateinit var appNavigator: AppNavigator
    @Inject lateinit var foregroundServiceController: ForegroundServiceController

    private val permissionHelper = PermissionHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        initializeSplashScreen()
        super.onCreate(savedInstanceState)

        // 앱 디버깅 시에 서비스가 실행되도록 설정
        launch { foregroundServiceController.startIfActivated() }
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
                    CollectDeeplink(navController)
                }
            }
        }

        if (savedInstanceState == null) {
            intent?.let { receivedIntent -> launch { deeplinkManager.receiveDeeplink(receivedIntent.data) } }
            requestPermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch { deeplinkManager.receiveDeeplink(intent.data) }
    }

    /** Android 12 Splash 대응, 첫 화면이 그려지면 페이드아웃으로 사라짐 */
    private fun initializeSplashScreen() {
        installSplashScreen().setOnExitAnimationListener { provider ->
            ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).apply {
                duration = 300L
                doOnEnd { provider.remove() }
            }.start()
        }
    }

    /**
     * NavHost가 준비된 컴포지션에서 STARTED 구간 동안 딥링크 수집
     *
     * 스플래시 종료 콜백은 시스템 스플래시가 뜬 실행에서만 불려 configChanges로 처리하지 않는 언어·글꼴 크기 변경으로 다시 만들어지면 수집이 시작되지 않으므로
     * 컴포지션 수명에 맞춤. 이미 처리한 딥링크는 [DeeplinkManager]가 태그로 걸러 재수집 시 다시 이동하지 않음
     */
    @Composable
    private fun CollectDeeplink(navController: NavHostController) {
        LaunchedEffect(navController) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deeplinkManager.getDeeplinkFlow(this@MainActivity)
                    .map { Deeplink.parse(it) }
                    .collect {
                        when (it) {
                            is Deeplink.Navigate -> {
                                navController.navigate(it.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    if (it.clearBackStack) {
                                        popUpTo<AppRoute.Main> { inclusive = false }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
            }
        }
    }

    /**
     * 알림 권한 요청(첫 생성 때 한 번)
     *
     * 영구 거부 상태면 시스템 팝업 없이 끝나며 설정 화면으로 보내지 않음. 알림이 필요한 기능은 켤 때 설정 화면을 안내
     */
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        if (permissionHelper.checkPermissions(permissions).isGranted) {
            return
        }
        permissionHelper.requestPermissions(permissions, null)
    }
}
