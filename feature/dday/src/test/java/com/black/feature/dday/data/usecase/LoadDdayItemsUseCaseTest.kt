package com.black.feature.dday.data.usecase

import com.black.core.system.CalendarEventInstance
import com.black.core.system.CalendarSource
import com.black.feature.dday.data.DdayRepository
import com.black.feature.dday.model.queryRange
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [LoadDdayItemsUseCase]의 권한·조회 실패 분기와 항목 변환, 제외 캘린더 적용 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LoadDdayItemsUseCaseTest : BaseTest() {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 20)
    private val now = today.atTime(18, 0).atZone(zone).toInstant()

    private val source = mockk<CalendarSource>()
    private val repository = mockk<DdayRepository>()
    private val useCase = LoadDdayItemsUseCase(source, repository, { now }, { zone })

    @Before
    fun setupDefaults() {
        every { source.canRead() } returns true
        coEvery { repository.getExcludedCalendarIds() } returns emptySet()
    }

    private fun allDayBegin(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timedBegin(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * 읽기 권한이 없으면 조회하지 않고 권한 없음을 돌려주는지 검증
     *
     * Given: canRead false
     * When: load
     * Then: NoPermission, queryInstances 호출 없음
     */
    @Test
    fun test_01_noPermissionWhenCannotRead() = runTest {
        /** Given **/
        every { source.canRead() } returns false

        /** When **/
        val result = useCase.load()

        /** Then **/
        assertEquals(DdayLoadResult.NoPermission, result)
        verify(exactly = 0) { source.queryInstances(any(), any()) }
    }

    /**
     * 조회가 null이면 실패를 돌려주는지 검증
     *
     * Given: canRead true, queryInstances null
     * When: load
     * Then: Failed
     */
    @Test
    fun test_02_failedWhenQueryReturnsNull() = runTest {
        /** Given **/
        every { source.queryInstances(any(), any()) } returns null

        /** When / Then **/
        assertEquals(DdayLoadResult.Failed, useCase.load())
    }

    /**
     * 일정이 남은 날짜·날짜 문구 항목으로 바뀌는지 검증
     *
     * Given: 오늘 종일 일정과 9일 뒤 15:00 일정
     * When: load
     * Then: 라벨 D-Day·D-9, 날짜 문구 `11/20 Fri`·`11/29 Sun PM 3:00`, queryRange로 조회
     */
    @Test
    fun test_03_loadedBuildsItems() = runTest {
        /** Given **/
        val range = queryRange(today, zone)
        val todayEvent = CalendarEventInstance(1L, 1L, "오늘", allDayBegin(today), allDayBegin(today.plusDays(1)), true)
        val laterBegin = timedBegin(today.plusDays(9), 15)
        val laterEvent = CalendarEventInstance(2L, 1L, "나중", laterBegin, laterBegin + 3_600_000L, false)
        every { source.queryInstances(range.first, range.last) } returns listOf(laterEvent, todayEvent)

        /** When **/
        val result = useCase.load() as DdayLoadResult.Loaded

        /** Then **/
        assertEquals(listOf("D-Day", "D-9"), result.items.map { it.label })
        assertEquals(listOf("11/20 Fri", "11/29 Sun PM 3:00"), result.items.map { it.dateText })
        verify { source.queryInstances(range.first, range.last) }
    }

    /**
     * 제외한 캘린더의 일정이 결과에서 빠지는지 검증
     *
     * Given: 캘린더 1·2의 일정, 저장소에서 캘린더 2 제외
     * When: load
     * Then: 캘린더 1의 일정만 남음
     */
    @Test
    fun test_04_excludedCalendarsApplied() = runTest {
        /** Given **/
        coEvery { repository.getExcludedCalendarIds() } returns setOf(2L)
        val begin = allDayBegin(today)
        every { source.queryInstances(any(), any()) } returns listOf(
            CalendarEventInstance(1L, 1L, "유지", begin, begin, true),
            CalendarEventInstance(2L, 2L, "제외", begin, begin, true),
        )

        /** When **/
        val result = useCase.load() as DdayLoadResult.Loaded

        /** Then **/
        assertEquals(listOf(1L), result.items.map { it.eventId })
    }

    /**
     * 제목이 비었거나 공백이면 항목 제목이 null로 남는지 검증
     *
     * Given: 제목이 빈 문자열·공백·null인 일정
     * When: load
     * Then: 모든 항목의 title이 null (표시 단계에서 dday_untitled로 대체)
     */
    @Test
    fun test_05_blankTitleKeptNullForUi() = runTest {
        /** Given **/
        val begin = allDayBegin(today)
        every { source.queryInstances(any(), any()) } returns listOf(
            CalendarEventInstance(1L, 1L, "", begin, begin, true),
            CalendarEventInstance(2L, 1L, "  ", begin, begin, true),
            CalendarEventInstance(3L, 1L, null, begin, begin, true),
        )

        /** When **/
        val result = useCase.load() as DdayLoadResult.Loaded

        /** Then **/
        assertEquals(3, result.items.size)
        result.items.forEach { assertNull(it.title) }
    }

    /**
     * limitTo가 불러온 항목을 앞에서 개수만큼 남기고 다른 결과는 그대로 두는지 검증
     *
     * Given: 오늘부터 하루씩 시작하는 일정 3개를 불러온 Loaded 결과와 NoPermission 결과
     * When: limitTo(2) 호출
     * Then: Loaded는 앞의 2개만 남고, NoPermission은 그대로
     */
    @Test
    fun test_06_limitToKeepsFirstItems() = runTest {
        /** Given **/
        every { source.queryInstances(any(), any()) } returns (0L..2L).map { offset ->
            val begin = allDayBegin(today.plusDays(offset))
            CalendarEventInstance(offset + 1, 1L, "일정 $offset", begin, begin, true)
        }
        val loaded = useCase.load()

        /** When **/
        val limited = loaded.limitTo(2) as DdayLoadResult.Loaded
        val noPermission = DdayLoadResult.NoPermission.limitTo(2)

        /** Then **/
        assertEquals(listOf(1L, 2L), limited.items.map { it.eventId })
        assertEquals(DdayLoadResult.NoPermission, noPermission)
    }
}
