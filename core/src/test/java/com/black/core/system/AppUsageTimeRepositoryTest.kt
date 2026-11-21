package com.black.core.system

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.time.Duration.Companion.hours

/** [AppUsageTimeRepository]의 조회 범위, 권한 없음 처리, 보이는 구간 포함 여부 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUsageTimeRepositoryTest : BaseTest() {

    /**
     * 기간 시작보다 여유를 두고 조회하고 기간 안 사용 시간을 돌려주는지 검증
     *
     * Given: now 기준 a를 500ms 사용하고 바로 정지한 이벤트를 주는 소스
     * When: 24시간 조회
     * Then: a 500, 조회 범위는 now - 24시간 - 여유 ~ now
     */
    @Test
    fun test_01_queriesPeriodWithLookback() = runTest {
        /** Given **/
        val now = 100_000_000_000L
        var queriedBegin = -1L
        var queriedEnd = -1L
        val source = UsageEventSource { begin, end ->
            queriedBegin = begin
            queriedEnd = end
            listOf(
                RawUsageEvent(RawUsageEventKind.RESUMED, "a", now - 1000),
                RawUsageEvent(RawUsageEventKind.PAUSED, "a", now - 500),
                RawUsageEvent(RawUsageEventKind.RESUMED, "home", now - 500),
                RawUsageEvent(RawUsageEventKind.STOPPED, "a", now - 500),
            )
        }
        val repository = AppUsageTimeRepository(source) { now }

        /** When **/
        val result = repository.getRecentUsageMillis(listOf("a"), 24.hours, 0)

        /** Then **/
        assertEquals(mapOf("a" to 500L), result)
        assertEquals(now - 24.hours.inWholeMilliseconds - AppUsageTimeRepository.QUERY_LOOKBACK_MILLIS, queriedBegin)
        assertEquals(now, queriedEnd)
    }

    /**
     * 권한이 없어 조회하지 못하면 null인지 검증
     *
     * Given: null을 주는 소스
     * When: 조회
     * Then: null
     */
    @Test
    fun test_02_returnsNullWithoutPermission() = runTest {
        /** Given **/
        val repository = AppUsageTimeRepository({ _, _ -> null }) { 100_000L }

        /** When **/
        val result = repository.getRecentUsageMillis(listOf("a"), 24.hours, 0)

        /** Then **/
        assertNull(result)
    }

    /**
     * 보이는 구간을 포함하지 않으면 작은 창 시간을 빼고 포그라운드만 세는지 검증
     *
     * Given: a 재개 now-3000, a 일시 정지 now-2000, 홈 재개 now-2000, a 정지 없음, includeVisible false
     * When: 24시간 조회
     * Then: a 1000
     */
    @Test
    fun test_03_includeVisibleFalseCountsForegroundOnly() = runTest {
        /** Given **/
        val now = 100_000_000_000L
        val repository = AppUsageTimeRepository(pipSource(now), includeVisible = false) { now }

        /** When **/
        val result = repository.getRecentUsageMillis(listOf("a"), 24.hours, 0)

        /** Then **/
        assertEquals(mapOf("a" to 1000L), result)
    }

    /**
     * 보이는 구간을 포함하면 작은 창으로 보이는 시간을 조회 끝까지 세는지 검증
     *
     * Given: test_03과 같은 이벤트, includeVisible true
     * When: 24시간 조회
     * Then: a가 조회 끝 now에서 닫혀 3000
     */
    @Test
    fun test_04_includeVisibleTrueCountsPip() = runTest {
        /** Given **/
        val now = 100_000_000_000L
        val repository = AppUsageTimeRepository(pipSource(now), includeVisible = true) { now }

        /** When **/
        val result = repository.getRecentUsageMillis(listOf("a"), 24.hours, 0)

        /** Then **/
        assertEquals(mapOf("a" to 3000L), result)
    }

    /** a를 작은 창으로 줄이고 정지 없이 홈으로 간 이벤트를 주는 소스 */
    private fun pipSource(now: Long) = UsageEventSource { _, _ ->
        listOf(
            RawUsageEvent(RawUsageEventKind.RESUMED, "a", now - 3000),
            RawUsageEvent(RawUsageEventKind.PAUSED, "a", now - 2000),
            RawUsageEvent(RawUsageEventKind.RESUMED, "home", now - 2000),
        )
    }
}
