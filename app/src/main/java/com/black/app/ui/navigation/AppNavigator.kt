package com.black.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * Fragment에서 루트 Compose NavController로 화면 이동하기 위한 브릿지.
 * Fragment 전량 Compose 전환 완료 후 제거 예정
 */
@ActivityRetainedScoped
class AppNavigator @Inject constructor() {

    private var navController: NavHostController? = null

    fun bind(controller: NavHostController) {
        navController = controller
    }

    fun unbind(controller: NavHostController) {
        if (navController === controller) {
            navController = null
        }
    }

    fun navigate(route: AppRoute, builder: NavOptionsBuilder.() -> Unit = {}) {
        navController?.navigate(route, builder)
    }

    /**
     * 현재 destination이 RESUMED일 때만 이동.
     * 화면 전환 중 연타로 서로 다른 destination이 연속 push되는 문제 방지(기존 navigateSafety 대응)
     */
    fun navigateSafely(route: AppRoute, builder: NavOptionsBuilder.() -> Unit = {}) {
        val controller = navController ?: return
        if (controller.currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) {
            return
        }
        controller.navigate(route, builder)
    }
}
