package com.black.app.receiver

import android.content.Intent
import com.black.app.data.datastore.ForegroundServiceDataStore
import com.black.app.service.ForegroundService
import com.black.app.service.ForegroundServiceTestFixture
import com.black.feature.sleeproutine.data.SleepRoutineRepository
import com.black.feature.sleeproutine.data.datastore.SleepRoutineDataStore
import com.black.feature.sleeproutine.service.SleepRoutineService
import com.black.feature.todo.notification.TodoReminderRescheduler
import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** [AppUpdateReceiver]의 앱 업데이트 수신 후 ForegroundService 시작과 수면 루틴 세션 재개 요청, Todo 시각 알림 다시 예약과 그 순서 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUpdateReceiverTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private val dataStore = ForegroundServiceDataStore(application)
    private val originalAlarmRescheduler = ForegroundServiceStartReceiver.createAlarmRescheduler
    private val rescheduled = CompletableDeferred<Unit>()

    /** 실제 저장본을 읽지 않도록 다시 예약을 호출 기록으로 대체 */
    @Before
    fun replaceAlarmRescheduler() {
        ForegroundServiceStartReceiver.createAlarmRescheduler = { TodoReminderRescheduler { rescheduled.complete(Unit) } }
    }

    @After
    fun restoreAlarmRescheduler() {
        ForegroundServiceStartReceiver.createAlarmRescheduler = originalAlarmRescheduler
    }

    /** 프로세스 단일 DataStore의 실행 설정 초기화 */
    @After
    fun clearActivated() {
        ForegroundServiceTestFixture.resetActivated(application)
    }

    /** 프로세스 단일 DataStore의 수면 루틴 세션 삭제 */
    @After
    fun clearSleepRoutineDataStore() = runTest {
        SleepRoutineDataStore(application).clear()
    }

    /**
     * 앱 업데이트 action을 받으면 실행 설정이 켜진 ForegroundService 시작을 요청하는지 검증
     *
     * Given: 실행 설정 true, 배터리 최적화 예외
     * When: MY_PACKAGE_REPLACED action으로 AppUpdateReceiver를 지정한 브로드캐스트 전송
     * Then: ForegroundService 시작 인텐트 기록
     */
    @Test
    fun test_01_packageReplacedStartsActivatedService() = runTest {
        /** Given **/
        dataStore.updateActivated(true)
        ForegroundServiceTestFixture.setIgnoringBatteryOptimizations(application, true)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(application, AppUpdateReceiver::class.java))
        idleMainLooper()

        /** Then **/
        // 시작 확인은 goAsync로 이어 가는 Dispatchers.Default 범위에서 DataStore를 읽으므로 실제 시간으로 대기
        val startedService = ForegroundServiceTestFixture.awaitNextStartedService(application)
        assertEquals(ForegroundService::class.java.name, startedService.component?.className)
    }

    /**
     * 앱 업데이트 action을 받으면 저장된 수면 루틴 세션을 재개 인텐트로 시작하는지 검증
     *
     * Given: 실행 설정 false, 종료 시각이 남은 수면 루틴 세션 저장
     * When: MY_PACKAGE_REPLACED action으로 AppUpdateReceiver를 지정한 브로드캐스트 전송
     * Then: SleepRoutineService 시작 인텐트에 재개 표시
     */
    @Test
    fun test_02_packageReplacedResumesSleepRoutineSession() = runTest {
        /** Given **/
        dataStore.updateActivated(false)
        SleepRoutineRepository(application).startSession(System.currentTimeMillis() + 60 * 60_000L)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(application, AppUpdateReceiver::class.java))
        idleMainLooper()

        /** Then **/
        val startedService = ForegroundServiceTestFixture.awaitNextStartedService(application)
        assertEquals(SleepRoutineService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(SleepRoutineService.EXTRA_RESUME, false))
    }

    /**
     * 앱 업데이트 action을 받으면 Todo 시각 알림을 다시 예약하는지 검증
     *
     * Given: 실행 설정 false, 다시 예약 호출 기록
     * When: MY_PACKAGE_REPLACED action으로 AppUpdateReceiver를 지정한 브로드캐스트 전송
     * Then: 다시 예약 호출
     */
    @Test
    fun test_03_packageReplacedReschedulesAlarms() = runTest {
        /** Given **/
        dataStore.updateActivated(false)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(application, AppUpdateReceiver::class.java))
        idleMainLooper()

        /** Then **/
        // 다시 예약은 goAsync로 이어 가는 Dispatchers.Default 범위에서 불리므로 실제 시간으로 대기
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { rescheduled.await() } }
    }

    /**
     * 다시 예약이 수면 루틴 세션 재개와 ForegroundService 시작보다 먼저 불리는지 검증
     *
     * Given: 실행 설정 true, 배터리 최적화 예외, 종료 시각이 남은 수면 루틴 세션, 불릴 때 기록된 서비스 시작을 남기는 다시 예약
     * When: MY_PACKAGE_REPLACED action으로 AppUpdateReceiver를 지정한 브로드캐스트 전송
     * Then: 다시 예약 때 기록된 서비스 시작 없음, 이후 서비스 시작 기록
     */
    @Test
    fun test_04_reschedulesBeforeStartingServices() = runTest {
        /** Given **/
        dataStore.updateActivated(true)
        ForegroundServiceTestFixture.setIgnoringBatteryOptimizations(application, true)
        SleepRoutineRepository(application).startSession(System.currentTimeMillis() + 60 * 60_000L)
        val startedAtReschedule = CompletableDeferred<Intent?>()
        ForegroundServiceStartReceiver.createAlarmRescheduler = {
            TodoReminderRescheduler { startedAtReschedule.complete(shadowOf(application).peekNextStartedService()) }
        }

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(application, AppUpdateReceiver::class.java))
        idleMainLooper()

        /** Then **/
        // 다시 예약은 goAsync로 이어 가는 Dispatchers.Default 범위에서 불리므로 실제 시간으로 대기
        val startedIntent = withContext(Dispatchers.Default) { withTimeout(5.seconds) { startedAtReschedule.await() } }
        assertNull(startedIntent)
        ForegroundServiceTestFixture.awaitNextStartedService(application)
    }
}
