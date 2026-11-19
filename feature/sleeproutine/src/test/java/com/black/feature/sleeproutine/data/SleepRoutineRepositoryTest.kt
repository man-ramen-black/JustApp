package com.black.feature.sleeproutine.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.feature.sleeproutine.data.datastore.SleepRoutineDataStore
import com.black.feature.sleeproutine.model.SleepRoutineSettings
import com.black.test.BaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SleepRoutineRepository]의 설정 저장·조회와 진행 중 세션 종료 시각 저장·삭제 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore = SleepRoutineDataStore(context)
    private val repository = SleepRoutineRepository(context)

    /** DataStore가 프로세스 단일 인스턴스라 테스트 간 값이 섞이지 않도록 저장값 전체 삭제 */
    @Before
    fun resetBefore() {
        runTest { reset() }
    }

    @After
    fun resetAfter() {
        runTest { reset() }
    }

    private suspend fun reset() {
        dataStore.clear()
    }

    /**
     * 저장 전 기본 설정 조회 검증
     *
     * Given: 저장값을 전체 삭제해 키가 없는 상태
     * When: 설정 조회
     * Then: 기본값
     */
    @Test
    fun test_01_getSettingsReturnsDefaultsBeforeSave() = runTest {
        /** Given / When / Then **/
        assertEquals(SleepRoutineSettings(), repository.getSettings())
    }

    /**
     * 설정 저장 후 같은 값 조회 검증
     *
     * Given: 기본값과 다른 설정
     * When: 저장 후 조회
     * Then: 저장 성공, 같은 값 반환
     */
    @Test
    fun test_02_saveSettingsRoundTrip() = runTest {
        /** Given **/
        val settings = SleepRoutineSettings("https://youtu.be/abc", 30, false, 9)

        /** When **/
        val result = repository.saveSettings(settings)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(settings, repository.getSettings())
    }

    /**
     * 세션이 없을 때 종료 시각 검증
     *
     * Given: 시작한 세션 없음
     * When: 종료 시각 조회, Flow 수집
     * Then: 둘 다 null
     */
    @Test
    fun test_03_sessionEndTimeIsNullWithoutSession() = runTest {
        /** Given / When / Then **/
        assertNull(repository.getSessionEndTime())
        assertNull(repository.sessionEndTime.first())
    }

    /**
     * 세션 시작 후 종료 시각 검증
     *
     * Given: 종료 시각 1_000으로 세션 시작
     * When: 종료 시각 조회, Flow 수집
     * Then: 둘 다 1_000
     */
    @Test
    fun test_04_startSessionStoresEndTime() = runTest {
        /** Given **/
        repository.startSession(1_000L)

        /** When / Then **/
        assertEquals(1_000L, repository.getSessionEndTime())
        assertEquals(1_000L, repository.sessionEndTime.first())
    }

    /**
     * 세션 삭제 후 종료 시각 검증
     *
     * Given: 시작한 세션
     * When: 세션 삭제
     * Then: 종료 시각 조회·Flow 모두 null
     */
    @Test
    fun test_05_clearSessionRemovesEndTime() = runTest {
        /** Given **/
        repository.startSession(1_000L)

        /** When **/
        repository.clearSession()

        /** Then **/
        assertNull(repository.getSessionEndTime())
        assertNull(repository.sessionEndTime.first())
    }
}
