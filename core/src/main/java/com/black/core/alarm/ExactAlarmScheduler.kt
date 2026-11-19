package com.black.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/**
 * 정확한 알람 예약과 취소
 *
 * 만들 때 정확한 알람 허용을 한 번 확인하고, 허용이 없거나 예약이 [SecurityException]으로 거부되면 그 뒤 예약은 모두 부정확한 알람으로 함.
 * 허용을 확인한 뒤 사용자가 허용을 끄면 정확한 알람 예약이 [SecurityException]을 던지기 때문.
 * 부정확한 알람(`setAndAllowWhileIdle`)은 기한보다 늦게 울릴 수 있으며 대체로 1시간 안이지만 Doze와 앱 대기 버킷 제한으로 더 늦을 수 있음.
 * 예약 묶음 하나(다시 예약 한 번)마다 새로 만들어 허용을 다시 확인하며, 알람은 모두 `RTC_WAKEUP`이고 기기 유휴 중에도 울림.
 * 취소는 허용 확인이 필요 없어 [cancel]을 companion에 둠.
 * [AlarmManager] 호출은 시스템 서버와의 Binder 왕복이 끝날 때까지 스레드를 막으므로 메인 스레드 밖에서 부름.
 */
class ExactAlarmScheduler(context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 다음 예약에서 정확한 알람을 시도할지 여부 */
    private var exact = PermissionHelper.canScheduleExactAlarms(context)

    /**
     * [triggerAtMillis](epoch 밀리초)에 [operation]을 보내는 알람 예약
     *
     * 부정확한 알람 예약이 던진 예외는 그대로 던지며, 그 뒤 예약도 정확한 알람을 다시 시도하지 않음.
     */
    fun schedule(triggerAtMillis: Long, operation: PendingIntent) {
        if (exact && setExact(triggerAtMillis, operation)) {
            return
        }
        // 대체 호출이 던져도 다음 예약이 정확한 알람을 다시 시도하지 않도록 부정확한 알람 전에 끔
        exact = false
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    }

    /**
     * 정확한 알람 하나를 예약하고 성공 여부 반환
     *
     * 허용이 거둬져 [SecurityException]이 나면 false를 돌려줘 그 예약부터 부정확한 알람으로 예약하게 함.
     */
    private fun setExact(triggerAtMillis: Long, operation: PendingIntent): Boolean = try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        true
    } catch (revoked: SecurityException) {
        Log.w("[ExactAlarmScheduler] 정확한 알람 거부, 부정확한 알람으로 예약")
        false
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
