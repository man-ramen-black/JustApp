package com.black.core.system

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [AppVisibleIntervalTracker]의 액티비티별 보임 상태와 패키지 보이는 구간 계산 검증(home은 런처 패키지 역할) */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppVisibleIntervalTrackerTest : BaseTest() {

    private fun resumed(packageName: String, className: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.RESUMED, packageName, time, className)

    private fun paused(packageName: String, className: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.PAUSED, packageName, time, className)

    private fun stopped(packageName: String, className: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.STOPPED, packageName, time, className)

    private fun screen(kind: RawUsageEventKind, time: Long) = RawUsageEvent(kind, null, time)

    /** [events]를 차례대로 넣어 각 이벤트로 닫힌 구간을 순서대로 모음 */
    private fun AppVisibleIntervalTracker.acceptAll(events: List<RawUsageEvent>): List<AppUsageInterval> =
        events.flatMap { accept(it) }

    /**
     * 작은 창으로 줄인 앱이 정지 전까지 보이는 것으로 남는지 검증
     *
     * Given: a 재개 1000, a 일시 정지 2000, 홈 재개 2000, a 정지 9000
     * When: 이벤트 반영
     * Then: 닫힌 구간은 a 1000~9000 하나
     */
    @Test
    fun test_01_pipKeepsVisibleUntilStopped() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", "A", 1000), paused("a", "A", 2000), resumed("home", "H", 2000), stopped("a", "A", 9000),
        )

        /** When **/
        val closed = AppVisibleIntervalTracker().acceptAll(events)

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 9000)), closed)
    }

    /**
     * 분할 화면에서 두 앱이 함께 보이는 구간을 각각 만드는지 검증
     *
     * Given: a 재개 1000, a 일시 정지 2000, b 재개 2000, a 정지 5000, b 일시 정지 6000, b 정지 7000
     * When: 이벤트 반영
     * Then: a 1000~5000, b 2000~7000
     */
    @Test
    fun test_02_splitScreenBothVisible() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", "A", 1000), paused("a", "A", 2000), resumed("b", "B", 2000),
            stopped("a", "A", 5000), paused("b", "B", 6000), stopped("b", "B", 7000),
        )

        /** When **/
        val closed = AppVisibleIntervalTracker().acceptAll(events)

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 5000), AppUsageInterval("b", 2000, 7000)), closed)
    }

    /**
     * 화면 꺼짐이 보이는 앱을 모두 닫고 그 뒤 정지는 아무것도 닫지 않는지 검증
     *
     * Given: a 1000부터, b 2000부터 보이는 중 화면 꺼짐 5000, 이어서 a 정지 5500, b 정지 5600
     * When: 이벤트 반영
     * Then: 화면 꺼짐에서 a 1000~5000, b 2000~5000이 닫히고 정지 이벤트는 빈 목록
     */
    @Test
    fun test_03_screenOffClosesAll() = runTest {
        /** Given **/
        val tracker = AppVisibleIntervalTracker()
        tracker.acceptAll(listOf(resumed("a", "A", 1000), paused("a", "A", 2000), resumed("b", "B", 2000)))

        /** When **/
        val closedByScreenOff = tracker.accept(screen(RawUsageEventKind.SCREEN_OFF, 5000))
        val closedByStop = tracker.acceptAll(listOf(stopped("a", "A", 5500), stopped("b", "B", 5600)))

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 5000), AppUsageInterval("b", 2000, 5000)), closedByScreenOff)
        assertEquals(emptyList<AppUsageInterval>(), closedByStop)
    }

    /**
     * 같은 앱의 액티비티가 하나라도 보이는 동안 패키지가 계속 보이는지 검증
     *
     * Given: a/A1 재개 1000, a/A1 일시 정지 2000, a/A2 재개 2000, a/A1 정지 3000, a/A2 일시 정지 4000, a/A2 정지 5000
     * When: 이벤트 반영
     * Then: a 1000~5000 하나
     */
    @Test
    fun test_04_packageVisibleWhileAnyActivityVisible() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", "A1", 1000), paused("a", "A1", 2000), resumed("a", "A2", 2000),
            stopped("a", "A1", 3000), paused("a", "A2", 4000), stopped("a", "A2", 5000),
        )

        /** When **/
        val closed = AppVisibleIntervalTracker().acceptAll(events)

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 5000)), closed)
    }

    /**
     * 끝까지 보이는 앱을 closeAll 시각에 닫는지 검증
     *
     * Given: a 일시 정지 1000 뒤 이벤트 없음
     * When: closeAll(8000)
     * Then: a 1000~8000
     */
    @Test
    fun test_05_closeAllClosesVisibleAtEnd() = runTest {
        /** Given **/
        val tracker = AppVisibleIntervalTracker()
        tracker.accept(paused("a", "A", 1000))

        /** When **/
        val closed = tracker.closeAll(8000)

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 8000)), closed)
    }

    /**
     * 화면을 껐다 켜고 잠금 해제한 뒤 일시 정지가 다시 오면 보이는 구간을 다시 여는지 검증(태블릿 기기 기록 순서)
     *
     * Given: a 일시 정지 1000, 화면 꺼짐 2000, a 정지 2001, 화면 켜짐 3000, 잠금 해제 3500, a 일시 정지 3500
     * When: 이벤트 반영 후 closeAll(6000)
     * Then: a 1000~2000, 3500~6000
     */
    @Test
    fun test_06_pausedAfterUnlockReopens() = runTest {
        /** Given **/
        val tracker = AppVisibleIntervalTracker()
        val events = listOf(
            paused("a", "A", 1000), screen(RawUsageEventKind.SCREEN_OFF, 2000), stopped("a", "A", 2001),
            screen(RawUsageEventKind.SCREEN_ON, 3000), screen(RawUsageEventKind.UNLOCK, 3500), paused("a", "A", 3500),
        )

        /** When **/
        val closed = tracker.acceptAll(events) + tracker.closeAll(6000)

        /** Then **/
        assertEquals(listOf(AppUsageInterval("a", 1000, 2000), AppUsageInterval("a", 3500, 6000)), closed)
    }
}
