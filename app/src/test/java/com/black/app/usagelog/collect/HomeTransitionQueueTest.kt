package com.black.app.usagelog.collect

import android.util.Log
import com.black.test.BaseTest
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [HomeTransitionQueue]의 받은 순서대로 하나씩 처리, 실패와 내부 취소 예외 뒤 계속 처리, 닫은 뒤 멈춤 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HomeTransitionQueueTest : BaseTest() {

    /** 소비 코루틴의 부모 Job, 테스트가 끝나면 취소 */
    private val consumerJob = Job()

    /** runTest와 같은 테스트 스케줄러에서 도는 소비 scope */
    private val consumerScope = CoroutineScope(testDispatcherRule.testDispatcher + consumerJob)

    @After
    fun cancelConsumer() {
        consumerJob.cancel()
    }

    private fun leave(time: Long, inferred: Boolean = false) = HomeTransition(arrived = false, inferred = inferred, time = time)

    private fun arrive(time: Long) = HomeTransition(arrived = true, inferred = false, time = time)

    /**
     * 앞 전환의 처리가 끝난 뒤에 다음 전환을 받은 순서대로 처리하는지 검증
     *
     * Given: 처리마다 1초 걸리는 대기열
     * When: 추정 출발(1), 실제 도착(2), 실제 출발(3)을 연달아 넣음
     * Then: 1 시작·끝, 2 시작·끝, 3 시작·끝 순서
     */
    @Test
    fun test_01_handlesInReceivedOrderOneAtATime() = runTest {
        /** Given **/
        val steps = mutableListOf<String>()
        val queue = HomeTransitionQueue(consumerScope) { transition ->
            steps += "start ${transition.time}"
            delay(1_000)
            steps += "end ${transition.time}"
        }

        /** When **/
        queue.offer(leave(1L, inferred = true))
        queue.offer(arrive(2L))
        queue.offer(leave(3L))
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf("start 1", "end 1", "start 2", "end 2", "start 3", "end 3"), steps)
    }

    /**
     * 앞 전환의 처리 예외가 뒤 전환의 처리를 막지 않는지 검증
     *
     * Given: 시각 1의 처리만 IllegalStateException을 던지는 대기열
     * When: 출발(1), 도착(2)을 넣음
     * Then: 두 전환 모두 처리 시도, 실패는 클래스 이름으로 Log.w 기록
     */
    @Test
    fun test_02_failureDoesNotBlockNextTransition() = runTest {
        /** Given **/
        val handled = mutableListOf<HomeTransition>()
        val queue = HomeTransitionQueue(consumerScope) { transition ->
            handled += transition
            if (transition.time == 1L) throw IllegalStateException()
        }

        /** When **/
        queue.offer(leave(1L))
        queue.offer(arrive(2L))
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(leave(1L), arrive(2L)), handled)
        verify { Log.w(any<String>(), match<String> { it.contains("집 전환 처리 실패: IllegalStateException") }) }
    }

    /**
     * 닫으면 이미 받은 전환까지만 처리하고 소비 코루틴이 끝나는지 검증
     *
     * Given: 처리한 전환을 모으는 대기열
     * When: 출발(1)을 넣고 닫은 뒤 도착(2)을 넣음
     * Then: 출발(1)만 처리, 소비 코루틴이 끝나 남은 자식 코루틴 없음
     */
    @Test
    fun test_03_closeStopsConsumer() = runTest {
        /** Given **/
        val handled = mutableListOf<HomeTransition>()
        val queue = HomeTransitionQueue(consumerScope) { transition -> handled += transition }

        /** When **/
        queue.offer(leave(1L))
        queue.close()
        queue.offer(arrive(2L))
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(leave(1L)), handled)
        assertTrue(consumerJob.children.none())
    }

    /**
     * 처리 안의 시간 초과처럼 소비 코루틴 자신의 취소가 아닌 취소 예외가 뒤 전환의 처리를 막지 않는지 검증
     *
     * Given: 시각 1의 처리만 내부 withTimeout이 시간 초과로 끝나는 대기열
     * When: 출발(1), 도착(2)을 넣음
     * Then: 두 전환 모두 처리 시도, 시간 초과는 클래스 이름으로 Log.w 기록, 소비 코루틴이 남아 있음
     */
    @Test
    fun test_04_innerCancellationDoesNotStopConsumer() = runTest {
        /** Given **/
        val handled = mutableListOf<HomeTransition>()
        val queue = HomeTransitionQueue(consumerScope) { transition ->
            handled += transition
            if (transition.time == 1L) withTimeout(10) { delay(1_000) }
        }

        /** When **/
        queue.offer(leave(1L))
        queue.offer(arrive(2L))
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(leave(1L), arrive(2L)), handled)
        verify { Log.w(any<String>(), match<String> { it.contains("집 전환 처리 실패: TimeoutCancellationException") }) }
        assertTrue(consumerJob.children.any { it.isActive })
    }
}
