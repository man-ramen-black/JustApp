package com.black.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.black.app.ui.maintab.MainTabFragment
import com.black.app.ui.maintab.main.alarm.AlarmFragment
import com.black.app.ui.maintab.main.appinstallnotification.AppInstallNotificationScreen
import com.black.app.ui.maintab.main.architecture.ArchitectureFragment
import com.black.app.ui.maintab.main.etc.BlackFragment
import com.black.app.ui.maintab.main.etc.EtcFragment
import com.black.app.ui.maintab.main.launcher.LauncherFragment
import com.black.app.ui.maintab.main.notification.NotificationFragment
import com.black.app.ui.maintab.main.recyclerview.RecyclerViewFragment
import com.black.app.ui.maintab.main.retrofit.RetrofitFragment
import com.black.app.ui.maintab.main.service.ServiceFragment
import com.black.app.ui.maintab.main.studypopup.StudyPopupSettingScreen
import com.black.app.ui.maintab.main.texteditor.TextEditorScreen
import com.black.app.ui.maintab.main.usagetimer.UsageTimerScreen
import com.black.feature.floatingbutton.ui.FloatingSettingScreen
import com.black.feature.pokerogue.ui.PokeRogueFragment

/**
 * 루트 내비게이션 호스트. 미전환 Fragment는 AndroidFragment로 래핑.
 * 전환 효과: 기존 nav_default 기본 애니메이션을 fadeIn/fadeOut으로 대체(브릿지 기간 시각 차이 허용)
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.MainTab,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) {
        composable<AppRoute.MainTab> { AndroidFragment<MainTabFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Black> { AndroidFragment<BlackFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.TextEditor> {
            TextEditorScreen(onNavigateUp = { navController.navigateUp() }, modifier = Modifier.fillMaxSize())
        }
        composable<AppRoute.RecyclerView> { AndroidFragment<RecyclerViewFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Retrofit> { AndroidFragment<RetrofitFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Service> { AndroidFragment<ServiceFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Alarm> { AndroidFragment<AlarmFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Architecture> { AndroidFragment<ArchitectureFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Launcher> { AndroidFragment<LauncherFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.StudyPopup> {
            StudyPopupSettingScreen(
                onNavigateUp = { navController.navigateUp() },
                modifier = Modifier.fillMaxSize(),
                // 화면 켜짐을 감지하는 서비스는 앱 설치 알림 화면이 켜고 끔
                onOpenAppInstallNotification = {
                    navController.navigate(AppRoute.AppInstallNotification) { launchSingleTop = true }
                },
            )
        }
        composable<AppRoute.UsageTimer> {
            UsageTimerScreen(onNavigateUp = { navController.navigateUp() }, modifier = Modifier.fillMaxSize())
        }
        composable<AppRoute.Etc> { AndroidFragment<EtcFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Notification> { AndroidFragment<NotificationFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.PokeRogue> { AndroidFragment<PokeRogueFragment>(Modifier.fillMaxSize()) }
        composable<AppRoute.Floating> {
            FloatingSettingScreen(onNavigateUp = { navController.navigateUp() }, modifier = Modifier.fillMaxSize())
        }
        composable<AppRoute.AppInstallNotification> {
            AppInstallNotificationScreen(onNavigateUp = { navController.navigateUp() }, modifier = Modifier.fillMaxSize())
        }
    }
}
