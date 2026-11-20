package com.black.feature.usagelog.data.usecase

import com.black.core.system.AppUsageIntervalTracker
import com.black.core.system.OpenUsageSession
import com.black.core.system.RawUsageEvent
import com.black.core.system.RawUsageEventKind
import com.black.feature.usagelog.model.AppUsageRecord
import com.black.feature.usagelog.model.ScreenOffRecord
import com.black.feature.usagelog.model.ScreenOnRecord
import com.black.feature.usagelog.model.UnlockRecord
import com.black.feature.usagelog.model.UsageLogRecord

/**
 * 변환 결과
 *
 * @property openSession 아직 닫히지 않아 다음 변환으로 넘길 구간.
 */
data class UsageSessionResult(val records: List<UsageLogRecord>, val openSession: OpenUsageSession?)

/** 시스템 사용 기록 이벤트를 앱 사용 구간과 화면 기록으로 변환 */
object UsageSessionBuilder {

    /**
     * 기록으로 남기는 앱 사용 구간의 최소 길이
     *
     * 최근 앱 화면·자동화 앱의 중간 화면처럼 다른 앱으로 넘어가는 길에 잠깐 뜨는 화면이 1초 미만 구간을 대량으로 만들어,
     * 사용으로 볼 수 없는 이 구간은 기록하지 않음.
     */
    const val MIN_SESSION_MILLIS = 1000L

    /**
     * 이벤트 변환
     *
     * [events]는 시간 오름차순이며 [openSession]은 직전 변환이 넘긴 구간.
     * [MIN_SESSION_MILLIS]보다 짧은 구간은 버리고 기록은 time 순서로 돌려줌.
     */
    fun build(
        events: List<RawUsageEvent>,
        openSession: OpenUsageSession?,
        appNameOf: (String) -> String,
    ): UsageSessionResult {
        val records = ArrayList<UsageLogRecord>()
        val tracker = AppUsageIntervalTracker(openSession)

        events.forEach { event ->
            // 이벤트로 닫힌 구간을 최소 길이 이상일 때만 기록으로 변환
            val closed = tracker.accept(event)
            if (closed != null && closed.end - closed.start >= MIN_SESSION_MILLIS) {
                records.add(AppUsageRecord(closed.packageName, appNameOf(closed.packageName), closed.start, closed.end))
            }
            // 같은 이벤트의 화면 기록은 닫힌 구간 뒤에 추가
            when (event.kind) {
                RawUsageEventKind.SCREEN_OFF -> records.add(ScreenOffRecord(event.time))
                RawUsageEventKind.SCREEN_ON -> records.add(ScreenOnRecord(event.time))
                RawUsageEventKind.UNLOCK -> records.add(UnlockRecord(event.time))
                RawUsageEventKind.RESUMED, RawUsageEventKind.PAUSED -> Unit
            }
        }
        return UsageSessionResult(records.sortedBy { it.time }, tracker.openSession)
    }
}
