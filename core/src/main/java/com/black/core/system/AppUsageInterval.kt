package com.black.core.system

import kotlinx.serialization.Serializable

/** 앱 하나를 쓴 구간(밀리초 시각) */
data class AppUsageInterval(val packageName: String, val start: Long, val end: Long)

/**
 * 기간 안으로 자른 앱 사용 구간 한 건(밀리초 시각)
 *
 * @property startsBeforePeriod 실제 시작이 기간 시작보다 앞이라 [start]가 기간 시작 시각인지 여부
 * @property inProgress 조회 끝까지 열려 있어 [end]가 조회 시각인지 여부
 */
data class AppUsageSession(
    val start: Long,
    val end: Long,
    val startsBeforePeriod: Boolean = false,
    val inProgress: Boolean = false,
)

/**
 * 최근 기간 앱별 사용 구간 조회 결과
 *
 * @property queriedAtMillis 조회 끝 시각
 */
data class RecentAppUsage(
    val queriedAtMillis: Long,
    val sessions: Map<String, List<AppUsageSession>>,
)

/**
 * 아직 끝나지 않은 앱 사용 구간
 *
 * DataStore에 JSON 문자열로 저장됨. pausedAt은 이 앱의 마지막 ACTIVITY_PAUSED 시각으로, 같은 앱이 다시 실행되면 지워지고
 * 다른 앱 실행이나 화면 꺼짐으로 구간을 닫을 때 끝 시각이 됨.
 */
@Serializable
data class OpenUsageSession(val packageName: String, val start: Long, val pausedAt: Long? = null)

/**
 * 시스템 사용 통계 이벤트를 시간 순서대로 받아 앱 사용 구간으로 계산
 *
 * [openSession]은 직전 계산이 넘긴 열린 구간. 최소 길이 필터는 적용하지 않으므로 길이 0 구간도 그대로 돌려주며
 * 거르는 일은 호출자 책임.
 */
class AppUsageIntervalTracker(openSession: OpenUsageSession? = null) {

    /** 아직 닫히지 않은 구간 */
    var openSession: OpenUsageSession? = openSession
        private set

    /**
     * 이벤트 하나 반영
     *
     * @return 이 이벤트로 닫힌 구간, 없으면 null.
     */
    fun accept(event: RawUsageEvent): AppUsageInterval? {
        val current = openSession
        val packageName = event.packageName
        when (event.kind) {
            RawUsageEventKind.RESUMED -> {
                // 패키지 이름이 없는 이벤트 무시
                if (packageName == null) {
                    return null
                }
                if (current == null) {
                    openSession = OpenUsageSession(packageName, event.time)
                } else if (current.packageName == packageName) {
                    // 같은 앱 안의 화면 전환은 구간을 이어 감
                    openSession = current.copy(pausedAt = null)
                } else {
                    openSession = OpenUsageSession(packageName, event.time)
                    return current.toInterval(event.time)
                }
            }
            RawUsageEventKind.PAUSED -> {
                if (current != null && current.packageName == packageName) {
                    openSession = current.copy(pausedAt = event.time)
                }
            }
            RawUsageEventKind.SCREEN_OFF -> {
                openSession = null
                return current?.toInterval(event.time)
            }
            // 정지는 보이는 구간 계산용이라 포그라운드 구간에 영향 없음
            RawUsageEventKind.STOPPED, RawUsageEventKind.SCREEN_ON, RawUsageEventKind.UNLOCK -> Unit
        }
        return null
    }

    /**
     * 열린 구간을 pausedAt 또는 [end]에 닫고 비움
     *
     * @return 닫힌 구간, 열린 구간이 없으면 null.
     */
    fun closeOpenSession(end: Long): AppUsageInterval? {
        val current = openSession ?: return null
        openSession = null
        return current.toInterval(end)
    }

    /** 끝 시각을 pausedAt 우선으로 정해 구간으로 변환 */
    private fun OpenUsageSession.toInterval(fallbackEnd: Long) =
        AppUsageInterval(packageName, start, pausedAt ?: fallbackEnd)
}
