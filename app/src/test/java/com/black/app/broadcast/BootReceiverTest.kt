package com.black.app.broadcast

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.black.app.assistanttask.alarm.AssistantTaskAlarmRescheduler
import com.black.app.model.SleepRoutineRepository
import com.black.app.model.datastore.SleepRoutineDataStore
import com.black.app.service.ForegroundServiceTestFixture
import com.black.app.sleeproutine.SleepRoutineService
import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/** [BootReceiver]의 action 확인 동작과 수면 루틴 세션 재개 요청, Todo 시각 알림 다시 예약 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BootReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val originalAlarmRescheduler = ForegroundServiceStartReceiver.createAlarmRescheduler
    private val rescheduled = CompletableDeferred<Unit>()

    /** 알림 권한 허용 */
    @Before
    fun setupNotificationPermission() {
        PackageInstallTestFixture.grantNotificationPermission(context)
    }

    /** 실제 저장본을 읽지 않도록 다시 예약을 호출 기록으로 대체 */
    @Before
    fun replaceAlarmRescheduler() {
        ForegroundServiceStartReceiver.createAlarmRescheduler = { AssistantTaskAlarmRescheduler { rescheduled.complete(Unit) } }
    }

    @After
    fun restoreAlarmRescheduler() {
        ForegroundServiceStartReceiver.createAlarmRescheduler = originalAlarmRescheduler
    }

    /** 프로세스 단일 DataStore의 수면 루틴 세션 삭제 */
    @After
    fun clearSleepRoutineDataStore() = runTest {
        SleepRoutineDataStore(context).clear()
    }

    /**
     * 부팅 완료가 아닌 action은 처리하지 않는지 검증
     *
     * Given: 알림 없음
     * When: 다른 action으로 BootReceiver를 지정한 명시적 브로드캐스트 전송
     * Then: 부팅 알림이 표시되지 않음
     */
    @Test
    fun test_01_otherActionIsIgnored() = runTest {
        /** Given **/
        // 알림 권한만 허용(별도 준비 없음)

        /** When **/
        context.sendBroadcast(Intent("com.other.ACTION").setClass(context, BootReceiver::class.java))
        idleMainLooper()

        /** Then **/
        assertEquals(0, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 부팅 완료 action은 부팅 알림을 표시하는지 검증
     *
     * Given: 알림 없음
     * When: BOOT_COMPLETED action으로 BootReceiver를 지정한 브로드캐스트 전송
     * Then: 부팅 알림 1개 표시
     */
    @Test
    fun test_02_bootCompletedShowsNotification() = runTest {
        /** Given **/
        // 알림 권한만 허용(별도 준비 없음)

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(context, BootReceiver::class.java))
        idleMainLooper()

        /** Then **/
        assertEquals(1, PackageInstallTestFixture.shadowNotificationManager(context).size())
    }

    /**
     * 부팅 완료 action을 받으면 저장된 수면 루틴 세션을 재개 인텐트로 시작하는지 검증
     *
     * Given: 종료 시각이 남은 수면 루틴 세션 저장
     * When: BOOT_COMPLETED action으로 BootReceiver를 지정한 브로드캐스트 전송
     * Then: SleepRoutineService 시작 인텐트에 재개 표시
     */
    @Test
    fun test_03_bootCompletedResumesSleepRoutineSession() = runTest {
        /** Given **/
        SleepRoutineRepository(context).startSession(System.currentTimeMillis() + 60 * 60_000L)

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(context, BootReceiver::class.java))
        idleMainLooper()

        /** Then **/
        // 세션 확인은 goAsync로 이어 가는 Dispatchers.Default 범위에서 DataStore를 읽으므로 실제 시간으로 대기
        val startedService = ForegroundServiceTestFixture.awaitNextStartedService(context)
        assertEquals(SleepRoutineService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(SleepRoutineService.EXTRA_RESUME, false))
    }

    /**
     * 부팅 완료 action을 받으면 Todo 시각 알림을 다시 예약하는지 검증
     *
     * Given: 다시 예약 호출 기록
     * When: BOOT_COMPLETED action으로 BootReceiver를 지정한 브로드캐스트 전송
     * Then: 다시 예약 호출
     */
    @Test
    fun test_04_bootCompletedReschedulesAlarms() = runTest {
        /** Given **/
        // 다시 예약 대체는 @Before에서 처리

        /** When **/
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(context, BootReceiver::class.java))
        idleMainLooper()

        /** Then **/
        // 다시 예약은 goAsync로 이어 가는 Dispatchers.Default 범위에서 불리므로 실제 시간으로 대기
        withContext(Dispatchers.Default) { withTimeout(5_000) { rescheduled.await() } }
    }
}
