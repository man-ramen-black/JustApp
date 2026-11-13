package com.black.app.usagelog.collect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/** 시간 범위의 시스템 사용 기록 이벤트 조회 */
fun interface UsageEventSource {
    /**
     * [begin] 이상 [end] 미만의 이벤트를 시간 오름차순으로 조회
     *
     * 사용 정보 접근 권한이 없어 조회할 수 없으면 null.
     * 권한이 없을 때 시스템은 빈 이벤트를 돌려주므로 빈 목록과 구분해야 함.
     */
    fun query(begin: Long, end: Long): List<RawUsageEvent>?
}

/**
 * [UsageStatsManager] 기반 [UsageEventSource]
 *
 * 사용 정보 접근 권한([PermissionHelper.isUsageAccessGranted])이 없으면 null.
 */
class SystemUsageEventSource(private val context: Context) : UsageEventSource {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun query(begin: Long, end: Long): List<RawUsageEvent>? {
        if (!PermissionHelper.isUsageAccessGranted(context)) {
            Log.w("[SystemUsageEventSource] 사용 정보 접근 권한 없음")
            return null
        }
        val result = ArrayList<RawUsageEvent>()
        try {
            val events = usageStatsManager.queryEvents(begin, end)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val kind = when (event.eventType) {
                    ACTIVITY_RESUMED -> RawUsageEventKind.RESUMED
                    ACTIVITY_PAUSED -> RawUsageEventKind.PAUSED
                    SCREEN_INTERACTIVE -> RawUsageEventKind.SCREEN_ON
                    SCREEN_NON_INTERACTIVE -> RawUsageEventKind.SCREEN_OFF
                    KEYGUARD_HIDDEN -> RawUsageEventKind.UNLOCK
                    else -> null
                } ?: continue
                result.add(RawUsageEvent(kind, event.packageName, event.timeStamp))
            }
        } catch (e: SecurityException) {
            Log.w("[SystemUsageEventSource] 사용 기록 조회 거부")
            return null
        }
        return result
    }

    private companion object {
        // minSdk 24라 API 28 이상 상수는 값으로 비교
        const val ACTIVITY_RESUMED = 1
        const val ACTIVITY_PAUSED = 2
        const val SCREEN_INTERACTIVE = 15
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_HIDDEN = 18
    }
}
