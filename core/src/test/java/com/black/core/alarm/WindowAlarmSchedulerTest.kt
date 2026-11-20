package com.black.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [WindowAlarmScheduler]의 창 알람 예약과 취소 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowAlarmSchedulerTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, 1, Intent("test.WINDOW_ALARM").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 깨우지 않는 창 알람이 예약되는지 검증
     *
     * Given: PendingIntent
     * When: schedule(1_000_000, 600_000) 호출
     * Then: RTC 타입, trigger 1_000_000인 알람 하나
     */
    @Test
    fun test_01_scheduleSetsRtcWindowAlarm() = runTest {
        /** Given **/
        val operation = pendingIntent()

        /** When **/
        WindowAlarmScheduler(context).schedule(1_000_000L, 600_000L, operation)

        /** Then **/
        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals(1_000_000L, alarm.triggerAtMs)
    }

    /**
     * 취소하면 예약된 알람이 없는지 검증
     *
     * Given: 예약된 창 알람
     * When: cancel 호출
     * Then: 예약된 알람 없음
     */
    @Test
    fun test_02_cancelRemovesAlarm() = runTest {
        /** Given **/
        val operation = pendingIntent()
        WindowAlarmScheduler(context).schedule(1_000_000L, 600_000L, operation)

        /** When **/
        WindowAlarmScheduler.cancel(context, operation)

        /** Then **/
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }
}
