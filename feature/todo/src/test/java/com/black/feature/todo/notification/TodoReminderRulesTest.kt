package com.black.feature.todo.notification

import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate

/** [TodoReminderScheduler]의 시각 알림 예약 대상, 취소하지 않을 ID, 알릴 항목, 울린 알람과 기한 일치 판정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoReminderRulesTest : BaseTest() {

    private fun task(
        pageId: String,
        done: Boolean = false,
        due: TodoDue? = TodoDue.DateTime(Instant.ofEpochMilli(2_000L)),
        reminder: TodoReminder = TodoReminder.TIME,
    ) = Todo(pageId, pageId, done, due, reminder, "", Instant.EPOCH, "")

    /**
     * 미완료·시각 알림·미래 기한 시각인 항목만 대상으로 고르는지 검증
     *
     * Given: 조건을 모두 만족하는 항목과 완료·날짜만·집 도착·알림 없음·지난 시각·기한 없음 항목
     * When: targets 호출
     * Then: 조건을 모두 만족하는 항목만 반환
     */
    @Test
    fun test_01_targetsOnlyOpenTimeReminderWithFutureDateTime() = runTest {
        /** Given **/
        val target = task("target")
        val tasks = listOf(
            target,
            task("done", done = true),
            task("dateOnly", due = TodoDue.Date(LocalDate.of(2026, 11, 18))),
            task("home", reminder = TodoReminder.HOME_ARRIVAL),
            task("none", reminder = TodoReminder.NONE),
            task("past", due = TodoDue.DateTime(Instant.ofEpochMilli(1_000L))),
            task("noDue", due = null),
        )

        /** When **/
        val result = TodoReminderScheduler.targets(tasks, now = Instant.ofEpochMilli(1_500L))

        /** Then **/
        assertEquals(listOf(target), result)
    }

    /**
     * 기한 시각이 지금과 같으면 대상에서 제외하는지 검증
     *
     * Given: 기한 시각이 지금과 같은 항목
     * When: targets 호출
     * Then: 빈 목록
     */
    @Test
    fun test_02_excludesTaskDueExactlyNow() = runTest {
        /** Given / When **/
        val result = TodoReminderScheduler.targets(listOf(task("now")), now = Instant.ofEpochMilli(2_000L))

        /** Then **/
        assertEquals(emptyList<Todo>(), result)
    }

    /**
     * 기한 시각이 지났어도 미완료 시각 알림이면 알릴 항목으로 보는지 검증
     *
     * Given: 기한 시각이 지난 미완료 시각 알림 항목과 완료·날짜만·집 도착·기한 없음 항목
     * When: 각각 isTimeReminder 호출
     * Then: 첫 항목만 true
     */
    @Test
    fun test_03_isTimeReminderIgnoresDueTime() = runTest {
        /** Given **/
        val past = task("past", due = TodoDue.DateTime(Instant.ofEpochMilli(1_000L)))
        val others = listOf(
            task("done", done = true),
            task("dateOnly", due = TodoDue.Date(LocalDate.of(2026, 11, 18))),
            task("home", reminder = TodoReminder.HOME_ARRIVAL),
            task("noDue", due = null),
        )

        /** When **/
        val pastResult = TodoReminderScheduler.isTimeReminder(past)
        val otherResults = others.map { TodoReminderScheduler.isTimeReminder(it) }

        /** Then **/
        assertTrue(pastResult)
        assertEquals(List(4) { false }, otherResults)
    }

    /**
     * 미래 기한과 유예 시간 안에 지난 기한의 시각 알림 항목만 취소하지 않을 ID로 고르는지 검증
     *
     * Given: 지금 10,000,000, 미래·1분 전·딱 유예 경계·2시간 전 기한의 시각 알림 항목과 1분 전 기한의 완료·집 도착·날짜만 항목
     * When: keptAlarmIds 호출
     * Then: 미래·1분 전·유예 경계 항목의 ID만 반환
     */
    @Test
    fun test_04_keptAlarmIdsIncludeLateDeliveryWindow() = runTest {
        /** Given **/
        val nowMillis = 10_000_000L
        val oneMinuteAgo = TodoDue.DateTime(Instant.ofEpochMilli(nowMillis - 60_000L))
        val tasks = listOf(
            task("future", due = TodoDue.DateTime(Instant.ofEpochMilli(nowMillis + 60_000L))),
            task("recent", due = oneMinuteAgo),
            task("edge", due = TodoDue.DateTime(Instant.ofEpochMilli(nowMillis - TodoReminderScheduler.LATE_DELIVERY_GRACE.inWholeMilliseconds))),
            task("old", due = TodoDue.DateTime(Instant.ofEpochMilli(nowMillis - 7_200_000L))),
            task("done", done = true, due = oneMinuteAgo),
            task("home", due = oneMinuteAgo, reminder = TodoReminder.HOME_ARRIVAL),
            task("dateOnly", due = TodoDue.Date(LocalDate.of(2026, 11, 18))),
        )

        /** When **/
        val result = TodoReminderScheduler.keptAlarmIds(tasks, Instant.ofEpochMilli(nowMillis))

        /** Then **/
        assertEquals(setOf("future", "recent", "edge"), result)
    }

    /**
     * 예약 시각과 기한의 차가 허용치 이하일 때만 지금 기한의 알람으로 보는지 검증
     *
     * Given: 예약 시각 10,000,000
     * When: 기한을 예약 시각과 같게, 앞뒤로 60,000ms, 앞뒤로 60,001ms 차이 나게 두고 각각 isAlarmForCurrentDue 호출
     * Then: 같음·60,000ms 차이는 true, 60,001ms 차이는 false
     */
    @Test
    fun test_05_alarmMatchesDueWithinTolerance() = runTest {
        /** Given **/
        val triggerAt = 10_000_000L
        val tolerance = TodoReminderScheduler.ALARM_TIME_TOLERANCE.inWholeMilliseconds

        /** When **/
        val matched = listOf(triggerAt, triggerAt - tolerance, triggerAt + tolerance)
            .map { dueMillis -> TodoReminderScheduler.isAlarmForCurrentDue(Instant.ofEpochMilli(dueMillis), Instant.ofEpochMilli(triggerAt)) }
        val moved = listOf(triggerAt - tolerance - 1, triggerAt + tolerance + 1)
            .map { dueMillis -> TodoReminderScheduler.isAlarmForCurrentDue(Instant.ofEpochMilli(dueMillis), Instant.ofEpochMilli(triggerAt)) }

        /** Then **/
        assertEquals(60_000L, tolerance)
        assertEquals(List(3) { true }, matched)
        assertEquals(List(2) { false }, moved)
    }
}
