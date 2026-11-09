package com.black.app.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageTimerRepository]의 선택 앱 저장/조회와 일시정지 종료 시각 계산 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = UsageTimerRepository(context)

    /** 각 테스트 전 선택 앱 저장소 초기화(테스트 간 격리) */
    @Before
    fun clearSelectedApps() {
        runTest { repository.saveSelectedApps(emptyList()) }
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
}
