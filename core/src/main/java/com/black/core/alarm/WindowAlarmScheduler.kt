package com.black.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context

/**
 * 정확한 알람 권한 없이 쓰는 창 알람 예약과 취소
 *
 * `setWindow`로 [triggerAtMillis]부터 `windowMillis` 안에 울리게 하며 시스템이 창 안에서 시각을 조정함.
 * 알람 종류는 `RTC`(기기를 깨우지 않음)라서 유휴 상태에서는 화면이 켜질 때까지 미뤄지고, 정확한 알람 권한(`SCHEDULE_EXACT_ALARM`)이 필요 없음.
 * [AlarmManager] 호출은 시스템 서버와의 Binder 왕복이 끝날 때까지 스레드를 막지만 짧은 호출 한 번이라 메인 스레드에서 불러도 됨.
 * 한꺼번에 여러 번 부르거나 반복문 안에서 부를 때는 메인 스레드 밖에서 부름.
 */
class WindowAlarmScheduler(context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** [triggerAtMillis](epoch 밀리초)부터 [windowMillis] 안에 [operation]을 보내는 알람 예약 */
    fun schedule(triggerAtMillis: Long, windowMillis: Long, operation: PendingIntent) {
        alarmManager.setWindow(AlarmManager.RTC, triggerAtMillis, windowMillis, operation)
    }

    companion object {
        /** [operation]으로 건 알람과 그 PendingIntent 취소 */
        fun cancel(context: Context, operation: PendingIntent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }
}
