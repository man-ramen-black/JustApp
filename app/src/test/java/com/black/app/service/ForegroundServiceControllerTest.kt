package com.black.app.service

import android.app.Application
import android.os.PowerManager
import com.black.app.model.datastore.ForegroundServiceDataStore
import com.black.app.service.ForegroundServiceController.StartResult
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [ForegroundServiceController]의 실행 설정 저장과 시작 결과, 서비스 시작·중지 요청 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForegroundServiceControllerTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val dataStore = ForegroundServiceDataStore(application)
    private val controller = ForegroundServiceController(application)

    /** 각 테스트 전 실행 설정 초기화(실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유) */
    @Before
    fun clearActivated() {
        runTest { dataStore.updateActivated(false) }
    }

    /** 배터리 최적화 예외 여부 지정 */
    private fun setIgnoringBatteryOptimizations(ignored: Boolean) {
        val powerManager = application.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).setIgnoringBatteryOptimizations(application.packageName, ignored)
    }

    /**
     * 배터리 최적화 예외 상태에서 켜면 설정 저장 후 서비스를 시작하는지 검증
     *
     * Given: 실행 설정 false, 배터리 최적화 예외
     * When: activate 호출
     * Then: Started 반환, 실행 설정 true(값·Flow), ForegroundService 시작 인텐트
     */
    @Test
    fun test_01_activateStartsServiceWhenBatteryOptimizationIgnored() = runTest {
        /** Given **/
        setIgnoringBatteryOptimizations(true)

        /** When **/
        val result = controller.activate()

        /** Then **/
        assertEquals(StartResult.Started, result)
        assertTrue(dataStore.getActivated())
        assertTrue(controller.activated.first())
        assertEquals(ForegroundService::class.java.name, shadowOf(application).nextStartedService.component?.className)
    }

    /**
     * 배터리 최적화 예외가 없으면 설정만 저장하고 서비스를 시작하지 않는지 검증
     *
     * Given: 실행 설정 false, 배터리 최적화 예외 아님
     * When: activate 호출
     * Then: BatteryOptimizationRequired 반환, 실행 설정 true, 시작 인텐트·화면 이동 없음
     */
    @Test
    fun test_02_activateReturnsBatteryOptimizationRequired() = runTest {
        /** Given **/
        setIgnoringBatteryOptimizations(false)

        /** When **/
        val result = controller.activate()

        /** Then **/
        assertEquals(StartResult.BatteryOptimizationRequired, result)
        assertTrue(dataStore.getActivated())
        assertNull(shadowOf(application).nextStartedService)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    /**
     * 끄면 설정 저장 후 서비스를 생성하지 않는 stopService로 중지하는지 검증
     *
     * Given: 실행 설정 true
     * When: deactivate 호출
     * Then: 실행 설정 false, ForegroundService 중지 인텐트, 시작 인텐트 없음
     */
    @Test
    fun test_03_deactivateStopsServiceWithoutStartIntent() = runTest {
        /** Given **/
        dataStore.updateActivated(true)

        /** When **/
        controller.deactivate()

        /** Then **/
        assertFalse(dataStore.getActivated())
        assertEquals(ForegroundService::class.java.name, shadowOf(application).nextStoppedService.component?.className)
        assertNull(shadowOf(application).nextStartedService)
    }

    /**
     * 실행 설정이 꺼져 있으면 배터리 최적화 예외여도 시작하지 않는지 검증
     *
     * Given: 실행 설정 false, 배터리 최적화 예외
     * When: startIfActivated 호출
     * Then: NotActivated 반환, 시작 인텐트 없음
     */
    @Test
    fun test_04_startIfActivatedSkipsWhenNotActivated() = runTest {
        /** Given **/
        setIgnoringBatteryOptimizations(true)

        /** When **/
        val result = controller.startIfActivated()

        /** Then **/
        assertEquals(StartResult.NotActivated, result)
        assertNull(shadowOf(application).nextStartedService)
    }
}
