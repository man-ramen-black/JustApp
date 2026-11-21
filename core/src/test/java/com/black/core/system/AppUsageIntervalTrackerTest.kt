package com.black.core.system

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [AppUsageIntervalTracker]의 이벤트 → 앱 사용 구간 계산 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUsageIntervalTrackerTest : BaseTest() {

    private fun resumed(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.RESUMED, packageName, time)

    private fun paused(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.PAUSED, packageName, time)

    private fun screen(kind: RawUsageEventKind, time: Long) = RawUsageEvent(kind, null, time)

    /**
     * 다른 앱으로 전환하면 앞 앱이 PAUSED 시각에 닫히는지 검증
     *
     * Given: a 실행, a 일시정지
     * When: b 실행
     * Then: a 구간 1000~2000 반환, 열린 구간은 b
     */
    @Test
    fun test_01_switchToOtherAppClosesAtPause() = runTest {
        /** Given **/
        val tracker = AppUsageIntervalTracker()
        assertNull(tracker.accept(resumed("a", 1000)))
        assertNull(tracker.accept(paused("a", 2000)))

        /** When **/
        val closed = tracker.accept(resumed("b", 2100))

        /** Then **/
        assertEquals(AppUsageInterval("a", 1000, 2000), closed)
        assertEquals(OpenUsageSession("b", 2100), tracker.openSession)
    }

    /**
     * 같은 앱 재실행이 구간을 이어 가는지 검증
     *
     * Given: a 실행, a 일시정지
     * When: a 재실행
     * Then: 모두 null, 열린 구간 a 1000 유지
     */
    @Test
    fun test_02_resumeSameAppKeepsSession() = runTest {
        /** Given / When **/
        val tracker = AppUsageIntervalTracker()
        val results = listOf(resumed("a", 1000), paused("a", 1500), resumed("a", 1600)).map { tracker.accept(it) }

        /** Then **/
        assertEquals(listOf(null, null, null), results)
        assertEquals(OpenUsageSession("a", 1000, null), tracker.openSession)
    }

    /**
     * 화면 꺼짐이 구간을 닫는지 검증
     *
     * Given: a 실행
     * When: SCREEN_OFF 2000
     * Then: a 구간 1000~2000, 열린 구간 없음
     */
    @Test
    fun test_03_screenOffClosesAtScreenOff() = runTest {
        /** Given **/
        val tracker = AppUsageIntervalTracker()
        tracker.accept(resumed("a", 1000))

        /** When **/
        val closed = tracker.accept(screen(RawUsageEventKind.SCREEN_OFF, 2000))

        /** Then **/
        assertEquals(AppUsageInterval("a", 1000, 2000), closed)
        assertNull(tracker.openSession)
    }

    /**
     * closeOpenSession이 pausedAt 또는 인자 시각에 닫는지 검증
     *
     * Given: pausedAt이 있는 구간과 없는 구간
     * When: closeOpenSession(5000)
     * Then: 각각 3000, 5000에 닫히고 이후 열린 구간 없음, 다시 부르면 null
     */
    @Test
    fun test_04_closeOpenSessionUsesPausedAtOrEnd() = runTest {
        /** Given **/
        val pausedTracker = AppUsageIntervalTracker()
        pausedTracker.accept(resumed("a", 1000))
        pausedTracker.accept(paused("a", 3000))
        val runningTracker = AppUsageIntervalTracker()
        runningTracker.accept(resumed("a", 1000))

        /** When / Then **/
        assertEquals(AppUsageInterval("a", 1000, 3000), pausedTracker.closeOpenSession(5000))
        assertEquals(AppUsageInterval("a", 1000, 5000), runningTracker.closeOpenSession(5000))
        assertNull(runningTracker.openSession)
        assertNull(runningTracker.closeOpenSession(6000))
    }

    /**
     * 넘겨받은 열린 구간에서 이어 계산하는지 검증
     *
     * Given: a 1000에 열린 구간을 가진 tracker
     * When: a 일시정지 5000, b 실행 5100
     * Then: a 구간 1000~5000
     */
    @Test
    fun test_05_carriedOpenSessionContinues() = runTest {
        /** Given **/
        val tracker = AppUsageIntervalTracker(OpenUsageSession("a", 1000))

        /** When **/
        assertNull(tracker.accept(paused("a", 5000)))
        val closed = tracker.accept(resumed("b", 5100))

        /** Then **/
        assertEquals(AppUsageInterval("a", 1000, 5000), closed)
    }

    /**
     * 길이 0 구간도 그대로 돌려주는지 검증
     *
     * Given: a 1000 실행
     * When: b 1000 실행
     * Then: a 구간 1000~1000 반환
     */
    @Test
    fun test_06_zeroLengthIntervalReturned() = runTest {
        /** Given **/
        val tracker = AppUsageIntervalTracker()
        tracker.accept(resumed("a", 1000))

        /** When **/
        val closed = tracker.accept(resumed("b", 1000))

        /** Then **/
        assertEquals(AppUsageInterval("a", 1000, 1000), closed)
    }

    /**
     * 화면 켜짐·잠금 해제가 구간에 영향 없는지 검증
     *
     * Given: a 실행
     * When: SCREEN_ON, UNLOCK
     * Then: 모두 null, 열린 구간 유지
     */
    @Test
    fun test_07_screenOnAndUnlockIgnored() = runTest {
        /** Given **/
        val tracker = AppUsageIntervalTracker()
        tracker.accept(resumed("a", 1000))

        /** When **/
        val results = listOf(
            tracker.accept(screen(RawUsageEventKind.SCREEN_ON, 1500)),
            tracker.accept(screen(RawUsageEventKind.UNLOCK, 1600)),
        )

        /** Then **/
        assertEquals(listOf(null, null), results)
        assertEquals(OpenUsageSession("a", 1000), tracker.openSession)
    }
}
