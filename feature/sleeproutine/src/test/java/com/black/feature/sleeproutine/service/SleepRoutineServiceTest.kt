package com.black.feature.sleeproutine.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.black.core.service.BaseForegroundService
import com.black.feature.sleeproutine.R
import com.black.feature.sleeproutine.data.SleepRoutineRepository
import com.black.feature.sleeproutine.data.datastore.SleepRoutineDataStore
import com.black.test.BaseTest
import com.black.test.TestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowToast
import kotlin.time.Duration.Companion.milliseconds

/**
 * [SleepRoutineService]의 시작·재개·중지·종료 경로별 마무리와 세션 정리 검증
 *
 * 서비스 코루틴은 테스트 스케줄러(Dispatchers.Main 대체)에서 돌고 DataStore 읽기·쓰기는 IO 스레드에서 끝나,
 * runTest 안에서 [SleepRoutineService.routineJob]·[SleepRoutineService.cleanupJob]을 join해 IO 완료와 이어지는 실행까지 기다림.
 * 앱 실행 단계는 [SleepRoutineRunnerTest]가 담당하므로 URL 없음·재개처럼 실행 단계를 거치지 않는 경로만 구성
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineServiceTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private val repository = SleepRoutineRepository(application)
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var serviceController: ServiceController<SleepRoutineService>? = null

    /** DataStore가 프로세스 단일 인스턴스라 다른 테스트 클래스가 남긴 설정·세션 삭제 */
    @Before
    fun clearDataStoreBefore() = runTest {
        SleepRoutineDataStore(application).clear()
    }

    /** 서비스 종료와 남은 정리 대기 후 이 테스트가 남긴 설정·세션 삭제 */
    @After
    fun destroyServiceAndClearDataStore() = runTest {
        serviceController?.let { destroyService(it) }
        SleepRoutineDataStore(application).clear()
    }

    /**
     * 서비스 종료 후 정리 코루틴 완료 대기
     *
     * runTest는 본문이 끝나면 테스트 스케줄러에 남은 작업을 모두 실행하고, 종료 시각까지 기다리는 루틴 코루틴은 실제 시각이 지나지 않아 가상 시간 대기를 끝없이 반복함.
     * 루틴을 진행 중으로 둔 테스트는 본문 안에서 이 함수로 종료
     */
    private suspend fun destroyService(controller: ServiceController<SleepRoutineService>) {
        controller.destroy()
        serviceController = null
        controller.get().cleanupJob?.join()
    }

    /**
     * [intent]로 서비스 생성 후 시작 명령 전달
     *
     * 인텐트가 null이면 START_STICKY 재시작으로 처리하므로 시작·중지 인텐트를 직접 지정.
     */
    private fun startService(intent: Intent): ServiceController<SleepRoutineService> =
        Robolectric.buildService(SleepRoutineService::class.java, intent)
            .create()
            .startCommand(0, 1)
            .also { serviceController = it }

    /** START_STICKY 재생성처럼 빈 인텐트로 서비스 생성 후 시작 명령 전달 */
    private fun startServiceWithoutIntent(): ServiceController<SleepRoutineService> =
        Robolectric.buildService(SleepRoutineService::class.java)
            .create()
            .also { it.get().onStartCommand(null, 0, 1) }
            .also { serviceController = it }

    /** 방해 금지 접근 허용 후 우선순위만 허용으로 켜 둠 */
    private fun enableDoNotDisturb() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    /**
     * 루틴 코루틴이 세션을 읽고 진행 단계에 들어갈 때까지 실제 시간으로 최대 5초 대기
     *
     * 세션 읽기가 테스트 스케줄러 밖(IO)에서 끝나 가상 시간 진행으로는 기다릴 수 없음.
     * withTimeout은 가상 시간이라 횟수로 제한하고, 상태는 서비스 코루틴과 같은 테스트 스레드에서 확인
     */
    private suspend fun awaitRoutineStarted(service: SleepRoutineService) {
        repeat(500) {
            if (service.routineStarted) {
                return
            }
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
        fail("routine did not start")
    }

    /**
     * YouTube URL이 설정되지 않았으면 안내 토스트 후 세션 없이 스스로 종료하고, 종료 뒤 다시 시작하지 않는지 검증
     *
     * Given: 기본 설정(YouTube URL 비어 있음)
     * When: 시작 인텐트로 서비스 시작 후 루틴 코루틴 완료 대기, 서비스 종료
     * Then: URL 설정 안내 토스트, stopSelf 호출, 저장된 세션 없음, 서비스 시작 인텐트 없음(기본 재시작 끔)
     */
    @Test
    fun test_01_missingUrlShowsGuideAndStops() = runTest {
        /** Given **/
        val startIntent = Intent(application, SleepRoutineService::class.java)

        /** When **/
        val controller = startService(startIntent)
        requireNotNull(controller.get().routineJob).join()
        destroyService(controller)

        /** Then **/
        assertEquals(application.getString(R.string.sleep_routine_url_required), ShadowToast.getTextOfLatestToast())
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(repository.getSessionEndTime())
        assertNull(shadowOf(application).nextStartedService)
    }

    /**
     * 중지 인텐트만 받으면 루틴 없이 멈추고 종료 뒤 다시 시작하지 않는지 검증
     *
     * Given: 실행 중이 아닌 서비스, 저장된 세션 없음
     * When: 중지 인텐트로 서비스 시작 후 정리 코루틴 완료 대기, 서비스 종료
     * Then: stopSelf 호출, 루틴 코루틴 없음, 서비스 시작 인텐트 없음, running false
     */
    @Test
    fun test_02_stopIntentStopsWithoutRestart() = runTest {
        /** Given **/
        val stopIntent = BaseForegroundService.createStopIntent(application, SleepRoutineService::class.java)

        /** When **/
        val controller = startService(stopIntent)
        controller.get().cleanupJob?.join()
        destroyService(controller)

        /** Then **/
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(controller.get().routineJob)
        assertNull(shadowOf(application).nextStartedService)
        assertFalse(SleepRoutineService.running.value)
    }

    /**
     * 프로세스 정리 뒤 재시작 전에 중지 인텐트를 받으면 저장된 세션 기준으로 마무리하는지 검증
     *
     * Given: 방해 금지 접근 허용·우선순위만 허용, 종료 시각이 남은 세션 저장
     * When: 중지 인텐트로 서비스 시작 후 정리 코루틴 완료 대기
     * Then: 방해 금지 꺼짐(모두 허용), 세션 삭제, 루틴 코루틴 없음
     */
    @Test
    fun test_03_stopIntentFinishesSavedSession() = runTest {
        /** Given **/
        enableDoNotDisturb()
        repository.startSession(System.currentTimeMillis() + 60 * 60_000L)
        val stopIntent = BaseForegroundService.createStopIntent(application, SleepRoutineService::class.java)

        /** When **/
        val controller = startService(stopIntent)
        requireNotNull(controller.get().cleanupJob).join()

        /** Then **/
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
        assertNull(repository.getSessionEndTime())
        assertNull(controller.get().routineJob)
    }

    /**
     * 빈 인텐트(START_STICKY 재생성)인데 저장된 세션이 없으면 루틴 없이 종료하는지 검증
     *
     * Given: 저장된 세션 없음
     * When: 빈 인텐트로 서비스 시작 후 루틴 코루틴 완료 대기
     * Then: stopSelf 호출, 루틴 진행 안 함, 토스트 없음
     */
    @Test
    fun test_04_resumeWithoutSessionStops() = runTest {
        /** Given **/
        // 저장된 세션 없음(DataStore 초기화 상태)

        /** When **/
        val controller = startServiceWithoutIntent()
        requireNotNull(controller.get().routineJob).join()

        /** Then **/
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertFalse(controller.get().routineStarted)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    /**
     * 빈 인텐트(START_STICKY 재생성)로 종료 시각이 지난 세션을 재개하면 바로 마무리하는지 검증
     *
     * Given: 방해 금지 접근 허용·우선순위만 허용, 종료 시각이 1분 지난 세션 저장
     * When: 빈 인텐트로 서비스 시작 후 루틴 코루틴 완료 대기
     * Then: 방해 금지 꺼짐(모두 허용), 세션 삭제, stopSelf 호출
     */
    @Test
    fun test_05_resumePastSessionFinishes() = runTest {
        /** Given **/
        enableDoNotDisturb()
        repository.startSession(System.currentTimeMillis() - 60_000L)

        /** When **/
        val controller = startServiceWithoutIntent()
        requireNotNull(controller.get().routineJob).join()

        /** Then **/
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
        assertNull(repository.getSessionEndTime())
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    /**
     * 루틴 코루틴이 진행 중일 때 다시 시작 요청이 오면 이미 진행 중 토스트만 띄우는지 검증
     *
     * Given: 시작 인텐트로 서비스를 시작해 루틴 코루틴이 아직 진행 중
     * When: 같은 시작 인텐트로 시작 명령 다시 전달, 루틴 코루틴 상태 기록 후 서비스 종료
     * Then: 이미 진행 중 토스트 1개, 루틴 코루틴 그대로 유지
     */
    @Test
    fun test_06_startWhileRunningShowsAlreadyRunning() = runTest {
        /** Given **/
        val startIntent = Intent(application, SleepRoutineService::class.java)
        val controller = startService(startIntent)
        val routineJob = requireNotNull(controller.get().routineJob)

        /** When **/
        // 테스트 본문이 멈추지 않아 첫 루틴 코루틴은 아직 실행 전이고 진행 중 상태
        controller.startCommand(0, 2)
        val routineJobAfterRestart = controller.get().routineJob
        val routineActive = routineJob.isActive
        destroyService(controller)

        /** Then **/
        assertEquals(application.getString(R.string.sleep_routine_already_running), ShadowToast.getTextOfLatestToast())
        assertEquals(1, ShadowToast.shownToastCount())
        assertSame(routineJob, routineJobAfterRestart)
        assertTrue(routineActive)
    }

    /**
     * 중지 인텐트 없이 서비스가 종료(stopService 등)되면 진행 중인 루틴을 마무리하는지 검증
     *
     * Given: 방해 금지 접근 허용·우선순위만 허용, 종료 시각이 1시간 남은 세션을 빈 인텐트로 재개해 진행 중
     * When: 서비스 종료(onDestroy만 호출) 후 정리 코루틴 완료 대기
     * Then: 방해 금지 꺼짐(모두 허용), 세션 삭제, 루틴 코루틴 취소
     */
    @Test
    fun test_07_destroyWithoutStopIntentFinishesRoutine() = runTest {
        /** Given **/
        enableDoNotDisturb()
        repository.startSession(System.currentTimeMillis() + 60 * 60_000L)
        val controller = startServiceWithoutIntent()
        awaitRoutineStarted(controller.get())

        /** When **/
        destroyService(controller)

        /** Then **/
        assertNotNull(controller.get().cleanupJob)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
        assertNull(repository.getSessionEndTime())
        assertFalse(requireNotNull(controller.get().routineJob).isActive)
    }

    /**
     * 종료 시각이 남은 세션이 있을 때 바로가기로 시작하면 새로 시작하지 않고 이미 진행 중 안내 후 재개하는지 검증
     *
     * Given: 기본 설정(YouTube URL 비어 있음), 종료 시각이 1시간 남은 세션 저장
     * When: 시작 인텐트로 서비스 시작 후 진행 단계 진입 대기, 세션·루틴 상태 기록 후 서비스 종료
     * Then: 이미 진행 중 토스트(URL 안내 아님), 저장된 종료 시각 유지, 루틴 진행 중이었음
     */
    @Test
    fun test_08_startWithPendingSessionResumes() = runTest {
        /** Given **/
        val endTimeMillis = System.currentTimeMillis() + 60 * 60_000L
        repository.startSession(endTimeMillis)
        val startIntent = Intent(application, SleepRoutineService::class.java)

        /** When **/
        val controller = startService(startIntent)
        awaitRoutineStarted(controller.get())
        val savedEndTimeMillis = repository.getSessionEndTime()
        val routineActive = requireNotNull(controller.get().routineJob).isActive
        // 단언이 실패해도 진행 중인 루틴이 남지 않도록 확인 전에 종료
        destroyService(controller)

        /** Then **/
        assertEquals(application.getString(R.string.sleep_routine_already_running), ShadowToast.getTextOfLatestToast())
        assertEquals(endTimeMillis, savedEndTimeMillis)
        assertTrue(routineActive)
    }

    /**
     * 포그라운드 알림을 누르면 앱 시작 액티비티가 열리는지 검증
     *
     * Given: 이 앱에 MAIN·LAUNCHER 액티비티 추가(기능 모듈 매니페스트에는 앱 시작 액티비티가 없음)
     * When: 중지 인텐트로 서비스 시작해 포그라운드 알림을 읽은 뒤 정리 코루틴 완료 대기, 서비스 종료
     * Then: 포그라운드 알림의 contentIntent가 추가한 시작 액티비티를 여는 액티비티 PendingIntent
     */
    @Test
    fun test_09_notificationOpensAppLauncherActivity() = runTest {
        /** Given **/
        TestUtil.addLauncherActivity(application, application.packageName, LAUNCHER_ACTIVITY)
        val stopIntent = BaseForegroundService.createStopIntent(application, SleepRoutineService::class.java)

        /** When **/
        val controller = startService(stopIntent)
        // 중지하면 포그라운드 알림이 지워지므로 그 전에 읽음
        val notification = shadowOf(controller.get()).lastForegroundNotification
        controller.get().cleanupJob?.join()
        destroyService(controller)

        /** Then **/
        val contentIntent = requireNotNull(notification?.contentIntent)
        val shadowContentIntent = shadowOf(contentIntent)
        assertTrue(shadowContentIntent.isActivityIntent)
        assertEquals(ComponentName(application.packageName, LAUNCHER_ACTIVITY), shadowContentIntent.savedIntent.component)
    }

    private companion object {
        const val LAUNCHER_ACTIVITY = "com.black.app.MainActivity"
    }
}
