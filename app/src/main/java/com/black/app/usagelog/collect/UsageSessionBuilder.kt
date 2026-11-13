package com.black.app.usagelog.collect

import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.ScreenOffRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.UnlockRecord
import com.black.app.usagelog.record.UsageLogRecord

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
        var open = openSession

        // 열린 구간을 pausedAt 또는 fallbackEnd에 닫아 기록으로 변환
        fun close(session: OpenUsageSession, fallbackEnd: Long) {
            val end = session.pausedAt ?: fallbackEnd
            if (end - session.start >= MIN_SESSION_MILLIS) {
                records.add(AppUsageRecord(session.packageName, appNameOf(session.packageName), session.start, end))
            }
        }

        events.forEach { event ->
            val current = open
            val packageName = event.packageName
            when (event.kind) {
                RawUsageEventKind.RESUMED -> {
                    if (packageName == null) return@forEach
                    if (current == null) {
                        open = OpenUsageSession(packageName, event.time)
                    } else if (current.packageName == packageName) {
                        // 같은 앱 안의 화면 전환은 구간을 이어 감
                        open = current.copy(pausedAt = null)
                    } else {
                        close(current, event.time)
                        open = OpenUsageSession(packageName, event.time)
                    }
                }
                RawUsageEventKind.PAUSED -> {
                    if (current != null && current.packageName == packageName) {
                        open = current.copy(pausedAt = event.time)
                    }
                }
                RawUsageEventKind.SCREEN_OFF -> {
                    if (current != null) close(current, event.time)
                    open = null
                    records.add(ScreenOffRecord(event.time))
                }
                RawUsageEventKind.SCREEN_ON -> records.add(ScreenOnRecord(event.time))
                RawUsageEventKind.UNLOCK -> records.add(UnlockRecord(event.time))
            }
        }
        return UsageSessionResult(records.sortedBy { it.time }, open)
    }
}
