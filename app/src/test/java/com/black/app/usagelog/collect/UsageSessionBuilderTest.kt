package com.black.app.usagelog.collect

import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.ScreenOffRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.UnlockRecord
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageSessionBuilder]의 이벤트 → 앱 사용 구간·화면 기록 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageSessionBuilderTest : BaseTest() {

    private val appNameOf: (String) -> String = { "${it}앱" }

    private fun resumed(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.RESUMED, packageName, time)

    private fun paused(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.PAUSED, packageName, time)

    private fun screen(kind: RawUsageEventKind, time: Long) = RawUsageEvent(kind, null, time)

    private fun build(events: List<RawUsageEvent>, open: OpenUsageSession? = null) =
        UsageSessionBuilder.build(events, open, appNameOf)

    /**
     * 다른 앱으로 전환하면 앞 앱이 PAUSED 시각에 닫히는지 검증
     *
     * Given: a 실행, a 일시정지, b 실행
     * When: 변환
     * Then: a 구간 1000~2000, b 구간은 열림
     */
    @Test
    fun test_01_switchToOtherAppClosesAtPause() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), paused("a", 2000), resumed("b", 2100)))

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 2000)), result.records)
        assertEquals(OpenUsageSession("b", 2100, null), result.openSession)
    }

    /**
     * 같은 앱 안의 화면 전환이 한 구간으로 이어지는지 검증
     *
     * Given: a 실행, 일시정지, a 재실행, 일시정지, 화면 꺼짐
     * When: 변환
     * Then: a 구간 1000~3000 하나와 화면 꺼짐 기록, 열린 구간 없음
     */
    @Test
    fun test_02_activityChangeInsideSameAppKeepsOneSession() = runTest {
        /** Given / When **/
        val result = build(
            listOf(
                resumed("a", 1000), paused("a", 1500), resumed("a", 1600), paused("a", 3000),
                screen(RawUsageEventKind.SCREEN_OFF, 3100),
            ),
        )

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 3000), ScreenOffRecord(3100)), result.records)
        assertNull(result.openSession)
    }

    /**
     * PAUSED 없이 화면이 꺼지면 꺼진 시각에 닫히는지 검증
     *
     * Given: a 실행, 화면 꺼짐
     * When: 변환
     * Then: a 구간 1000~2000과 화면 꺼짐 기록
     */
    @Test
    fun test_03_screenOffWithoutPauseClosesAtScreenOff() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), screen(RawUsageEventKind.SCREEN_OFF, 2000)))

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 2000), ScreenOffRecord(2000)), result.records)
    }

    /**
     * 열린 구간이 다음 변환으로 이어지는지 검증
     *
     * Given: 첫 변환에서 a 실행만 있음
     * When: 그 열린 구간으로 a 일시정지, b 실행을 변환
     * Then: 첫 변환은 기록 없이 열린 구간, 둘째는 a 구간 1000~5000과 열린 b
     */
    @Test
    fun test_04_openSessionCarriesToNextBuild() = runTest {
        /** Given / When **/
        val first = build(listOf(resumed("a", 1000)))
        val second = build(listOf(paused("a", 5000), resumed("b", 5100)), first.openSession)

        /** Then **/
        assertEquals(emptyList<Any>(), first.records)
        assertEquals(OpenUsageSession("a", 1000, null), first.openSession)
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 5000)), second.records)
        assertEquals(OpenUsageSession("b", 5100, null), second.openSession)
    }

    /**
     * PAUSED 없이 다른 앱이 실행되면 실행 시각에 닫히는지 검증
     *
     * Given: a 실행, b 실행
     * When: 변환
     * Then: a 구간 1000~4000
     */
    @Test
    fun test_05_resumeOtherAppWithoutPauseClosesAtResume() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), resumed("b", 4000)))

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 4000)), result.records)
    }

    /**
     * 화면 켜짐·잠금 해제 기록이 순서대로 나오는지 검증
     *
     * Given: 화면 켜짐, 잠금 해제, a 실행
     * When: 변환
     * Then: 화면 켜짐, 잠금 해제 기록과 열린 a
     */
    @Test
    fun test_06_screenEventsKeepOrder() = runTest {
        /** Given / When **/
        val result = build(
            listOf(
                screen(RawUsageEventKind.SCREEN_ON, 100),
                screen(RawUsageEventKind.UNLOCK, 200),
                resumed("a", 300),
            ),
        )

        /** Then **/
        assertEquals(listOf(ScreenOnRecord(100), UnlockRecord(200)), result.records)
        assertEquals(OpenUsageSession("a", 300, null), result.openSession)
    }

    /**
     * 열린 구간이 아닌 패키지의 PAUSED가 무시되는지 검증
     *
     * Given: a 실행, b 일시정지, c 실행
     * When: 변환
     * Then: a 구간 1000~2000
     */
    @Test
    fun test_07_pauseOfOtherPackageIgnored() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), paused("b", 1500), resumed("c", 2000)))

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 2000)), result.records)
    }

    /**
     * 길이가 0인 구간이 버려지는지 검증
     *
     * Given: a와 b가 같은 시각에 실행
     * When: 변환
     * Then: 기록 없음
     */
    @Test
    fun test_08_zeroLengthSessionDropped() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), resumed("b", 1000)))

        /** Then **/
        assertEquals(emptyList<Any>(), result.records)
    }

    /**
     * 일시정지와 다른 앱 실행이 같은 시각이어도 구간이 닫히는지 검증
     *
     * Given: a 실행, a 일시정지 2000, b 실행 2000
     * When: 변환
     * Then: a 구간 1000~2000
     */
    @Test
    fun test_09_pauseAndResumeAtSameTime() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), paused("a", 2000), resumed("b", 2000)))

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 2000)), result.records)
    }

    /**
     * 사용으로 보지 않는 1초 미만 구간이 버려지는지 검증
     *
     * Given: a 999ms 실행, b 1001ms 실행, c 실행
     * When: 변환
     * Then: b 구간만 남음
     */
    @Test
    fun test_10_sessionShorterThanOneSecondDropped() = runTest {
        /** Given / When **/
        val result = build(
            listOf(
                resumed("a", 1000), paused("a", 1999),
                resumed("b", 1999), paused("b", 3000),
                resumed("c", 3000),
            ),
        )

        /** Then **/
        assertEquals(listOf(AppUsageRecord("b", "b앱", 1999, 3000)), result.records)
    }

    /**
     * 최소 길이와 정확히 같은 구간은 남고 1ms 짧은 구간은 버려지는지 검증
     *
     * Given: a 1000ms 실행, b 999ms 실행, c 실행
     * When: 변환
     * Then: a 구간 1000~2000만 남음
     */
    @Test
    fun test_11_sessionOfExactlyMinimumLengthKept() = runTest {
        /** Given / When **/
        val minimum = UsageSessionBuilder.MIN_SESSION_MILLIS
        val result = build(
            listOf(
                resumed("a", 1000), paused("a", 1000 + minimum),
                resumed("b", 1000 + minimum), paused("b", 1000 + 2 * minimum - 1),
                resumed("c", 1000 + 2 * minimum - 1),
            ),
        )

        /** Then **/
        assertEquals(listOf(AppUsageRecord("a", "a앱", 1000, 2000)), result.records)
    }

    /**
     * 다음 변환으로 넘어간 구간도 합친 길이가 1초 미만이면 버려지는지 검증
     *
     * Given: 첫 변환에서 a 실행 1000만 있음
     * When: 그 열린 구간으로 a 일시정지 1999, b 실행 2100을 변환
     * Then: 첫 변환은 열린 a, 둘째는 기록 없이 열린 b
     */
    @Test
    fun test_12_carriedSessionShorterThanOneSecondDropped() = runTest {
        /** Given / When **/
        val first = build(listOf(resumed("a", 1000)))
        val second = build(listOf(paused("a", 1999), resumed("b", 2100)), first.openSession)

        /** Then **/
        assertEquals(OpenUsageSession("a", 1000, null), first.openSession)
        assertEquals(emptyList<Any>(), second.records)
        assertEquals(OpenUsageSession("b", 2100, null), second.openSession)
    }

    /**
     * 화면 꺼짐으로 닫히는 1초 미만 구간은 버리고 화면 꺼짐 기록만 남는지 검증
     *
     * Given: a 실행 1000, 화면 꺼짐 1500
     * When: 변환
     * Then: 화면 꺼짐 기록 하나, 열린 구간 없음
     */
    @Test
    fun test_13_shortSessionClosedByScreenOffDropped() = runTest {
        /** Given / When **/
        val result = build(listOf(resumed("a", 1000), screen(RawUsageEventKind.SCREEN_OFF, 1500)))

        /** Then **/
        assertEquals(listOf(ScreenOffRecord(1500)), result.records)
        assertNull(result.openSession)
    }
}
