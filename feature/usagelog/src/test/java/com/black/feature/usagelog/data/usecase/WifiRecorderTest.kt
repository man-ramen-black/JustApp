package com.black.feature.usagelog.data.usecase

import android.util.Log
import com.black.core.model.HomeTransition
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.SendReason
import com.black.feature.usagelog.model.WifiState
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.time.Duration.Companion.seconds

/**
 * [WifiRecorder]의 집 전환 처리(리스너 먼저 호출, 사유에 맞는 즉시 전송 예약, 한쪽 실패 뒤에도 나머지와 다음 전환 계속)와 세션 수명 주기 검증
 *
 * 이벤트 처리가 시간 초과로 실패해도 다음 이벤트를 처리하는지도 검증.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WifiRecorderTest : BaseTest() {

    /**
     * 수집 켜짐, 집 SSID 하나인 설정 저장소
     *
     * 소비 코루틴이 테스트 스케줄러 밖의 스레드로 넘어가지 않도록 목업하고, 마지막 Wi-Fi 상태는 [lastWifiState]에 저장함.
     */
    private val settings = mockk<UsageLogSettingsRepository>()
    private val repository = mockk<UsageLogRepository>()
    private var lastWifiState = WifiState(connected = false)

    /** 기록기 코루틴의 부모 Job, 테스트가 끝나면 취소 */
    private val recorderJob = Job()

    /** runTest와 같은 테스트 스케줄러에서 도는 기록기 scope */
    private val recorderScope = CoroutineScope(testDispatcherRule.testDispatcher + recorderJob)

    /** 리스너 호출과 전송 예약이 일어난 순서 */
    private val steps = mutableListOf<String>()

    @Before
    fun setupSettings() {
        lastWifiState = WifiState(connected = false)
        every { settings.collectionEnabled } returns MutableStateFlow(true)
        every { settings.homeSsids } returns MutableStateFlow(setOf(HOME))
        coEvery { settings.isCollectionEnabled() } returns true
        coEvery { settings.getHomeSsids() } returns setOf(HOME)
        coEvery { settings.getLastWifiState() } answers { lastWifiState }
        coEvery { settings.setLastWifiState(any()) } answers {
            lastWifiState = firstArg()
            Result.success(Unit)
        }
        coEvery { repository.add(any(), any()) } just Runs
    }

    @After
    fun cancelRecorder() {
        recorderJob.cancel()
    }

    private fun createRecorder(
        listener: HomeTransitionListener = HomeTransitionListener { steps += "listener ${it.arrived} ${it.time}" },
        scheduleSend: (SendReason) -> Unit = { steps += "send $it" },
    ) = WifiRecorder(settings, repository, recorderScope, listener, scheduleSend)

    /** 기록 세션을 열어 등록을 마친 뒤 집 Wi-Fi 연결(시각 1000)과 해제(시각 2000)를 보고 */
    private fun reportHomeArriveAndLeave(recorder: WifiRecorder) {
        val session = recorder.openSession(emptySet())
        session.onRegistered()
        session.onConnected(handle = 1L, ssid = HOME, time = 1000L)
        session.onLost(handle = 1L, time = 2000L)
    }

    /**
     * 집 전환마다 리스너를 먼저 부르고 이어서 사유에 맞는 전송을 예약하는지 검증
     *
     * Given: 수집 켜짐, 집 SSID 등록, 끊긴 상태로 저장된 기록기
     * When: 집 Wi-Fi 연결(1000) 뒤 해제(2000) 보고
     * Then: 도착 리스너, HOME_ARRIVE 예약, 출발 리스너, HOME_LEAVE 예약 순서
     */
    @Test
    fun test_01_listenerFirstThenSchedulesMatchingReason() = runTest {
        /** Given **/
        val transitions = mutableListOf<HomeTransition>()
        val recorder = createRecorder(
            listener = HomeTransitionListener { transition ->
                transitions += transition
                steps += "listener ${transition.arrived} ${transition.time}"
            },
        )

        /** When **/
        reportHomeArriveAndLeave(recorder)
        advanceUntilIdle()

        /** Then **/
        assertEquals(
            listOf(
                "listener true 1000",
                "send ${SendReason.HOME_ARRIVE}",
                "listener false 2000",
                "send ${SendReason.HOME_LEAVE}",
            ),
            steps,
        )
        assertEquals(
            listOf(
                HomeTransition(arrived = true, inferred = false, time = 1000L),
                HomeTransition(arrived = false, inferred = false, time = 2000L),
            ),
            transitions,
        )
    }

    /**
     * 전송 예약이 던져도 리스너 호출과 다음 전환의 처리가 계속되는지 검증
     *
     * Given: 전송 예약이 매번 IllegalStateException을 던지는 기록기
     * When: 집 Wi-Fi 연결(1000) 뒤 해제(2000) 보고
     * Then: 두 전환 모두 리스너와 전송 예약 시도, 실패는 클래스 이름으로 Log.w 기록, 마지막 상태는 끊김으로 저장
     */
    @Test
    fun test_02_scheduleFailureDoesNotBlockListenerOrNextTransition() = runTest {
        /** Given **/
        val recorder = createRecorder(
            scheduleSend = { reason ->
                steps += "send $reason"
                throw IllegalStateException()
            },
        )

        /** When **/
        reportHomeArriveAndLeave(recorder)
        advanceUntilIdle()

        /** Then **/
        assertEquals(
            listOf(
                "listener true 1000",
                "send ${SendReason.HOME_ARRIVE}",
                "listener false 2000",
                "send ${SendReason.HOME_LEAVE}",
            ),
            steps,
        )
        verify(exactly = 2) {
            Log.w(any<String>(), match<String> { it.contains("집 전환 전송 예약 실패: IllegalStateException") })
        }
        assertEquals(WifiState(connected = false), lastWifiState)
    }

    /**
     * 리스너가 던져도 그 전환의 전송 예약과 다음 전환의 처리가 계속되는지 검증
     *
     * Given: 리스너가 매번 IllegalStateException을 던지는 기록기
     * When: 집 Wi-Fi 연결(1000) 뒤 해제(2000) 보고
     * Then: 두 전환 모두 리스너와 전송 예약 시도, 실패는 클래스 이름으로 Log.w 기록
     */
    @Test
    fun test_03_listenerFailureDoesNotBlockSchedule() = runTest {
        /** Given **/
        val recorder = createRecorder(
            listener = HomeTransitionListener { transition ->
                steps += "listener ${transition.arrived} ${transition.time}"
                throw IllegalStateException()
            },
        )

        /** When **/
        reportHomeArriveAndLeave(recorder)
        advanceUntilIdle()

        /** Then **/
        assertEquals(
            listOf(
                "listener true 1000",
                "send ${SendReason.HOME_ARRIVE}",
                "listener false 2000",
                "send ${SendReason.HOME_LEAVE}",
            ),
            steps,
        )
        verify(exactly = 2) {
            Log.w(any<String>(), match<String> { it.contains("집 전환 알림 실패: IllegalStateException") })
        }
    }

    /**
     * 다시 연 세션의 소비 코루틴이 이전 세션의 소비 코루틴이 끝난 뒤에 처리를 시작하는지 검증
     *
     * Given: 첫 세션의 첫 기록 저장이 취소되지 않고 gate가 열릴 때까지 멈춰 있는 상태에서 첫 세션을 닫음
     * When: 두 번째 세션을 열어 등록한 뒤, gate를 열기 전과 후에 대기열을 비움
     * Then: gate를 열기 전에는 두 번째 세션이 저장소 상태를 읽지 않고, 연 뒤에는 첫 세션 저장이 끝난 다음에 읽음
     */
    @Test
    fun test_04_reopenedSessionWaitsForPreviousConsumer() = runTest {
        /** Given **/
        val gate = CompletableDeferred<Unit>()
        var isFirstSave = true
        coEvery { repository.add(any(), any()) } coAnswers {
            if (isFirstSave) {
                isFirstSave = false
                // 취소 중에도 끝까지 저장하는 이전 세션을 흉내 냄
                withContext(NonCancellable) { gate.await() }
                steps += "first session saved"
            }
        }
        coEvery { settings.isCollectionEnabled() } answers {
            steps += "read"
            true
        }
        val recorder = createRecorder()
        val firstSession = recorder.openSession(emptySet())
        firstSession.onRegistered()
        firstSession.onConnected(handle = 1L, ssid = HOME, time = 1000L)
        advanceUntilIdle()
        firstSession.onClosed()
        steps.clear()

        /** When **/
        val secondSession = recorder.openSession(emptySet())
        secondSession.onRegistered()
        advanceUntilIdle()
        val stepsBeforeGate = steps.toList()
        gate.complete(Unit)
        advanceUntilIdle()

        /** Then **/
        assertEquals(emptyList<String>(), stepsBeforeGate)
        assertEquals("first session saved", steps.first())
        assertTrue("read" in steps)
    }

    /**
     * 콜백 등록 실패로 onRegistered 없이 닫힌 세션은 보고를 처리하지 않고, 다음 세션은 기다림 없이 처리하는지 검증
     *
     * Given: 연 뒤 onRegistered 없이 닫은 세션
     * When: 닫힌 세션에 집 Wi-Fi 연결(500)을 보고하고, 새 세션을 열어 등록한 뒤 집 Wi-Fi 연결(1000) 보고
     * Then: 닫힌 세션의 보고(500)는 처리되지 않고, 새 세션의 보고(1000)만 도착 리스너와 HOME_ARRIVE 예약
     */
    @Test
    fun test_05_sessionClosedWithoutRegisteredIgnoresReports() = runTest {
        /** Given **/
        val recorder = createRecorder()
        val failedSession = recorder.openSession(emptySet())
        failedSession.onClosed()

        /** When **/
        failedSession.onConnected(handle = 1L, ssid = HOME, time = 500L)
        advanceUntilIdle()
        val session = recorder.openSession(emptySet())
        session.onRegistered()
        session.onConnected(handle = 1L, ssid = HOME, time = 1000L)
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf("listener true 1000", "send ${SendReason.HOME_ARRIVE}"), steps)
    }

    /**
     * 처리 안의 시간 초과로 한 건이 실패해도 다음 이벤트를 기록하는지 검증
     *
     * Given: 마지막 Wi-Fi 상태의 첫 읽기가 withTimeout의 시간 초과로 끝나는 설정 저장소
     * When: 집 Wi-Fi 연결(1000) 뒤 해제(2000) 보고
     * Then: 첫 이벤트(세션을 열 때 넣은 수집 켜짐)의 실패는 클래스 이름으로 Log.w 기록, 이어진 두 전환은 리스너 호출과 전송 예약까지 처리
     */
    @Test
    fun test_06_timeoutInsideProcessingDoesNotStopLaterEvents() = runTest {
        /** Given **/
        var isFirstRead = true
        coEvery { settings.getLastWifiState() } coAnswers {
            if (isFirstRead) {
                isFirstRead = false
                // 읽기 안에서 난 시간 초과라 소비 코루틴의 취소가 아님
                withTimeout(1.seconds) { awaitCancellation() }
            }
            lastWifiState
        }
        val recorder = createRecorder()

        /** When **/
        reportHomeArriveAndLeave(recorder)
        advanceUntilIdle()

        /** Then **/
        assertEquals(
            listOf(
                "listener true 1000",
                "send ${SendReason.HOME_ARRIVE}",
                "listener false 2000",
                "send ${SendReason.HOME_LEAVE}",
            ),
            steps,
        )
        verify(exactly = 1) {
            Log.w(any<String>(), match<String> { it.contains("Wi-Fi 이벤트 처리 실패: TimeoutCancellationException") })
        }
    }

    companion object {
        private const val HOME = "home-wifi"
    }
}
