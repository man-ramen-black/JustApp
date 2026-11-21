package com.black.core.system

/** 사용 이벤트를 기간 안 앱별 사용 구간으로 계산 */
object AppUsageTimeCalculator {

    /**
     * 결과에 남기는 사용 구간의 최소 길이
     *
     * 최근 앱 화면·자동화 앱의 중간 화면처럼 다른 앱으로 넘어가는 길에 잠깐 뜨는 화면이 1초 미만 구간을 대량으로 만들어,
     * 사용으로 볼 수 없는 이 구간은 셈에서 뺌.
     */
    const val MIN_SESSION_MILLIS = 1000L

    /**
     * [begin]~[end] 안 [packageNames] 앱별 사용 구간(시작 오름차순)
     *
     * 앞 구간 끝과 다음 구간 시작의 차이가 [mergeGapMillis] 이하이고 그 사이(양 끝 포함)에 화면 꺼짐이 없으면
     * 같은 앱의 두 구간을 이탈 시간까지 포함해 하나로 합침. [mergeGapMillis]가 0이면 합치지 않음.
     * [events]는 [begin]보다 앞서 시작한 구간을 경계부터 세도록 기간보다 앞에서 조회한 이벤트여도 됨.
     * 합친 구간을 기간으로 자른 뒤 [MIN_SESSION_MILLIS]보다 짧은 구간은 뺌.
     * 요청한 앱은 사용 기록이 없어도 빈 목록으로 결과에 포함.
     *
     * [includeVisible]이면 포그라운드 구간에 [AppVisibleIntervalTracker]의 보이는 구간을 더해, 작은 창·분할 화면처럼
     * 앞에 있지 않아도 화면에 떠 있던 시간도 셈. 두 구간을 합집합으로 합치는 이유는 같은 클래스 화면을 하나 더 열면
     * 앞 화면의 정지로 보이는 구간에 틈이 생기고, 그 틈은 포그라운드 구간이 덮기 때문.
     * 겹치거나 맞닿은 구간을 먼저 하나로 합친 뒤 짧은 이탈 합치기와 기간 자르기를 적용함.
     */
    fun usageSessions(
        events: List<RawUsageEvent>,
        begin: Long,
        end: Long,
        packageNames: Collection<String>,
        mergeGapMillis: Long,
        includeVisible: Boolean = false,
    ): Map<String, List<AppUsageSession>> {
        // 이벤트로 포그라운드 구간, 보이는 구간, 화면 꺼짐 시각 수집
        val tracker = AppUsageIntervalTracker()
        val visibleTracker = if (includeVisible) AppVisibleIntervalTracker() else null
        val intervals = ArrayList<AppUsageInterval>()
        val screenOffTimes = ArrayList<Long>()
        for (event in events) {
            if (event.kind == RawUsageEventKind.SCREEN_OFF) {
                screenOffTimes.add(event.time)
            }
            tracker.accept(event)?.let { intervals.add(it) }
            visibleTracker?.let { intervals.addAll(it.accept(event)) }
        }
        tracker.closeOpenSession(end)?.let { intervals.add(it) }
        visibleTracker?.let { intervals.addAll(it.closeAll(end)) }

        // 요청한 앱마다 겹친 구간과 짧은 이탈을 합치고 기간으로 잘라 구간으로 변환
        val result = LinkedHashMap<String, List<AppUsageSession>>()
        for (packageName in packageNames) {
            val merged = mergeIntervals(
                unionIntervals(intervals.filter { it.packageName == packageName }.sortedBy { it.start }),
                screenOffTimes,
                mergeGapMillis,
            )
            result[packageName] = merged.mapNotNull { it.toSession(begin, end) }
        }
        return result
    }

    /**
     * 합친 구간을 [periodBegin]~[periodEnd]로 잘라 구간 한 건으로 변환, [MIN_SESSION_MILLIS]보다 짧으면 null
     *
     * 조회 끝에 열려 있던 구간은 트래커가 [periodEnd]에 닫으므로 끝이 [periodEnd] 이상이면 진행 중으로 표시.
     */
    private fun AppUsageInterval.toSession(periodBegin: Long, periodEnd: Long): AppUsageSession? {
        val clippedStart = maxOf(periodBegin, start)
        val clippedEnd = minOf(periodEnd, end)
        if (clippedEnd - clippedStart < MIN_SESSION_MILLIS) {
            return null
        }
        return AppUsageSession(
            start = clippedStart,
            end = clippedEnd,
            startsBeforePeriod = start < periodBegin,
            inProgress = end >= periodEnd,
        )
    }

    /**
     * 시작 순으로 정렬된 같은 앱 구간 중 겹치거나 맞닿은 구간을 하나로 합침
     *
     * 포그라운드 구간끼리는 겹치지 않아 보이는 구간을 넣지 않으면 결과가 바뀌지 않음.
     */
    private fun unionIntervals(sorted: List<AppUsageInterval>): List<AppUsageInterval> {
        val union = ArrayList<AppUsageInterval>()
        for (interval in sorted) {
            val previous = union.lastOrNull()
            if (previous != null && interval.start <= previous.end) {
                union[union.lastIndex] = previous.copy(end = maxOf(previous.end, interval.end))
            } else {
                union.add(interval)
            }
        }
        return union
    }

    /** 시작 순으로 정렬된 같은 앱 구간을 짧은 이탈 기준으로 합침 */
    private fun mergeIntervals(
        sorted: List<AppUsageInterval>,
        screenOffTimes: List<Long>,
        mergeGapMillis: Long,
    ): List<AppUsageInterval> {
        val merged = ArrayList<AppUsageInterval>()
        for (interval in sorted) {
            val previous = merged.lastOrNull()
            if (previous != null &&
                mergeGapMillis > 0 &&
                interval.start - previous.end <= mergeGapMillis &&
                screenOffTimes.none { it >= previous.end && it <= interval.start }
            ) {
                merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, interval.end))
            } else {
                merged.add(interval)
            }
        }
        return merged
    }
}
