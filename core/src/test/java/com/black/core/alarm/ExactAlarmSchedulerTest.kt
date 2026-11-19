package com.black.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** [ExactAlarmScheduler]의 정확한 알람 예약, 허용 없음과 거부 때의 부정확한 알람 대체, 알람 취소 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExactAlarmSchedulerTest : BaseTest() {

    companion object {
        private const val TEST_ACTION = "com.black.core.alarm.TEST_ALARM"
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun before() {
        super.before()
        ShadowAlarmManager.reset()
    }

    /** 요청 코드마다 구분되는 테스트 PendingIntent */
    private fun operation(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, Intent(TEST_ACTION), PendingIntent.FLAG_IMMUTABLE)

    /** [alarms]를 알람 서비스로 돌려주는 컨텍스트, 예약 호출이 던지는 경우를 만들 때 씀 */
    private fun contextWith(alarms: AlarmManager) = object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.ALARM_SERVICE) alarms else super.getSystemService(name)
    }

    /** 정확한 알람이 허용된 목업 알람 서비스 */
    private fun mockAlarmManager(): AlarmManager = mockk<AlarmManager>(relaxed = true).also {
        every { it.canScheduleExactAlarms() } returns true
    }

    /**
     * 정확한 알람이 허용되면 기기 유휴 중에도 울리는 정확한 알람으로 예약하는지 검증
     *
     * Given: 정확한 알람 허용
     * When: 알람 두 개 예약
     * Then: 두 알람 모두 RTC_WAKEUP, 유휴 중 허용, 정확한 알람, 지정한 시각
     */
    @Test
    fun test_01_schedulesExactAlarmWhenAllowed() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = ExactAlarmScheduler(context)

        /** When **/
        scheduler.schedule(5_000L, operation(1))
        scheduler.schedule(6_000L, operation(2))

        /** Then **/
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(listOf(5_000L, 6_000L), alarms.map { it.triggerAtMs }.sorted())
        assertTrue(alarms.all { it.type == AlarmManager.RTC_WAKEUP && it.isAllowWhileIdle })
        assertTrue(alarms.all { it.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT })
    }

    /**
     * 정확한 알람이 허용되지 않으면 부정확한 알람으로 예약하는지 검증
     *
     * Given: 정확한 알람 허용 없음
     * When: 알람 하나 예약
     * Then: RTC_WAKEUP, 유휴 중 허용, 부정확한 알람
     */
    @Test
    fun test_02_schedulesInexactAlarmWhenNotAllowed() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = ExactAlarmScheduler(context)

        /** When **/
        scheduler.schedule(5_000L, operation(1))

        /** Then **/
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    }

    /**
     * 정확한 알람 예약이 거부되면 그 알람부터 부정확한 알람으로 예약하고 다시 시도하지 않는지 검증
     *
     * Given: 허용 확인은 통과했지만 두 번째 정확한 알람 예약이 SecurityException을 던짐
     * When: 알람 세 개 예약
     * Then: 첫 알람은 정확한 알람, 두 번째는 정확한 알람 시도 뒤 부정확한 알람, 세 번째는 부정확한 알람만
     */
    @Test
    fun test_03_fallsBackToInexactOnSecurityException() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any()) } throws SecurityException("revoked")
        val scheduler = ExactAlarmScheduler(contextWith(alarms))

        /** When **/
        scheduler.schedule(5_000L, operation(1))
        scheduler.schedule(6_000L, operation(2))
        scheduler.schedule(7_000L, operation(3))

        /** Then **/
        verifyOrder {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 7_000L, any())
        }
        verify(exactly = 0) { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 7_000L, any()) }
    }

    /**
     * 부정확한 알람 예약이 던져도 그 뒤 예약이 정확한 알람을 다시 시도하지 않는지 검증
     *
     * Given: 첫 정확한 알람 예약이 SecurityException, 그 대체인 부정확한 알람 예약도 예외를 던짐
     * When: 첫 예약(예외 전파) 뒤 두 번째 예약
     * Then: 첫 예약은 예외, 두 번째 예약은 부정확한 알람만 호출
     */
    @Test
    fun test_04_doesNotRetryExactAfterFallbackThrows() = runTest {
        /** Given **/
        val alarms = mockAlarmManager()
        every { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any()) } throws SecurityException("revoked")
        every { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 5_000L, any()) } throws IllegalStateException("failed")
        val scheduler = ExactAlarmScheduler(contextWith(alarms))

        /** When **/
        val thrown = runCatching { scheduler.schedule(5_000L, operation(1)) }.exceptionOrNull()
        scheduler.schedule(6_000L, operation(2))

        /** Then **/
        assertTrue(thrown is IllegalStateException)
        verify(exactly = 0) { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any()) }
        verify(exactly = 1) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 6_000L, any()) }
    }

    /**
     * 취소하면 알람과 PendingIntent가 함께 취소되는지 검증
     *
     * Given: 정확한 알람 허용, 알람 두 개 예약
     * When: 첫 알람 취소
     * Then: 남은 알람은 둘째 하나, 첫 PendingIntent는 취소됨
     */
    @Test
    fun test_05_cancelRemovesAlarmAndPendingIntent() = runTest {
        /** Given **/
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = ExactAlarmScheduler(context)
        val first = operation(1)
        scheduler.schedule(5_000L, first)
        scheduler.schedule(6_000L, operation(2))

        /** When **/
        ExactAlarmScheduler.cancel(context, first)

        /** Then **/
        assertEquals(listOf(6_000L), shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs })
        assertTrue(shadowOf(first).isCanceled)
    }
}
