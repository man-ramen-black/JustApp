package com.black.core.system

/**
 * 시스템 사용 통계 이벤트를 시간 순서대로 받아 앱이 화면에 보이던 구간으로 계산
 *
 * 작은 창(PiP)으로 줄인 앱은 일시 정지(PAUSED)만 남기고 정지(STOPPED)가 올 때까지 화면에 떠 있어,
 * 포그라운드 구간([AppUsageIntervalTracker])으로는 잡히지 않는 이 시간을 세기 위한 계산.
 * 액티비티 인스턴스 ID가 공개 API가 아니라 액티비티는 패키지와 클래스 이름 한 쌍으로 구분함.
 * - RESUMED·PAUSED: 그 액티비티 보임
 * - STOPPED: 그 액티비티 안 보임
 * - SCREEN_OFF: 모든 액티비티 안 보임
 *
 * 패키지는 보이는 액티비티가 하나라도 있는 동안 보이는 것으로 보고, 0개에서 1개가 된 시각부터 1개에서 0개가 된 시각까지를
 * 구간으로 돌려줌. 같은 클래스 화면을 하나 더 열면 앞 화면의 STOPPED로 클래스 단위 보임이 끊겨 구간에 틈이 생기므로,
 * 호출자는 포그라운드 구간과 합쳐서 써야 함.
 * API 29 미만은 STOPPED 이벤트가 남지 않아 화면 꺼짐까지 보이는 것으로 계산됨.
 */
class AppVisibleIntervalTracker {

    /** 보이는 중인 패키지의 보이기 시작한 시각과 보이는 액티비티 클래스 이름 */
    private class VisiblePackage(val start: Long, val classNames: MutableSet<String?>)

    /** 보이는 중인 패키지, 보이기 시작한 순서 유지 */
    private val visiblePackages = LinkedHashMap<String, VisiblePackage>()

    /**
     * 이벤트 하나 반영
     *
     * @return 이 이벤트로 닫힌 패키지 보이는 구간, 없으면 빈 목록.
     */
    fun accept(event: RawUsageEvent): List<AppUsageInterval> {
        when (event.kind) {
            RawUsageEventKind.RESUMED, RawUsageEventKind.PAUSED -> {
                // 패키지 이름이 없는 이벤트 무시
                val packageName = event.packageName ?: return emptyList()
                val visible = visiblePackages.getOrPut(packageName) { VisiblePackage(event.time, HashSet()) }
                visible.classNames.add(event.className)
            }
            RawUsageEventKind.STOPPED -> {
                // 마지막으로 보이던 액티비티가 정지하면 패키지 구간을 닫음
                val packageName = event.packageName ?: return emptyList()
                val visible = visiblePackages[packageName] ?: return emptyList()
                visible.classNames.remove(event.className)
                if (visible.classNames.isEmpty()) {
                    visiblePackages.remove(packageName)
                    return listOf(AppUsageInterval(packageName, visible.start, event.time))
                }
            }
            RawUsageEventKind.SCREEN_OFF -> return closeAll(event.time)
            RawUsageEventKind.SCREEN_ON, RawUsageEventKind.UNLOCK -> Unit
        }
        return emptyList()
    }

    /**
     * 아직 보이는 패키지를 모두 [end]에 닫고 비움
     *
     * @return 닫힌 구간, 보이는 순서대로.
     */
    fun closeAll(end: Long): List<AppUsageInterval> {
        val closed = visiblePackages.map { (packageName, visible) -> AppUsageInterval(packageName, visible.start, end) }
        visiblePackages.clear()
        return closed
    }
}
