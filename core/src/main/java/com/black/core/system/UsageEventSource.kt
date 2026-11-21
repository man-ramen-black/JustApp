package com.black.core.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/** 시간 범위의 시스템 사용 통계 이벤트 조회 */
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
 * 앱 화면 재개·일시 정지·정지(종료 포함), 화면 켜짐·꺼짐, 잠금 해제만 [RawUsageEvent]로 바꾸고 나머지 이벤트는 뺌.
 * 액티비티 클래스 이름은 앱 이벤트에만 담음. 정지·종료 이벤트는 API 29부터 남음.
 * `PACKAGE_USAGE_STATS` 권한은 core가 아닌 이 클래스를 쓰는 기능 모듈의 매니페스트에서 선언.
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
                    ACTIVITY_STOPPED, ACTIVITY_DESTROYED -> RawUsageEventKind.STOPPED
                    SCREEN_INTERACTIVE -> RawUsageEventKind.SCREEN_ON
                    SCREEN_NON_INTERACTIVE -> RawUsageEventKind.SCREEN_OFF
                    KEYGUARD_HIDDEN -> RawUsageEventKind.UNLOCK
                    else -> null
                } ?: continue
                // 클래스 이름은 앱 이벤트에만 실음
                val className = when (kind) {
                    RawUsageEventKind.RESUMED, RawUsageEventKind.PAUSED, RawUsageEventKind.STOPPED -> event.className
                    RawUsageEventKind.SCREEN_ON, RawUsageEventKind.SCREEN_OFF, RawUsageEventKind.UNLOCK -> null
                }
                result.add(RawUsageEvent(kind, event.packageName, event.timeStamp, className))
            }
        } catch (e: SecurityException) {
            Log.w("[SystemUsageEventSource] 사용 통계 조회 거부")
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
        const val ACTIVITY_STOPPED = 23

        // 공개 SDK에 없는 시스템 API 상수
        const val ACTIVITY_DESTROYED = 24
    }
}
