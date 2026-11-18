package com.black.app.assistanttask.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.black.app.util.ComponentExtensions.launchAsync

/**
 * 정확한 알람 허용 뒤 Todo 시각 알림 다시 예약
 *
 * 허용 전에 부정확한 알람으로 걸어 둔 것을 정확한 알람으로 바꿈.
 * 허용을 거두면 시스템이 앱을 멈추고 정확한 알람을 취소하며 이 방송은 보내지 않아, 그 경우는 다음 다시 예약이 부정확한 알람으로 다시 걺.
 */
class AssistantTaskExactAlarmPermissionReceiver : BroadcastReceiver() {

    // 액션 상수는 API 31에 추가됐지만 컴파일 때 문자열로 들어가고, 그 미만에서는 이 방송이 오지 않음
    @SuppressLint("InlinedApi")
    override fun onReceive(context: Context, intent: Intent) {
        // exported라 다른 앱의 명시적 브로드캐스트도 받으므로 액션 확인
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        // 저장본으로 다시 예약
        val alarms = createRescheduler(context.applicationContext)
        launchAsync { alarms.reschedule() }
    }

    companion object {
        /** 다시 예약기 생성 함수, 테스트에서 바꿔 넣음 */
        @VisibleForTesting
        internal var createRescheduler: (Context) -> AssistantTaskAlarmRescheduler = ::AssistantTaskAlarmScheduler
    }
}
