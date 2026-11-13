package com.black.app.ui.navigation

import androidx.navigation.NavOptionsBuilder

/**
 * 앱 화면 이동 기본 옵션 적용
 *
 * 이동할 화면이 이미 맨 위면 다시 쌓지 않고, 그 화면의 저장된 상태가 있으면 복원.
 * 딥링크 이동과 메인 목록 이동이 같은 옵션을 쓰도록 한곳에 둠.
 */
fun NavOptionsBuilder.applyDefaultNavOptions() {
    launchSingleTop = true
    restoreState = true
}
