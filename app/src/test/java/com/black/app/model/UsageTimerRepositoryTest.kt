package com.black.app.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.datastore.UsageTimerDataStore
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageTimerRepository]의 선택 앱 저장/조회와 일시정지 종료 시각 계산, 일시정지 판정 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = UsageTimerRepository(context)

    /** 각 테스트 전 선택 앱 저장소 초기화(테스트 간 격리) */
    @Before
    fun clearSelectedApps() {
        runTest { repository.saveSelectedApps(emptyList()) }
    }

    /** 일시정지 종료 시각 초기화(DataStore가 프로세스 단일 인스턴스라 다른 테스트 클래스의 타이머가 일시정지로 판단하지 않도록) */
    @After
    fun clearPause() {
        runTest { repository.cancelPause() }
    }

    /**
     * 저장한 선택 앱 목록을 그대로 다시 읽어오는지 검증
     *
     * Given: 앱 두 개를 선택 목록으로 저장
     * When: 선택 목록 조회
     * Then: 저장한 순서·내용 그대로 반환
     */
    @Test
    fun test_01_saveAndGetSelectedAppsRoundTrip() = runTest {
        /** Given / When **/
        repository.saveSelectedApps(listOf("com.a", "com.b"))

        /** Then **/
        assertEquals(listOf("com.a", "com.b"), repository.getSelectedApps())
    }

    /**
     * 빈 목록 저장 시 빈 목록으로 조회되는지 검증
     *
     * Given: 빈 선택 목록 저장
     * When: 선택 목록 조회
     * Then: 빈 목록 반환(빈 문자열 split로 [""]가 되지 않음)
     */
    @Test
    fun test_02_getSelectedAppsReturnsEmptyWhenEmptySaved() = runTest {
        /** Given / When **/
        repository.saveSelectedApps(emptyList())

        /** Then **/
        assertEquals(emptyList<String>(), repository.getSelectedApps())
    }

    /**
     * 단일 앱 저장/조회 검증
     *
     * Given: 앱 한 개 저장
     * When: 선택 목록 조회
     * Then: 단일 항목 목록 반환
     */
    @Test
    fun test_03_saveSingleApp() = runTest {
        /** Given / When **/
        repository.saveSelectedApps(listOf("com.only"))

        /** Then **/
        assertEquals(listOf("com.only"), repository.getSelectedApps())
    }

    /**
     * Int 곱셈이면 넘치는 긴 일시정지 시간도 미래 시각으로 저장되는지 검증
     *
     * Given: 40,000분(Int 밀리초 곱셈이면 넘치는 값)
     * When: pause 호출
     * Then: 종료 시각이 현재 시각 + 40,000분 이상
     */
    @Test
    fun test_04_longPauseDurationDoesNotOverflow() = runTest {
        /** Given **/
        val minutes = 40_000
        val before = System.currentTimeMillis()

        /** When **/
        repository.pause(minutes)

        /** Then **/
        assertTrue(repository.getPauseEndTime() >= before + minutes * 60_000L)
    }

    /**
     * 일시정지 중이면 일시정지로 판정하고 남은 시간을 돌려주는지 검증
     *
     * Given: 5분 일시정지
     * When: 일시정지 여부, 남은 시간 조회
     * Then: 일시정지 true, 남은 시간 0 초과 5분 이하
     */
    @Test
    fun test_05_pausedWhileBeforeEndTime() = runTest {
        /** Given **/
        repository.pause(5)

        /** When **/
        val isPaused = repository.isPaused()
        val remainTimeMillis = repository.getPauseRemainTimeMillis()

        /** Then **/
        assertTrue(isPaused)
        assertTrue(remainTimeMillis in 1..5 * 60_000L)
    }

    /**
     * 일시정지를 해제하면 일시정지가 아니고 남은 시간 0인지 검증
     *
     * Given: 5분 일시정지 후 해제(종료 시각 0)
     * When: 일시정지 여부, 남은 시간 조회
     * Then: 일시정지 false, 남은 시간 0
     */
    @Test
    fun test_06_notPausedAfterCancel() = runTest {
        /** Given **/
        repository.pause(5)
        repository.cancelPause()

        /** When **/
        val isPaused = repository.isPaused()
        val remainTimeMillis = repository.getPauseRemainTimeMillis()

        /** Then **/
        assertFalse(isPaused)
        assertEquals(0L, remainTimeMillis)
    }

    /**
     * 종료 시각이 지나면 일시정지가 아니고 남은 시간 0인지 검증
     *
     * Given: 종료 시각을 1초 전으로 저장
     * When: 일시정지 여부, 남은 시간 조회
     * Then: 일시정지 false, 남은 시간 0(음수가 아님)
     */
    @Test
    fun test_07_notPausedAfterEndTime() = runTest {
        /** Given **/
        UsageTimerDataStore(context).updatePauseEndTime(System.currentTimeMillis() - 1_000L)

        /** When **/
        val isPaused = repository.isPaused()
        val remainTimeMillis = repository.getPauseRemainTimeMillis()

        /** Then **/
        assertFalse(isPaused)
        assertEquals(0L, remainTimeMillis)
    }
}
