package com.black.app.assistanttask.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.model.AssistantTaskRepository
import com.black.app.model.AssistantTaskSettingsRepository
import com.black.app.model.datastore.AssistantTaskDataStore
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import kotlin.coroutines.ContinuationInterceptor

/** [AssistantTaskAlarmScheduler]의 알람 예약·취소·정확도 선택, 늦게 울릴 알람 유지, 항목별 예외 처리, 호출 직렬화, 실행 디스패처 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskAlarmSchedulerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val settings = AssistantTaskSettingsRepository(context)
    private val repository = mockk<AssistantTaskRepository>()

    /** 각 테스트 전 DataStore와 예약된 알람 비우기 */
    @Before
    fun reset() {
        runTest { AssistantTaskDataStore(context).clear() }
        ShadowAlarmManager.reset()
    }

    private fun task(pageId: String, done: Boolean = false, at: Long = 5_000L) = AssistantTask(
        pageId, pageId, AssistantTaskKind.TASK, done, AssistantTaskDue.DateTime(at),
        AssistantTaskReminder.TIME, "", 0L, "",
    )

    private fun scheduler(settingsRepository: AssistantTaskSettingsRepository = settings, nowMillis: Long = 1_000L) =
        AssistantTaskAlarmScheduler(context, repository, settingsRepository, testDispatcherRule.testDispatcher) { nowMillis }

    /** [alarms]를 알람 서비스로 쓰고 지금 시각이 1,000인 예약기 */
    private fun schedulerWith(alarms: AlarmManager) =
        AssistantTaskAlarmScheduler(contextWith(alarms), repository, settings, testDispatcherRule.testDispatcher) { 1_000L }

    private fun scheduledPageIds(): Set<String> = shadowOf(alarmManager).scheduledAlarms
        .map { requireNotNull(shadowOf(it.operation).savedIntent.data).lastPathSegment.orEmpty() }
        .toSet()

    /** [alarms]를 알람 서비스로 돌려주는 컨텍스트, 예약 호출이 던지는 경우를 만들 때 씀 */
    private fun contextWith(alarms: AlarmManager) = object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.ALARM_SERVICE) alarms else super.getSystemService(name)
    }

    /** 정확한 알람이 허용된 목업 알람 서비스 */
    private fun mockAlarmManager(): AlarmManager = mockk<AlarmManager>(relaxed = true).also {
        every { it.canScheduleExactAlarms() } returns true
    }

    /** [operation]이 담은 page ID */
    private fun pageIdOf(operation: PendingIntent): String? =
        AssistantTaskAlarmScheduler.pageIdOf(shadowOf(operation).savedIntent)

    /** 지난 예약이 남긴 것처럼 [pageId] 알람의 PendingIntent 생성 */
    private fun createPendingIntent(pageId: String) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        PendingIntent.getBroadcast(context, 0, AssistantTaskAlarmScheduler.alarmIntent(context, pageId), flags)
    }

    /** 남아 있는 [pageId] 알람의 PendingIntent, 없으면 null */
    private fun existingPendingIntent(pageId: String): PendingIntent? {
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, AssistantTaskAlarmScheduler.alarmIntent(context, pageId), flags)
    }

    /**
     * 대상 둘을 예약하고 예약한 ID를 저장하는지 검증
     *
     * Given: 정확한 알람 허용, 대상 두 개
     * When: reschedule
     * Then: 알람 둘이 정확한 알람으로 예약되고 두 ID가 저장됨
     */
    @Test
    fun test_01_schedulesTargetsAndSavesIds() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coEvery { repository.getAll() } returns listOf(task("a"), task("b", at = 6_000L))

        /** When **/
        scheduler().reschedule()

        /** Then **/
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, alarms.size)
        assertEquals(setOf("a", "b"), scheduledPageIds())
        assertEquals(setOf(5_000L, 6_000L), alarms.map { it.triggerAtMs }.toSet())
        assertTrue(alarms.all { it.type == AlarmManager.RTC_WAKEUP && it.isAllowWhileIdle })
        assertTrue(alarms.all { it.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT })
        assertEquals(setOf("a", "b"), settings.getScheduledAlarmIds())
    }

    /**
     * 다음 호출에서 완료된 항목의 알람이 취소되는지 검증
     *
     * Given: 둘을 예약한 뒤 하나가 완료됨
     * When: reschedule 다시 호출
     * Then: 완료된 항목의 알람만 취소되고 저장 ID도 하나로 줄어듦
     */
    @Test
    fun test_02_cancelsAlarmOfTaskNoLongerTarget() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coEvery { repository.getAll() } returns listOf(task("a"), task("b"))
        scheduler().reschedule()
        coEvery { repository.getAll() } returns listOf(task("a"), task("b", done = true))

        /** When **/
        scheduler().reschedule()

        /** Then **/
        assertEquals(setOf("a"), scheduledPageIds())
        assertEquals(setOf("a"), settings.getScheduledAlarmIds())
    }

    /**
     * 정확한 알람을 쓸 수 없으면 부정확한 알람으로 예약하는지 검증
     *
     * Given: 정확한 알람 불가, 대상 하나
     * When: reschedule
     * Then: 알람 하나가 절전 중 허용이면서 정확하지 않은 알람으로 예약됨
     */
    @Test
    fun test_03_usesInexactAlarmWhenExactNotAllowed() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coEvery { repository.getAll() } returns listOf(task("a"))

        /** When **/
        scheduler().reschedule()

        /** Then **/
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    }

    /**
     * 저장소 예외가 나도 던지지 않는지 검증
     *
     * Given: 저장본 조회가 예외를 던짐
     * When: reschedule
     * Then: 예외 없이 끝나고 알람은 예약되지 않음
     */
    @Test
    fun test_04_doesNotThrowWhenRepositoryFails() = runTest {
        /** Given **/
        coEvery { repository.getAll() } throws IllegalStateException("fail")

        /** When **/
        scheduler().reschedule()

        /** Then **/
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    /**
     * 항목 하나의 예약이 예외를 던져도 나머지를 예약하고, PendingIntent를 만든 실패 항목도 저장하는지 검증
     *
     * Given: 대상 a·b·c, b의 정확한 알람 예약이 IllegalStateException을 던짐
     * When: reschedule
     * Then: a·b·c 모두 정확한 알람 예약을 시도하고 부정확한 알람 예약은 없음, 저장 ID는 a·b·c
     */
    @Test
    fun test_05_continuesAfterOneTargetFails() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
            if (pageIdOf(thirdArg()) == "b") throw IllegalStateException("fail")
        }
        coEvery { repository.getAll() } returns listOf(task("a"), task("b"), task("c"))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        verify(exactly = 3) { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any()) }
        verify(exactly = 0) { alarms.setAndAllowWhileIdle(any(), any(), any()) }
        assertEquals(setOf("a", "b", "c"), settings.getScheduledAlarmIds())
    }

    /**
     * 정확한 알람 예약이 SecurityException을 던지면 부정확한 알람으로 예약하는지 검증
     *
     * Given: 허용 확인은 통과했지만 b의 정확한 알람 예약이 SecurityException을 던짐, 대상 a·b·c
     * When: reschedule
     * Then: a는 정확한 알람, b·c는 부정확한 알람으로 예약되고 저장 ID는 a·b·c
     */
    @Test
    fun test_06_fallsBackToInexactOnSecurityException() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
            if (pageIdOf(thirdArg()) == "b") throw SecurityException("revoked")
        }
        coEvery { repository.getAll() } returns listOf(task("a"), task("b", at = 6_000L), task("c", at = 7_000L))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        verify {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 7_000L, any())
        }
        verify(exactly = 0) {
            alarms.setExactAndAllowWhileIdle(any(), 7_000L, any())
            alarms.setAndAllowWhileIdle(any(), 5_000L, any())
        }
        assertEquals(setOf("a", "b", "c"), settings.getScheduledAlarmIds())
    }

    /**
     * 경로 구분자와 공백이 든 page ID도 예약·취소되고 취소 뒤 PendingIntent가 남지 않는지 검증
     *
     * Given: page ID `a/b c` 항목을 예약한 뒤 완료됨
     * When: reschedule 다시 호출
     * Then: 첫 예약은 `a/b c`로 걸리고, 다시 호출 뒤 알람·저장 ID·PendingIntent가 모두 없음
     */
    @Test
    fun test_07_encodedPageIdSchedulesAndCancels() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coEvery { repository.getAll() } returns listOf(task("a/b c"))
        scheduler().reschedule()
        val scheduledBefore = scheduledPageIds()
        val savedBefore = settings.getScheduledAlarmIds()
        coEvery { repository.getAll() } returns listOf(task("a/b c", done = true))

        /** When **/
        scheduler().reschedule()

        /** Then **/
        assertEquals(setOf("a/b c"), scheduledBefore)
        assertEquals(setOf("a/b c"), savedBefore)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertTrue(settings.getScheduledAlarmIds().isEmpty())
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        assertNull(PendingIntent.getBroadcast(context, 0, AssistantTaskAlarmScheduler.alarmIntent(context, "a/b c"), flags))
    }

    /**
     * 지난 알람 하나의 취소가 예외를 던져도 다른 지난 알람은 취소하고 대상은 예약하며 취소 못 한 ID만 저장에 남기는지 검증
     *
     * Given: 지난 예약 x·y(PendingIntent 있음), x의 취소만 IllegalStateException을 던짐, 대상 a
     * When: reschedule
     * Then: y는 취소되어 PendingIntent가 없고, a는 예약되며 저장 ID는 a·x
     */
    @Test
    fun test_08_keepsIdWhenCancelFails() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.cancel(any<PendingIntent>()) } answers {
            if (pageIdOf(firstArg()) == "x") throw IllegalStateException("fail")
        }
        createPendingIntent("x")
        createPendingIntent("y")
        settings.saveScheduledAlarmIds(setOf("x", "y"))
        coEvery { repository.getAll() } returns listOf(task("a"))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        verify {
            alarms.cancel(match<PendingIntent> { pageIdOf(it) == "y" })
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any())
        }
        assertNull(existingPendingIntent("y"))
        assertEquals(setOf("a", "x"), settings.getScheduledAlarmIds())
    }

    /**
     * 예약에 실패한 대상이 지난 예약에 있었으면 저장 ID에 남기는지 검증
     *
     * Given: 지난 예약 a, 이번 a의 예약이 IllegalStateException을 던짐
     * When: reschedule
     * Then: 지난 알람이 남아 있을 수 있어 저장 ID에 a가 남음
     */
    @Test
    fun test_09_keepsPreviousIdWhenRescheduleFails() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(any(), any(), any()) } throws IllegalStateException("fail")
        settings.saveScheduledAlarmIds(setOf("a"))
        coEvery { repository.getAll() } returns listOf(task("a"))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        assertEquals(setOf("a"), settings.getScheduledAlarmIds())
    }

    /**
     * 동시에 시작한 두 호출이 차례로 돌아 저장 ID와 실제 예약이 마지막 저장본과 같은지 검증
     *
     * Given: 첫 호출은 저장본 a를 읽고 지난 예약 ID를 읽은 뒤 멈춤, 두 번째 호출은 저장본 b를 읽음
     * When: 두 호출을 동시에 시작한 뒤 첫 호출을 풀어 줌
     * Then: 걸린 알람과 저장 ID가 모두 b
     */
    @Test
    fun test_10_serializesConcurrentCalls() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coEvery { repository.getAll() } returnsMany listOf(listOf(task("a")), listOf(task("b")))
        val settingsRepository = mockk<AssistantTaskSettingsRepository>()
        val gate = CompletableDeferred<Unit>()
        var stored = emptySet<String>()
        var reads = 0
        // 첫 읽기는 값을 읽은 뒤 멈춰, 잠금이 없으면 두 번째 호출이 그 사이에 끝남
        coEvery { settingsRepository.getScheduledAlarmIds() } coAnswers {
            val snapshot = stored
            if (reads++ == 0) gate.await()
            snapshot
        }
        coEvery { settingsRepository.saveScheduledAlarmIds(any()) } coAnswers {
            stored = firstArg()
            Result.success(Unit)
        }

        /** When **/
        val first = launch { scheduler(settingsRepository).reschedule() }
        runCurrent()
        val second = launch { scheduler(settingsRepository).reschedule() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        first.join()
        second.join()

        /** Then **/
        assertEquals(setOf("b"), scheduledPageIds())
        assertEquals(setOf("b"), stored)
    }

    /**
     * 기한이 막 지난 시각 알림 항목의 알람은 늦게 울릴 수 있어 취소하지 않고 저장 ID에 남기는지 검증
     *
     * Given: 기한 10,000,000인 미완료 시각 알림 a를 기한 전에 예약
     * When: 기한 1분 뒤 시각으로 reschedule 다시 호출
     * Then: a 알람이 그대로 걸려 있고 저장 ID에 a가 남음
     */
    @Test
    fun test_11_keepsAlarmJustPastDueForLateDelivery() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coEvery { repository.getAll() } returns listOf(task("a", at = 10_000_000L))
        scheduler(nowMillis = 9_000_000L).reschedule()

        /** When **/
        scheduler(nowMillis = 10_060_000L).reschedule()

        /** Then **/
        assertEquals(setOf("a"), scheduledPageIds())
        assertEquals(setOf("a"), settings.getScheduledAlarmIds())
    }

    /**
     * 기한이 유예 시간보다 오래 지난 시각 알림 항목의 알람은 취소하고 저장 ID에서 빼는지 검증
     *
     * Given: 기한 10,000,000인 미완료 시각 알림 a를 기한 전에 예약
     * When: 기한 2시간 뒤 시각으로 reschedule 다시 호출
     * Then: a 알람이 취소되고 저장 ID가 비어 있음
     */
    @Test
    fun test_12_cancelsAlarmLongPastDue() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coEvery { repository.getAll() } returns listOf(task("a", at = 10_000_000L))
        scheduler(nowMillis = 9_000_000L).reschedule()

        /** When **/
        scheduler(nowMillis = 17_200_000L).reschedule()

        /** Then **/
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertTrue(settings.getScheduledAlarmIds().isEmpty())
    }

    /**
     * 알람을 건 뒤 호출이 취소돼도 저장 ID를 저장하는지 검증
     *
     * Given: 대상 a, a의 정확한 알람 예약 중에 다시 예약을 돌리는 Job이 취소됨
     * When: reschedule을 띄워 끝날 때까지 기다림
     * Then: Job은 취소 상태, 저장 ID는 a
     */
    @Test
    fun test_13_savesIdsWhenCancelledAfterSettingAlarm() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        lateinit var job: Job
        every { alarms.setExactAndAllowWhileIdle(any(), any(), any()) } answers { job.cancel() }
        coEvery { repository.getAll() } returns listOf(task("a"))

        /** When **/
        job = launch { schedulerWith(alarms).reschedule() }
        job.join()

        /** Then **/
        verify(exactly = 1) { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any()) }
        assertTrue(job.isCancelled)
        assertEquals(setOf("a"), settings.getScheduledAlarmIds())
    }

    /**
     * 지난 알람을 취소한 뒤 정확한 알람 허용 확인이 던져도 취소한 ID를 저장 집합에서 빼는지 검증
     *
     * Given: 지난 예약 x(PendingIntent 있음, 대상 아님), 대상 a, canScheduleExactAlarms가 IllegalStateException을 던짐
     * When: reschedule
     * Then: x는 취소되어 PendingIntent가 없고, a 예약은 없으며 저장 ID는 비어 있음
     */
    @Test
    fun test_14_dropsCancelledIdsWhenExactCheckThrows() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.canScheduleExactAlarms() } throws IllegalStateException("fail")
        createPendingIntent("x")
        settings.saveScheduledAlarmIds(setOf("x"))
        coEvery { repository.getAll() } returns listOf(task("a"))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        verify { alarms.cancel(match<PendingIntent> { pageIdOf(it) == "x" }) }
        verify(exactly = 0) {
            alarms.setExactAndAllowWhileIdle(any(), any(), any())
            alarms.setAndAllowWhileIdle(any(), any(), any())
        }
        assertNull(existingPendingIntent("x"))
        assertTrue(settings.getScheduledAlarmIds().isEmpty())
    }

    /**
     * 옛 저장본을 읽고 멈춘 호출이 뒤 호출의 결과를 덮어쓰지 않는지 검증
     *
     * Given: 첫 호출은 저장본 a를 읽은 뒤 돌려주기 전에 멈춤, 두 번째 호출은 저장본 b를 읽음
     * When: 두 호출을 동시에 시작한 뒤 첫 호출을 풀어 줌
     * Then: 걸린 알람과 저장 ID가 모두 b
     */
    @Test
    fun test_15_serializesFromStoreRead() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        // 첫 읽기는 옛 저장본을 읽은 뒤 멈춰, 잠금이 읽기를 감싸지 않으면 두 번째 호출이 그 사이에 끝남
        coEvery { repository.getAll() } coAnswers {
            if (reads++ == 0) {
                val snapshot = listOf(task("a"))
                gate.await()
                snapshot
            } else {
                listOf(task("b"))
            }
        }

        /** When **/
        val first = launch { scheduler().reschedule() }
        runCurrent()
        val second = launch { scheduler().reschedule() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        first.join()
        second.join()

        /** Then **/
        assertEquals(setOf("b"), scheduledPageIds())
        assertEquals(setOf("b"), settings.getScheduledAlarmIds())
    }

    /**
     * 정확한 알람 거부 뒤 부정확한 알람 대체까지 던져도 다음 항목부터 부정확한 알람을 쓰는지 검증
     *
     * Given: 대상 a·b·c, b의 정확한 알람이 SecurityException, b의 부정확한 알람이 IllegalStateException을 던짐
     * When: reschedule
     * Then: a는 정확한 알람, c는 부정확한 알람으로만 예약, 저장 ID는 a·b·c
     */
    @Test
    fun test_16_staysInexactAfterFallbackFails() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
            if (pageIdOf(thirdArg()) == "b") throw SecurityException("revoked")
        }
        every { alarms.setAndAllowWhileIdle(any(), any(), any()) } answers {
            if (pageIdOf(thirdArg()) == "b") throw IllegalStateException("fail")
        }
        coEvery { repository.getAll() } returns listOf(task("a"), task("b", at = 6_000L), task("c", at = 7_000L))

        /** When **/
        schedulerWith(alarms).reschedule()

        /** Then **/
        verify {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 7_000L, any())
        }
        verify(exactly = 0) { alarms.setExactAndAllowWhileIdle(any(), 7_000L, any()) }
        assertEquals(setOf("a", "b", "c"), settings.getScheduledAlarmIds())
    }

    /**
     * 알람 인텐트에 예약 시각을 담고, 기한이 바뀌어 다시 예약하면 새 예약 시각으로 바꾸는지 검증
     *
     * Given: 정확한 알람 허용, 기한 5,000인 대상 a를 예약
     * When: a의 기한을 8,000으로 바꿔 reschedule 다시 호출
     * Then: 첫 예약의 인텐트 예약 시각 5,000, 다시 예약 뒤 알람 하나의 시각과 인텐트 예약 시각이 모두 8,000
     */
    @Test
    fun test_17_alarmIntentCarriesUpdatedTriggerTime() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coEvery { repository.getAll() } returns listOf(task("a"))
        scheduler().reschedule()
        val firstOperation = shadowOf(alarmManager).scheduledAlarms.single().operation
        val firstTriggerAt = AssistantTaskAlarmScheduler.triggerAtOf(shadowOf(firstOperation).savedIntent)
        coEvery { repository.getAll() } returns listOf(task("a", at = 8_000L))

        /** When **/
        scheduler().reschedule()

        /** Then **/
        assertEquals(5_000L, firstTriggerAt)
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(8_000L, alarm.triggerAtMs)
        assertEquals(8_000L, AssistantTaskAlarmScheduler.triggerAtOf(shadowOf(alarm.operation).savedIntent))
    }

    /**
     * 다시 예약 본체가 호출한 코루틴의 디스패처가 아니라 생성자로 받은 디스패처에서 도는지 검증
     *
     * Given: 정확한 알람 허용, 호출하는 코루틴과 다른 테스트 디스패처로 만든 예약기, 대상 a
     * When: reschedule
     * Then: 저장본 읽기가 받은 디스패처에서 돌고 a 알람이 예약됨
     */
    @Test
    fun test_18_runsOnGivenDispatcher() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarmDispatcher = StandardTestDispatcher(testScheduler)
        var readInterceptor: ContinuationInterceptor? = null
        coEvery { repository.getAll() } coAnswers {
            readInterceptor = currentCoroutineContext()[ContinuationInterceptor]
            listOf(task("a"))
        }

        /** When **/
        AssistantTaskAlarmScheduler(context, repository, settings, alarmDispatcher) { 1_000L }.reschedule()

        /** Then **/
        assertSame(alarmDispatcher, readInterceptor)
        assertEquals(setOf("a"), scheduledPageIds())
    }
}
