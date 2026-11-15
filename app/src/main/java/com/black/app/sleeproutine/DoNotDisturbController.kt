package com.black.app.sleeproutine

import android.app.NotificationManager
import android.content.Context
import com.black.core.util.PermissionHelper

/**
 * 방해 금지 켜기·끄기
 *
 * 켤 때는 빠른 설정의 방해 금지와 같은 우선순위만 허용(사용자 예외 설정 적용). 전체 차단(NONE)은 미디어까지 막아 YouTube가 들리지 않음
 */
class DoNotDisturbController(private val context: Context) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasAccess(): Boolean = PermissionHelper.isNotificationPolicyAccessGranted(context)

    /**
     * 우선순위만 허용으로 켜기
     *
     * 접근 권한이 없으면 false
     */
    fun enable(): Boolean {
        if (!hasAccess()) return false
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    /**
     * 모두 허용으로 끄기
     *
     * 접근 권한이 없으면 무시
     */
    fun disable() {
        if (!hasAccess()) return
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }
}
