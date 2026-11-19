package com.black.feature.sleeproutine.service

import com.black.feature.sleeproutine.data.SleepRoutineRepository
import com.black.feature.sleeproutine.data.datastore.SleepRoutineDataStore
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
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

/** [SleepRoutineSessionRestorer]의 저장된 세션 유무에 따른 재개 시작 요청 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineSessionRestorerTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private val repository = SleepRoutineRepository(application)

    /** DataStore가 프로세스 단일 인스턴스라 다른 테스트 클래스가 남긴 설정·세션 삭제 */
    @Before
    fun clearDataStoreBefore() = runTest {
        SleepRoutineDataStore(application).clear()
    }

    /** 이 테스트가 남긴 세션 삭제 */
    @After
    fun clearDataStoreAfter() = runTest {
        SleepRoutineDataStore(application).clear()
    }

    /**
     * 저장된 세션이 있으면 서비스를 재개 인텐트로 시작하는지 검증
     *
     * Given: 종료 시각이 남은 세션 저장
     * When: restore 호출
     * Then: true, SleepRoutineService 시작 인텐트에 재개 표시
     */
    @Test
    fun test_01_savedSessionStartsResume() = runTest {
        /** Given **/
        repository.startSession(System.currentTimeMillis() + 60 * 60_000L)

        /** When **/
        val started = SleepRoutineSessionRestorer(application).restore()

        /** Then **/
        val startedService = requireNotNull(shadowOf(application).nextStartedService)
        assertTrue(started)
        assertEquals(SleepRoutineService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(SleepRoutineService.EXTRA_RESUME, false))
    }

    /**
     * 저장된 세션이 없으면 서비스를 시작하지 않는지 검증
     *
     * Given: 저장된 세션 없음
     * When: restore 호출
     * Then: false, 서비스 시작 인텐트 없음
     */
    @Test
    fun test_02_noSessionDoesNotStart() = runTest {
        /** Given **/
        // 저장된 세션 없음(DataStore 초기화 상태)

        /** When **/
        val started = SleepRoutineSessionRestorer(application).restore()

        /** Then **/
        assertFalse(started)
        assertNull(shadowOf(application).nextStartedService)
    }
}
