package com.black.feature.todo.data.usecase

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.black.core.model.HomeTransition
import com.black.feature.todo.data.TodoSettingsRepository
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [TodoHomeArrivalUseCase]의 출발 시각 저장과 도착 작업 예약 조건, 처리 로그 생략 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoHomeArrivalUseCaseTest : BaseTest() {

    private val settings = mockk<TodoSettingsRepository>()
    private var enqueueCount = 0
    private val handler = TodoHomeArrivalUseCase(settings) { enqueueCount++ }

    /** 추정 출발 기록 없음을 기본 응답으로 지정 */
    @Before
    fun setupSettings() {
        coEvery { settings.getLastInferredHomeLeaveTime() } returns null
    }

    /**
     * 출발이면 시각을 저장하고 작업을 예약하지 않는지 검증
     *
     * Given: 출발 전환 time 5000
     * When: onHomeTransition
     * Then: saveLastHomeLeaveTime(5000) 한 번, saveLastInferredHomeLeaveTime 0회, 예약 0회
     */
    @Test
    fun test_01_leaveSavesTimeWithoutEnqueue() = runTest {
        /** Given **/
        coEvery { settings.saveLastHomeLeaveTime(any()) } returns Result.success(Unit)

        /** When **/
        handler.onHomeTransition(HomeTransition(false, false, 5000L))

        /** Then **/
        coVerify(exactly = 1) { settings.saveLastHomeLeaveTime(5000L) }
        coVerify(exactly = 0) { settings.saveLastInferredHomeLeaveTime(any()) }
        assertEquals(0, enqueueCount)
    }

    /**
     * 조건을 충족한 도착은 작업을 한 번 예약하는지 검증
     *
     * Given: 마지막 출발이 10분 전인 상태
     * When: 실제 도착
     * Then: 예약 1회
     */
    @Test
    fun test_02_arrivalAfterLongAwayEnqueues() = runTest {
        /** Given **/
        coEvery { settings.getLastHomeLeaveTime() } returns 1_000L

        /** When **/
        handler.onHomeTransition(HomeTransition(true, false, 1_000L + TodoHomeArrivalUseCase.MIN_AWAY_MILLIS))

        /** Then **/
        assertEquals(1, enqueueCount)
    }

    /**
     * 추정 도착과 짧은 외출 뒤 도착은 예약하지 않는지 검증
     *
     * Given: 마지막 출발 1분 전
     * When: 추정 도착, 짧은 외출 도착
     * Then: 예약 0회
     */
    @Test
    fun test_03_inferredOrShortAwayArrivalDoesNotEnqueue() = runTest {
        /** Given **/
        coEvery { settings.getLastHomeLeaveTime() } returns 1_000L

        /** When **/
        handler.onHomeTransition(HomeTransition(true, true, 1_000_000L))
        handler.onHomeTransition(HomeTransition(true, false, 61_000L))

        /** Then **/
        assertEquals(0, enqueueCount)
    }

    /**
     * 추정 출발은 마지막 출발 시각이 아닌 추정 출발 시각으로 저장하는지 검증
     *
     * Given: 추정 출발 전환 time 5000
     * When: onHomeTransition
     * Then: saveLastInferredHomeLeaveTime(5000) 한 번, saveLastHomeLeaveTime 0회, 예약 0회
     */
    @Test
    fun test_04_inferredLeaveSavedSeparately() = runTest {
        /** Given **/
        coEvery { settings.saveLastHomeLeaveTime(any()) } returns Result.success(Unit)
        coEvery { settings.saveLastInferredHomeLeaveTime(any()) } returns Result.success(Unit)

        /** When **/
        handler.onHomeTransition(HomeTransition(false, true, 5000L))

        /** Then **/
        coVerify(exactly = 1) { settings.saveLastInferredHomeLeaveTime(5000L) }
        coVerify(exactly = 0) { settings.saveLastHomeLeaveTime(any()) }
        assertEquals(0, enqueueCount)
    }

    /**
     * 추정 출발 직후의 실제 도착은 실제 출발 기록이 오래돼도 예약하지 않는지 검증
     *
     * Given: 마지막 실제 출발 8시간 전, 마지막 추정 출발 시각
     * When: 추정 출발 30초 뒤 실제 도착
     * Then: 예약 0회
     */
    @Test
    fun test_05_arrivalSoonAfterInferredLeaveDoesNotEnqueue() = runTest {
        /** Given **/
        val inferredLeave = 100_000_000L
        coEvery { settings.getLastHomeLeaveTime() } returns inferredLeave - 8 * 3_600_000L
        coEvery { settings.getLastInferredHomeLeaveTime() } returns inferredLeave

        /** When **/
        handler.onHomeTransition(HomeTransition(true, false, inferredLeave + 30_000L))

        /** Then **/
        assertEquals(0, enqueueCount)
    }

    /**
     * 출발·도착 처리가 실제 저장소를 거쳐도 v·d·i 로그를 남기지 않는지 검증
     *
     * Given: 실제 저장소를 쓰는 처리기
     * When: 추정 출발, 실제 출발, 추정 도착, 알림 조건을 충족하는 실제 도착을 차례로 처리
     * Then: 두 출발 시각 저장, 예약 1회, Log.v·d·i 호출 없음
     */
    @Test
    fun test_06_transitionsLeaveNoLog() = runTest {
        /** Given **/
        val storedSettings = TodoSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        val storedHandler = TodoHomeArrivalUseCase(storedSettings) { enqueueCount++ }
        val inferredLeaveTime = 1_000L
        val leaveTime = 1_000_000L

        /** When **/
        storedHandler.onHomeTransition(HomeTransition(false, true, inferredLeaveTime))
        storedHandler.onHomeTransition(HomeTransition(false, false, leaveTime))
        storedHandler.onHomeTransition(HomeTransition(true, true, leaveTime + 1_000L))
        storedHandler.onHomeTransition(HomeTransition(true, false, leaveTime + TodoHomeArrivalUseCase.MIN_AWAY_MILLIS))

        /** Then **/
        assertEquals(inferredLeaveTime, storedSettings.getLastInferredHomeLeaveTime())
        assertEquals(leaveTime, storedSettings.getLastHomeLeaveTime())
        assertEquals(1, enqueueCount)
        verify(exactly = 0) {
            Log.v(any(), any())
            Log.d(any(), any())
            Log.i(any(), any())
        }
    }
}
