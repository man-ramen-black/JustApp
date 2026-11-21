package com.black.core.system

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [AppUsageTimeCalculator]의 기간 자르기, 짧은 이탈 합치기, 보이는 구간 포함 검증(home은 런처 패키지 역할) */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUsageTimeCalculatorTest : BaseTest() {

    private fun resumed(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.RESUMED, packageName, time)

    private fun paused(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.PAUSED, packageName, time)

    private fun stopped(packageName: String, time: Long) =
        RawUsageEvent(RawUsageEventKind.STOPPED, packageName, time)

    private fun screen(kind: RawUsageEventKind, time: Long) = RawUsageEvent(kind, null, time)

    /** a가 홈을 거쳐 1000~2000, 5000~6000 사용되는 이벤트 */
    private fun gapEvents() = listOf(
        resumed("a", 1000), paused("a", 2000), resumed("home", 2000), paused("home", 5000),
        resumed("a", 5000), paused("a", 6000), resumed("home", 6000),
    )

    /**
     * 요청한 앱만 합산하는지 검증
     *
     * Given: a, b, a 순서로 사용
     * When: [a] 합산
     * Then: a 2000만 반환
     */
    @Test
    fun test_01_sumsOnlyRequestedPackage() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000), resumed("b", 2000), paused("b", 5000),
            resumed("a", 5000), paused("a", 6000), resumed("home", 6000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 0)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 기간 시작 앞부분을 잘라내는지 검증
     *
     * Given: a 1000~5000
     * When: begin 3000
     * Then: a 2000
     */
    @Test
    fun test_02_clipsAtBegin() = runTest {
        /** Given **/
        val events = listOf(resumed("a", 1000), paused("a", 5000), resumed("home", 5000))

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 3000, 10_000, listOf("a"), 0)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 열린 구간을 pausedAt 또는 end에 닫는지 검증
     *
     * Given: a가 pausedAt 3000으로 열림, a가 pausedAt 없이 열림
     * When: end 10000, end 4000
     * Then: 2000, 3000
     */
    @Test
    fun test_03_closesOpenSessionAtPausedAtOrEnd() = runTest {
        /** Given / When **/
        val withPause = AppUsageTimeCalculator.sumUsageMillis(
            listOf(resumed("a", 1000), paused("a", 3000)), 0, 10_000, listOf("a"), 0,
        )
        val withoutPause = AppUsageTimeCalculator.sumUsageMillis(listOf(resumed("a", 1000)), 0, 4000, listOf("a"), 0)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), withPause)
        assertEquals(mapOf("a" to 3000L), withoutPause)
    }

    /**
     * 이벤트 없는 앱이 0으로 들어가는지 검증
     *
     * Given: c 이벤트 없음
     * When: [a, c] 합산
     * Then: c 0
     */
    @Test
    fun test_04_unusedPackageIsZero() = runTest {
        /** Given **/
        val events = listOf(resumed("a", 1000), paused("a", 2000), resumed("home", 2000))

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a", "c"), 0)

        /** Then **/
        assertEquals(mapOf("a" to 1000L, "c" to 0L), result)
    }

    /**
     * 간격 이하의 이탈을 간격까지 포함해 합치는지 검증
     *
     * Given: a 1000~2000, 홈 3000ms, a 5000~6000
     * When: gap 10000
     * Then: 1000~6000 합쳐 5000
     */
    @Test
    fun test_05_mergesShortGapIncludingGap() = runTest {
        /** Given / When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(gapEvents(), 0, 10_000, listOf("a"), 10_000)

        /** Then **/
        assertEquals(mapOf("a" to 5000L), result)
    }

    /**
     * 간격보다 긴 이탈은 합치지 않는지 검증
     *
     * Given: test_05 이벤트
     * When: gap 2000
     * Then: 2000
     */
    @Test
    fun test_06_gapOverThresholdNotMerged() = runTest {
        /** Given / When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(gapEvents(), 0, 10_000, listOf("a"), 2000)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 사이에 화면 꺼짐이 있으면 합치지 않는지 검증
     *
     * Given: a 1000~2000, SCREEN_OFF 2500, a 4000~5000
     * When: gap 10000
     * Then: 2000
     */
    @Test
    fun test_07_screenOffInGapNotMerged() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000),
            screen(RawUsageEventKind.SCREEN_OFF, 2500), screen(RawUsageEventKind.SCREEN_ON, 3000),
            resumed("a", 4000), paused("a", 5000), resumed("home", 5000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 10_000)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 간격 0이면 합치지 않는지 검증
     *
     * Given: test_05 이벤트
     * When: gap 0
     * Then: 2000
     */
    @Test
    fun test_08_zeroGapDoesNotMerge() = runTest {
        /** Given / When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(gapEvents(), 0, 10_000, listOf("a"), 0)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 합친 구간을 기간 시작에서 자르는지 검증
     *
     * Given: a 1000~2000, 홈 2000~3000, a 3000~4000
     * When: begin 2500, gap 10000
     * Then: 합친 1000~4000 중 2500 이후 1500
     */
    @Test
    fun test_09_mergedIntervalClippedAtBegin() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000), resumed("home", 2000),
            resumed("a", 3000), paused("a", 4000), resumed("home", 4000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 2500, 10_000, listOf("a"), 10_000)

        /** Then **/
        assertEquals(mapOf("a" to 1500L), result)
    }

    /**
     * 화면 꺼짐이 일시 정지보다 먼저 기록돼도 합치지 않는지 검증
     *
     * Given: a 1000~2000(화면 꺼짐 2000, 일시 정지 2050), 화면 켜짐 3000, a 4000~5000
     * When: gap 10000
     * Then: 구간이 화면 꺼짐 시각 2000에 닫혀 합치지 않으므로 a 2000
     */
    @Test
    fun test_10_screenOffBeforePauseNotMerged() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), screen(RawUsageEventKind.SCREEN_OFF, 2000), paused("a", 2050),
            screen(RawUsageEventKind.SCREEN_ON, 3000), resumed("a", 4000), paused("a", 5000),
            resumed("home", 5000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 10_000)

        /** Then **/
        assertEquals(mapOf("a" to 2000L), result)
    }

    /**
     * 간격이 합침 기준과 정확히 같으면 합치는지 검증
     *
     * Given: a 1000~2000, 홈 2000~12000, a 12000~13000
     * When: gap 10000
     * Then: 이탈 시간을 포함해 합쳐 a 12000
     */
    @Test
    fun test_11_gapEqualToThresholdMerged() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000), resumed("home", 2000),
            resumed("a", 12_000), paused("a", 13_000), resumed("home", 13_000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 20_000, listOf("a"), 10_000)

        /** Then **/
        assertEquals(mapOf("a" to 12_000L), result)
    }

    /**
     * 작은 창으로 보이던 시간을 보이는 구간 포함일 때만 세는지 검증
     *
     * Given: a 재개 1000, a 일시 정지 2000, 홈 재개 2000, a 정지 9000
     * When: includeVisible true, false로 각각 합산
     * Then: true면 a 8000, false면 a 1000
     */
    @Test
    fun test_12_includesPipTime() = runTest {
        /** Given **/
        val events = listOf(resumed("a", 1000), paused("a", 2000), resumed("home", 2000), stopped("a", 9000))

        /** When **/
        val withVisible = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 0, includeVisible = true)
        val foregroundOnly = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 0, includeVisible = false)

        /** Then **/
        assertEquals(mapOf("a" to 8000L), withVisible)
        assertEquals(mapOf("a" to 1000L), foregroundOnly)
    }

    /**
     * 같은 클래스 화면을 하나 더 열어 생긴 보이는 구간의 틈을 포그라운드 구간이 덮는지 검증
     *
     * Given: a 재개 1000, 일시 정지 2000, 같은 클래스 재개 2000, 앞 화면 정지 2500, 일시 정지 4000, 홈 재개 4000, 정지 4500
     * When: includeVisible true로 합산
     * Then: 포그라운드 1000~4000과 보이는 구간 1000~2500, 4000~4500의 합집합 1000~4500으로 a 3500
     */
    @Test
    fun test_13_sameClassTwoInstancesCoveredByForeground() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000), resumed("a", 2000), stopped("a", 2500),
            paused("a", 4000), resumed("home", 4000), stopped("a", 4500),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 0, includeVisible = true)

        /** Then **/
        assertEquals(mapOf("a" to 3500L), result)
    }

    /**
     * 포그라운드 구간과 보이는 구간이 겹친 시간을 두 번 세지 않는지 검증
     *
     * Given: a 재개 1000, a 일시 정지 3000, 홈 재개 3000, a 정지 3500
     * When: includeVisible true로 합산
     * Then: 포그라운드 1000~3000과 보이는 구간 1000~3500의 합집합으로 a 2500
     */
    @Test
    fun test_14_overlapNotDoubleCounted() = runTest {
        /** Given **/
        val events = listOf(resumed("a", 1000), paused("a", 3000), resumed("home", 3000), stopped("a", 3500))

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 0, includeVisible = true)

        /** Then **/
        assertEquals(mapOf("a" to 2500L), result)
    }

    /**
     * 합집합으로 합친 뒤 짧은 이탈 합치기를 적용하는지 검증
     *
     * Given: a 1000~2000(정지 2000), 5초 뒤 a 7000~8000(정지 8000)
     * When: includeVisible true, gap 10000으로 합산
     * Then: 이탈 시간을 포함해 1000~8000으로 합쳐 a 7000
     */
    @Test
    fun test_15_mergeGapAppliesAfterUnion() = runTest {
        /** Given **/
        val events = listOf(
            resumed("a", 1000), paused("a", 2000), resumed("home", 2000), stopped("a", 2000),
            paused("home", 7000), resumed("a", 7000), paused("a", 8000), resumed("home", 8000), stopped("a", 8000),
        )

        /** When **/
        val result = AppUsageTimeCalculator.sumUsageMillis(events, 0, 10_000, listOf("a"), 10_000, includeVisible = true)

        /** Then **/
        assertEquals(mapOf("a" to 7000L), result)
    }
}
