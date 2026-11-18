package com.black.app.assistanttask.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.broadcast.PackageInstallTestFixture
import com.black.app.model.AssistantTaskRepository
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** [AssistantTaskAlarmReceiver]의 저장본 재확인 뒤 알림 여부와 다시 예약 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskAlarmReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = mockk<AssistantTaskRepository>()
    private val rescheduled = CompletableDeferred<Unit>()
    private val rescheduleCount = AtomicInteger()
    private var repositoryCreations = 0

    private val originalRepository = AssistantTaskAlarmReceiver.createRepository
    private val originalRescheduler = AssistantTaskAlarmReceiver.createRescheduler

    /** 알림 권한 허용, 저장본 조회와 다시 예약을 테스트용으로 대체 */
    @Before
    fun replaceDependencies() {
        PackageInstallTestFixture.grantNotificationPermission(context)
        AssistantTaskAlarmReceiver.createRepository = {
            repositoryCreations++
            repository
        }
        AssistantTaskAlarmReceiver.createRescheduler = {
            AssistantTaskAlarmRescheduler {
                rescheduleCount.incrementAndGet()
                rescheduled.complete(Unit)
            }
        }
    }

    @After
    fun restoreDependencies() {
        AssistantTaskAlarmReceiver.createRepository = originalRepository
        AssistantTaskAlarmReceiver.createRescheduler = originalRescheduler
    }

    private fun task(
        pageId: String,
        done: Boolean = false,
        reminder: AssistantTaskReminder = AssistantTaskReminder.TIME,
        dueMillis: Long = 5_000L,
    ) = AssistantTask(
        pageId, "장보기", AssistantTaskKind.TASK, done, AssistantTaskDue.DateTime(dueMillis), reminder, "", 0L, "",
    )

    /**
     * 예약기와 같은 인텐트로 [pageId] 알람 브로드캐스트 전송
     *
     * 예약 시각 [triggerAtMillis]가 null이면 extra 없이 보냄.
     */
    private fun sendAlarm(pageId: String, triggerAtMillis: Long? = 5_000L) {
        context.sendBroadcast(AssistantTaskAlarmScheduler.alarmIntent(context, pageId, triggerAtMillis))
        idleMainLooper()
    }

    /**
     * 다시 예약이 불릴 때까지 실제 시간으로 최대 5초 대기
     *
     * 수신 처리가 goAsync로 이어 가는 Dispatchers.Default 범위에서 돌아 가상 시간 진행으로는 기다릴 수 없음.
     */
    private suspend fun awaitRescheduled() {
        withContext(Dispatchers.Default) { withTimeout(5_000) { rescheduled.await() } }
    }

    private fun notificationCount() = PackageInstallTestFixture.shadowNotificationManager(context).allNotifications.size

    /**
     * 미완료 시각 알림 Todo면 알림 한 번을 띄우고 다시 예약하는지 검증
     *
     * Given: 저장본의 local-1이 미완료, 알림 TIME
     * When: local-1 알람 수신
     * Then: getById("local-1"), local-1 hashCode 알림 하나(제목 `장보기`), 다시 예약 호출
     */
    @Test
    fun test_01_openTimeTaskNotifiesAndReschedules() = runTest {
        /** Given **/
        coEvery { repository.getById("local-1") } returns task("local-1")

        /** When **/
        sendAlarm("local-1")
        awaitRescheduled()

        /** Then **/
        coVerify(exactly = 1) { repository.getById("local-1") }
        assertEquals(1, notificationCount())
        val notification = PackageInstallTestFixture.shadowNotificationManager(context).getNotification("local-1".hashCode())
        assertEquals("장보기", PackageInstallTestFixture.notificationTitle(requireNotNull(notification)))
    }

    /**
     * 그사이 완료된 Todo면 알리지 않고 다시 예약만 하는지 검증
     *
     * Given: 저장본의 a가 완료
     * When: a 알람 수신
     * Then: 알림 없음, 다시 예약 호출
     */
    @Test
    fun test_02_doneTaskShowsNothing() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns task("a", done = true)

        /** When **/
        sendAlarm("a")
        awaitRescheduled()

        /** Then **/
        assertEquals(0, notificationCount())
    }

    /**
     * 저장본에 없는 Todo면 알리지 않고 다시 예약만 하는지 검증
     *
     * Given: 저장본에 a 없음
     * When: a 알람 수신
     * Then: 알림 없음, 다시 예약 호출
     */
    @Test
    fun test_03_missingTaskShowsNothing() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns null

        /** When **/
        sendAlarm("a")
        awaitRescheduled()

        /** Then **/
        assertEquals(0, notificationCount())
    }

    /**
     * 알림 방식이 시각이 아니게 바뀐 Todo면 알리지 않는지 검증
     *
     * Given: 저장본의 a가 미완료, 알림 HOME_ARRIVAL
     * When: a 알람 수신
     * Then: 알림 없음, 다시 예약 호출
     */
    @Test
    fun test_04_nonTimeReminderShowsNothing() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns task("a", reminder = AssistantTaskReminder.HOME_ARRIVAL)

        /** When **/
        sendAlarm("a")
        awaitRescheduled()

        /** Then **/
        assertEquals(0, notificationCount())
    }

    /**
     * 저장본 조회가 실패해도 다시 예약은 하는지 검증
     *
     * Given: getById가 예외를 던짐
     * When: a 알람 수신
     * Then: 알림 없음, 다시 예약 호출
     */
    @Test
    fun test_05_lookupFailureStillReschedules() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } throws IllegalStateException("fail")

        /** When **/
        sendAlarm("a")
        awaitRescheduled()

        /** Then **/
        assertEquals(0, notificationCount())
    }

    /**
     * 알람 액션이 아니면 아무것도 하지 않는지 검증
     *
     * Given: 다른 액션에 알람과 같은 데이터를 담은 인텐트
     * When: onReceive 직접 호출
     * Then: 저장소 생성과 다시 예약 없음
     */
    @Test
    fun test_06_otherActionIsIgnored() = runTest {
        /** Given **/
        val intent = Intent(AssistantTaskAlarmScheduler.alarmIntent(context, "a")).setAction("com.other.ACTION")

        /** When **/
        AssistantTaskAlarmReceiver().onReceive(context, intent)
        idleMainLooper()

        /** Then **/
        assertEquals(0, repositoryCreations)
        assertFalse(rescheduled.isCompleted)
    }

    /**
     * 예약 뒤 기한이 뒤로 옮겨졌으면 알리지 않고 다시 예약만 하는지 검증
     *
     * Given: 저장본의 a가 미완료 시각 알림이고 기한이 예약 시각(5,000)보다 10분 뒤
     * When: 예약 시각 5,000을 담은 a 알람 수신
     * Then: 알림 없음, 다시 예약 1회
     */
    @Test
    fun test_07_dueMovedLaterShowsNothing() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns task("a", dueMillis = 5_000L + 600_000L)

        /** When **/
        sendAlarm("a")
        awaitRescheduled()

        /** Then **/
        coVerify(exactly = 1) { repository.getById("a") }
        assertEquals(0, notificationCount())
        assertEquals(1, rescheduleCount.get())
    }

    /**
     * 데이터 없는 알람 인텐트는 무시하는지 검증
     *
     * Given: 알람 액션이지만 page ID 데이터가 없는 인텐트
     * When: onReceive 직접 호출
     * Then: 저장소 생성·알림·다시 예약 없음
     */
    @Test
    fun test_08_intentWithoutDataIsIgnored() = runTest {
        /** Given **/
        val intent = Intent(context, AssistantTaskAlarmReceiver::class.java).setAction(AssistantTaskAlarmScheduler.ACTION_ALARM)

        /** When **/
        AssistantTaskAlarmReceiver().onReceive(context, intent)
        idleMainLooper()

        /** Then **/
        assertEquals(0, repositoryCreations)
        assertEquals(0, notificationCount())
        assertFalse(rescheduled.isCompleted)
    }

    /**
     * 예약 뒤 기한이 최근 과거로 앞당겨졌으면 옛 시각 알람에 알리지 않고 다시 예약만 하는지 검증
     *
     * Given: 저장본의 a가 미완료 시각 알림이고 기한이 13:30으로 앞당겨짐
     * When: 예약 시각 15:00을 담은 a 알람 수신
     * Then: 알림 없음, 다시 예약 1회
     */
    @Test
    fun test_09_dueMovedEarlierShowsNothing() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns task("a", dueMillis = ONE_THIRTY_PM)

        /** When **/
        sendAlarm("a", triggerAtMillis = THREE_PM)
        awaitRescheduled()

        /** Then **/
        assertEquals(0, notificationCount())
        assertEquals(1, rescheduleCount.get())
    }

    /**
     * 예약 시각 extra가 없는 알람은 저장본을 만들거나 보지 않고 알리지 않으며 다시 예약만 하는지 검증
     *
     * Given: 저장본의 a가 미완료 시각 알림이고 기한 5,000
     * When: 예약 시각 extra 없는 a 알람 수신
     * Then: 저장본 생성과 getById 호출 없음, 알림 없음, 다시 예약 1회
     */
    @Test
    fun test_10_alarmWithoutTriggerOnlyReschedules() = runTest {
        /** Given **/
        coEvery { repository.getById("a") } returns task("a")

        /** When **/
        sendAlarm("a", triggerAtMillis = null)
        awaitRescheduled()

        /** Then **/
        assertEquals(0, repositoryCreations)
        coVerify(exactly = 0) { repository.getById(any()) }
        assertEquals(0, notificationCount())
        assertEquals(1, rescheduleCount.get())
    }

    private companion object {
        /** 기준일 0시부터 15:00까지의 밀리초 */
        const val THREE_PM = 15 * 3_600_000L

        /** 기준일 0시부터 13:30까지의 밀리초 */
        const val ONE_THIRTY_PM = 13 * 3_600_000L + 30 * 60_000L
    }
}
