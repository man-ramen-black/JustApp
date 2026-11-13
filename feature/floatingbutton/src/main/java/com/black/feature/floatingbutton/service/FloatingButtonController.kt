package com.black.feature.floatingbutton.service

import android.content.Context
import com.black.core.service.BaseForegroundService
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [FloatingForegroundService] 시작·중지와 플로팅 버튼 동작에 필요한 권한 상태 조회
 *
 * 설정 화면처럼 Hilt 주입을 받는 곳에서 사용하고, 바로가기는 서비스 인텐트를 직접 만들어 이 클래스를 거치지 않음
 */
class FloatingButtonController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    /** 플로팅 버튼 서비스 실행 여부 */
    val running: StateFlow<Boolean> = FloatingForegroundService.running

    /** 다른 앱 위에 표시 권한 허용 여부(없으면 버튼을 띄울 수 없음) */
    fun canDrawOverlays(): Boolean = PermissionHelper.canDrawOverlays(appContext)

    /** 뒤로 가기 버튼이 쓰는 [FloatingAccessibilityService] 사용 여부 */
    fun isAccessibilityServiceEnabled(): Boolean =
        PermissionHelper.isAccessibilityServiceEnabled(appContext, FloatingAccessibilityService::class.java)

    fun start() {
        BaseForegroundService.start(appContext, FloatingForegroundService::class.java)
    }

    /** 서비스 중지(stopService로 멈추면 onDestroy가 재시작하므로 중지 인텐트 사용) */
    fun stop() {
        BaseForegroundService.stop(appContext, FloatingForegroundService::class.java)
    }
}
