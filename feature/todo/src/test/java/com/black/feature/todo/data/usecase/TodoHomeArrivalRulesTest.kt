package com.black.feature.todo.data.usecase

import com.black.core.model.HomeTransition
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** [TodoHomeArrivalUseCase]의 알림 발송 판정과 대상 선별·정렬 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoHomeArrivalRulesTest : BaseTest() {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 11, 18)

    private fun task(
        id: String,
        due: TodoDue? = null,
        reminder: TodoReminder = TodoReminder.NONE,
        done: Boolean = false,
        lastEditedTime: Long = 0L,
    ) = Todo(id, id, done, due, reminder, "", Instant.ofEpochMilli(lastEditedTime), "")

    /** 서울 기준 [date] [hour]시의 시각 기한 */
    private fun dateTimeDue(date: LocalDate, hour: Int) =
        TodoDue.DateTime(ZonedDateTime.of(date, LocalTime.of(hour, 0), seoul).toInstant())

    /**
     * 출발 기록이 없는 실제 도착은 알리는지 검증
     *
     * Given: 추정 아닌 도착, 출발 기록 없음
     * When: shouldNotify
     * Then: true
     */
    @Test
    fun test_01_realArrivalWithoutLeaveRecordNotifies() = runTest {
        /** Given / When / Then **/
        assertTrue(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, 1_000_000L), null, null))
    }

    /**
     * 추정 도착과 출발은 알리지 않는지 검증
     *
     * Given: 추정 도착, 출발
     * When: shouldNotify
     * Then: 둘 다 false
     */
    @Test
    fun test_02_inferredArrivalAndLeaveDoNotNotify() = runTest {
        /** Given / When / Then **/
        assertFalse(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, true, 1_000_000L), null, null))
        assertFalse(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(false, false, 1_000_000L), null, null))
    }

    /**
     * 출발 10분 안의 도착은 알리지 않고 10분이 되면 알리는지 검증
     *
     * Given: 출발 시각 1_000_000
     * When: 9분 59초 뒤와 10분 뒤 도착
     * Then: false, true
     */
    @Test
    fun test_03_arrivalWithinTenMinutesOfLeaveDoesNotNotify() = runTest {
        /** Given **/
        val leave = 1_000_000L

        /** When / Then **/
        assertFalse(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, leave + 599_000L), leave, null))
        assertTrue(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, leave + 600_000L), leave, null))
    }

    /**
     * 대상 선별 조건과 순서 검증
     *
     * Given: 오늘 2026-11-18(서울), 완료·어제 날짜·오늘 시각·내일·기한 없는 집 도착·알림 없는 기한 없음·이른 기한 항목
     * When: targets
     * Then: 완료·내일·알림 없는 기한 없음은 제외, 기한 이른 순에 기한 없는 집 도착이 맨 뒤
     */
    @Test
    fun test_04_targetsFilterAndOrder() = runTest {
        /** Given **/
        // 2026-11-18 10:00 서울 = 2026-11-18T01:00:00Z
        val todayTime = 1_794_963_600_000L
        val tasks = listOf(
            task("homeNoDue", reminder = TodoReminder.HOME_ARRIVAL),
            task("today", TodoDue.DateTime(Instant.ofEpochMilli(todayTime))),
            task("yesterday", TodoDue.Date(LocalDate.of(2026, 11, 17))),
            task("tomorrow", TodoDue.Date(LocalDate.of(2026, 11, 19))),
            task("done", TodoDue.Date(LocalDate.of(2026, 11, 17)), done = true),
            task("none"),
            task("old", TodoDue.Date(LocalDate.of(2026, 11, 1))),
        )

        /** When **/
        val result = TodoHomeArrivalUseCase.targets(tasks, today, seoul).map { it.pageId }

        /** Then **/
        assertEquals(listOf("old", "yesterday", "today", "homeNoDue"), result)
    }

    /**
     * 같은 날 항목은 날짜만 있는 기한이 먼저, 그다음 시각 순인지 검증
     *
     * Given: 같은 날 18:00, 09:00 시각 기한과 날짜만 있는 기한을 거꾸로 입력
     * When: targets
     * Then: 날짜만, 09:00, 18:00
     */
    @Test
    fun test_05_sameDayOrderedByTime() = runTest {
        /** Given **/
        val tasks = listOf(
            task("late", dateTimeDue(today, 18)),
            task("early", dateTimeDue(today, 9)),
            task("dateOnly", TodoDue.Date(LocalDate.of(2026, 11, 18))),
        )

        /** When **/
        val result = TodoHomeArrivalUseCase.targets(tasks, today, seoul).map { it.pageId }

        /** Then **/
        assertEquals(listOf("dateOnly", "early", "late"), result)
    }

    /**
     * 기한 날짜를 기기 시간대로 판정하는지 검증
     *
     * Given: 서울 2026-11-19 07:00(UTC 11-18 22:00) 기한
     * When: 오늘 2026-11-18(서울)로 targets
     * Then: 대상 아님
     */
    @Test
    fun test_06_dueDateUsesDeviceZone() = runTest {
        /** Given **/
        val tomorrowSeoul = dateTimeDue(LocalDate.of(2026, 11, 19), 7)

        /** When **/
        val result = TodoHomeArrivalUseCase.targets(listOf(task("t", tomorrowSeoul)), today, seoul)

        /** Then **/
        assertTrue(result.isEmpty())
    }

    /**
     * 기한 없는 집 도착 항목은 화면의 기한 없음 구획처럼 최근 수정 순인지 검증
     *
     * Given: 기한 없는 집 도착 항목 셋을 수정 시각이 섞인 순서로 입력, 오늘 기한 항목 하나
     * When: targets
     * Then: 기한 있는 항목 뒤에 기한 없는 항목이 수정 시각 내림차순
     */
    @Test
    fun test_07_noDueTargetsOrderedByLastEditedDescending() = runTest {
        /** Given **/
        val tasks = listOf(
            task("middle", reminder = TodoReminder.HOME_ARRIVAL, lastEditedTime = 2_000L),
            task("oldest", reminder = TodoReminder.HOME_ARRIVAL, lastEditedTime = 1_000L),
            task("today", TodoDue.Date(LocalDate.of(2026, 11, 18))),
            task("newest", reminder = TodoReminder.HOME_ARRIVAL, lastEditedTime = 3_000L),
        )

        /** When **/
        val result = TodoHomeArrivalUseCase.targets(tasks, today, seoul).map { it.pageId }

        /** Then **/
        assertEquals(listOf("today", "newest", "middle", "oldest"), result)
    }

    /**
     * 추정 출발 직후의 실제 도착은 실제 출발 기록이 오래돼도 알리지 않는지 검증
     *
     * Given: 실제 출발 8시간 전, 추정 출발(재부팅 뒤 서비스 재시작) 시각
     * When: 추정 출발 30초 뒤 실제 도착
     * Then: false
     */
    @Test
    fun test_08_arrivalSoonAfterInferredLeaveDoesNotNotify() = runTest {
        /** Given **/
        val inferredLeave = 100_000_000L
        val leave = inferredLeave - 8 * 3_600_000L

        /** When **/
        val result = TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave + 30_000L), leave, inferredLeave)

        /** Then **/
        assertFalse(result)
    }

    /**
     * 추정 출발 5분이 지난 실제 도착은 실제 출발 기록으로 판정해 알리는지 검증
     *
     * Given: 실제 출발 8시간 전, 추정 출발 시각
     * When: 추정 출발 6분 뒤 실제 도착
     * Then: true
     */
    @Test
    fun test_09_arrivalAfterInferredLeaveSettleNotifies() = runTest {
        /** Given **/
        val inferredLeave = 100_000_000L
        val leave = inferredLeave - 8 * 3_600_000L

        /** When **/
        val result = TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave + 360_000L), leave, inferredLeave)

        /** Then **/
        assertTrue(result)
    }

    /**
     * 시계가 뒤로 가 출발 기록이 도착보다 늦으면 출발 기록 없음으로 보고 알리는지 검증
     *
     * Given: 도착 시각보다 1시간 늦은 실제 출발 기록과 추정 출발 기록
     * When: 실제 도착
     * Then: true
     */
    @Test
    fun test_10_leaveRecordAfterArrivalCountsAsNoRecord() = runTest {
        /** Given **/
        val arrival = 100_000_000L
        val futureRecord = arrival + 3_600_000L

        /** When **/
        val result = TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, arrival), futureRecord, futureRecord)

        /** Then **/
        assertTrue(result)
    }

    /**
     * 부팅 직후 시계 보정으로 도착 시각이 추정 출발보다 몇 초 이르면 알리지 않는지 검증
     *
     * Given: 실제 출발 8시간 전, 추정 출발 시각
     * When: 추정 출발보다 3초 이른 시각의 실제 도착
     * Then: false
     */
    @Test
    fun test_11_arrivalSecondsBeforeInferredLeaveDoesNotNotify() = runTest {
        /** Given **/
        val inferredLeave = 100_000_000L
        val leave = inferredLeave - 8 * 3_600_000L

        /** When **/
        val result = TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave - 3_000L), leave, inferredLeave)

        /** Then **/
        assertFalse(result)
    }

    /**
     * 추정 출발과의 차가 INFERRED_LEAVE_SETTLE_MILLIS 미만이면 알리지 않고 같으면 알리는지 검증
     *
     * Given: 실제 출발 8시간 전, 추정 출발 시각
     * When: 추정 출발 299_999ms 뒤·300_000ms 뒤, 299_999ms 앞·300_000ms 앞의 실제 도착
     * Then: 299_999ms는 앞뒤 모두 false, 300_000ms는 앞뒤 모두 true
     */
    @Test
    fun test_12_inferredLeaveSettleBoundary() = runTest {
        /** Given **/
        val inferredLeave = 100_000_000L
        val leave = inferredLeave - 8 * 3_600_000L
        val settle = TodoHomeArrivalUseCase.INFERRED_LEAVE_SETTLE_MILLIS

        /** When / Then **/
        assertFalse(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave + settle - 1), leave, inferredLeave))
        assertTrue(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave + settle), leave, inferredLeave))
        assertFalse(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave - settle + 1), leave, inferredLeave))
        assertTrue(TodoHomeArrivalUseCase.shouldNotify(HomeTransition(true, false, inferredLeave - settle), leave, inferredLeave))
    }
}
