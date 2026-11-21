package com.black.feature.dday.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf

/** [DdayWidgetReceiver]의 자정·시간 변경 액션 처리와 위젯 활성·비활성 콜백 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayWidgetReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val receiver = DdayWidgetReceiver()

    @Before
    fun setupWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun alarmShadow() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    /** IO에서 도는 수신기 처리가 알람을 지울 때까지 실제 시간으로 대기 */
    private suspend fun awaitNoAlarm() = withContext(Dispatchers.Default) {
        withTimeout(10_000L) {
            while (alarmShadow().peekNextScheduledAlarm() != null) {
                delay(50L)
            }
        }
    }

    /** IO에서 도는 수신기 처리가 다음 자정 알람을 걸 때까지 실제 시간으로 대기 */
    private suspend fun awaitAlarm() = withContext(Dispatchers.Default) {
        withTimeout(10_000L) {
            while (alarmShadow().peekNextScheduledAlarm() == null) {
                delay(50L)
            }
        }
    }

    /**
     * 자정 액션을 받으면 다음 자정 알람을 다시 거는지 검증
     *
     * Given: 위젯이 놓였고(갱신은 실제 Glance 대신 생략) 예약된 알람이 없는 상태
     * When: ACTION_MIDNIGHT_REFRESH 브로드캐스트 수신
     * Then: 갱신 뒤 ACTION_MIDNIGHT_REFRESH 알람이 다시 예약됨
     */
    @Test
    fun test_01_midnightActionReschedulesAlarm() = runTest {
        /** Given **/
        mockkConstructor(DdayWidgetRefresher::class)
        coEvery { anyConstructed<DdayWidgetRefresher>().hasWidgets() } returns true
        coEvery { anyConstructed<DdayWidgetRefresher>().refresh() } just Runs
        assertNull(alarmShadow().peekNextScheduledAlarm())

        /** When **/
        receiver.onReceive(context, Intent(DdayWidgetReceiver.ACTION_MIDNIGHT_REFRESH))
        awaitAlarm()

        /** Then **/
        val alarm = alarmShadow().peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(DdayWidgetReceiver.ACTION_MIDNIGHT_REFRESH, shadowOf(alarm.operation).savedIntent.action)
    }

    /**
     * 위젯이 활성화되면 goAsync 없이 알람과 캘린더 변경 감시가 바로 걸리는지 검증
     *
     * Given: 예약이 없는 상태
     * When: onEnabled 호출
     * Then: 반환 직후 알람과 대기 중인 감시 작업이 있음
     */
    @Test
    fun test_02_onEnabledSchedulesSynchronously() = runTest {
        /** Given / When **/
        receiver.onEnabled(context)

        /** Then **/
        assertNotNull(alarmShadow().peekNextScheduledAlarm())
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("dday_calendar_change").get()
        assertTrue(infos.any { it.state == WorkInfo.State.ENQUEUED })
    }

    /**
     * 마지막 위젯이 지워지면 goAsync 없이 알람과 감시가 바로 취소되는지 검증
     *
     * Given: onEnabled로 알람과 감시가 걸린 상태
     * When: onDisabled 호출
     * Then: 반환 직후 알람이 없고 감시 작업이 대기 상태로 남지 않음
     */
    @Test
    fun test_03_onDisabledCancelsSynchronously() = runTest {
        /** Given **/
        receiver.onEnabled(context)

        /** When **/
        receiver.onDisabled(context)

        /** Then **/
        assertNull(alarmShadow().peekNextScheduledAlarm())
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("dday_calendar_change").get()
        assertTrue(infos.none { it.state == WorkInfo.State.ENQUEUED })
    }

    /**
     * 놓인 위젯이 없으면 자정 액션이 알람과 감시를 취소하고 끝나는지 검증
     *
     * Given: 위젯은 없는데 알람과 감시가 남아 있는 상태
     * When: ACTION_MIDNIGHT_REFRESH 브로드캐스트 수신
     * Then: 알람이 지워지고 감시 작업이 대기 상태로 남지 않음
     */
    @Test
    fun test_04_midnightActionWithoutWidgetCancelsAll() = runTest {
        /** Given **/
        receiver.onEnabled(context)
        assertNotNull(alarmShadow().peekNextScheduledAlarm())

        /** When **/
        receiver.onReceive(context, Intent(DdayWidgetReceiver.ACTION_MIDNIGHT_REFRESH))
        awaitNoAlarm()

        /** Then **/
        assertNull(alarmShadow().peekNextScheduledAlarm())
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("dday_calendar_change").get()
        assertTrue(infos.none { it.state == WorkInfo.State.ENQUEUED })
    }
}
