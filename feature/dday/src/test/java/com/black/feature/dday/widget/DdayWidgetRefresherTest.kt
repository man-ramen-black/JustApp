package com.black.feature.dday.widget

import android.app.AlarmManager
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Shadows.shadowOf
import java.time.ZoneId
import java.time.ZonedDateTime

/** [DdayWidgetRefresher]의 자정 알람 예약, 캘린더 변경 감시 작업, 취소 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayWidgetRefresherTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val seoul = ZoneId.of("Asia/Seoul")

    private lateinit var refresher: DdayWidgetRefresher

    @Before
    fun setupRefresher() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        refresher = DdayWidgetRefresher(context)
    }

    private fun scheduledAlarmManager() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    /**
     * 다음 날 0시에 갱신 알람이 예약되는지 검증
     *
     * Given: 2026-11-20 18:00(서울)
     * When: scheduleNextMidnight 호출
     * Then: RTC 알람의 시각이 2026-11-21 00:00(서울) 밀리초이고 인텐트 액션이 ACTION_MIDNIGHT_REFRESH
     */
    @Test
    fun test_01_scheduleNextMidnightAtStartOfNextDay() = runTest {
        /** Given **/
        val now = ZonedDateTime.of(2026, 11, 20, 18, 0, 0, 0, seoul)
        val expectedTrigger = ZonedDateTime.of(2026, 11, 21, 0, 0, 0, 0, seoul).toInstant().toEpochMilli()

        /** When **/
        refresher.scheduleNextMidnight(now)

        /** Then **/
        val alarm = scheduledAlarmManager().peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals(expectedTrigger, alarm.triggerAtMs)
        assertEquals(DdayWidgetReceiver.ACTION_MIDNIGHT_REFRESH, shadowOf(alarm.operation).savedIntent.action)
    }

    /**
     * 캘린더 변경 감시가 콘텐츠 URI 트리거 작업으로 대기하는지 검증
     *
     * Given: 감시 작업이 없는 상태
     * When: watchCalendarChanges 호출
     * Then: 고유 작업 dday_calendar_change가 대기 상태이고 제약에 CalendarContract.CONTENT_URI(하위 포함) 트리거가 있음
     */
    @Test
    fun test_02_watchCalendarChangesEnqueuesContentTrigger() = runTest {
        /** Given / When **/
        refresher.watchCalendarChanges()

        /** Then **/
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("dday_calendar_change").get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        val triggers = infos.single().constraints.contentUriTriggers
        assertTrue(
            triggers.any { it.uri == CalendarContract.CONTENT_URI && it.isTriggeredForDescendants },
        )
    }

    /**
     * cancelAll이 알람과 감시 작업을 모두 지우는지 검증
     *
     * Given: 자정 알람과 캘린더 변경 감시 작업이 걸린 상태
     * When: cancelAll 호출
     * Then: 예약된 알람이 없고 고유 작업이 대기 상태로 남지 않음
     */
    @Test
    fun test_03_cancelAllRemovesAlarmAndWork() = runTest {
        /** Given **/
        refresher.scheduleNextMidnight(ZonedDateTime.of(2026, 11, 20, 18, 0, 0, 0, seoul))
        refresher.watchCalendarChanges()

        /** When **/
        refresher.cancelAll()

        /** Then **/
        assertNull(scheduledAlarmManager().peekNextScheduledAlarm())
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("dday_calendar_change").get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
