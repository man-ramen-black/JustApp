package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [SerialEventQueue]의 받은 순서대로 하나씩 처리, 실패와 자기 취소가 아닌 취소 예외 뒤 계속 처리, 취소 뒤 멈춤, 닫은 뒤 멈춤 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SerialEventQueueTest : BaseTest() {

    /** 소비 코루틴의 부모 Job, 테스트가 끝나면 취소 */
    private val consumerJob = Job()

    /**
     * runTest와 같은 테스트 스케줄러에서 도는 소비 scope
     *
     * backgroundScope의 작업은 advanceUntilIdle이 기다리지 않아 쓰지 않음.
     */
    private val consumerScope = CoroutineScope(testDispatcherRule.testDispatcher + consumerJob)

    @After
    fun cancelConsumer() {
        consumerJob.cancel()
    }

    /**
     * 앞 이벤트의 처리가 끝난 뒤에 다음 이벤트를 받은 순서대로 처리하는지 검증
     *
     * Given: 처리마다 1초 걸리는 대기열의 소비 코루틴
     * When: 1, 2, 3을 연달아 넣음
     * Then: 1 시작·끝, 2 시작·끝, 3 시작·끝 순서
     */
    @Test
    fun test_01_handlesInReceivedOrderOneAtATime() = runTest {
        /** Given **/
        val steps = mutableListOf<String>()
        val queue = SerialEventQueue<Int>()
        consumerScope.launch {
            queue.consume(onFailure = {}) { event ->
                steps += "start $event"
                delay(1.seconds)
                steps += "end $event"
            }
        }

        /** When **/
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf("start 1", "end 1", "start 2", "end 2", "start 3", "end 3"), steps)
    }

    /**
     * 앞 이벤트의 처리 예외를 onFailure에 넘기고 뒤 이벤트의 처리를 막지 않는지 검증
     *
     * Given: 1의 처리만 IllegalStateException을 던지는 대기열의 소비 코루틴
     * When: 1, 2를 넣음
     * Then: 두 이벤트 모두 처리 시도, onFailure는 IllegalStateException으로 한 번 호출
     */
    @Test
    fun test_02_failureDoesNotBlockNextEvent() = runTest {
        /** Given **/
        val handled = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val queue = SerialEventQueue<Int>()
        consumerScope.launch {
            queue.consume(onFailure = { failures += it }) { event ->
                handled += event
                if (event == 1) {
                    throw IllegalStateException()
                }
            }
        }

        /** When **/
        queue.offer(1)
        queue.offer(2)
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(1, 2), handled)
        assertEquals(1, failures.size)
        assertTrue(failures.single() is IllegalStateException)
    }

    /**
     * 처리 안의 시간 초과처럼 소비 코루틴 자신의 취소가 아닌 취소 예외가 실패로 넘어가고 뒤 이벤트의 처리를 막지 않는지 검증
     *
     * Given: 1의 처리만 안쪽 withTimeout이 시간 초과로 끝나는 대기열의 소비 코루틴
     * When: 1, 2를 넣음
     * Then: 두 이벤트 모두 처리 시도, onFailure는 TimeoutCancellationException으로 한 번 호출, 소비 코루틴이 남아 있음
     */
    @Test
    fun test_03_innerCancellationDoesNotStopConsumer() = runTest {
        /** Given **/
        val handled = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val queue = SerialEventQueue<Int>()
        val consumer = consumerScope.launch {
            queue.consume(onFailure = { failures += it }) { event ->
                handled += event
                if (event == 1) {
                    withTimeout(10.milliseconds) { delay(1.seconds) }
                }
            }
        }

        /** When **/
        queue.offer(1)
        queue.offer(2)
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(1, 2), handled)
        assertEquals(1, failures.size)
        assertTrue(failures.single() is TimeoutCancellationException)
        assertTrue(consumer.isActive)
    }

    /**
     * 처리 중에 소비 코루틴이 취소되면 실패로 넘기지 않고 남은 이벤트를 처리하지 않는지 검증
     *
     * Given: 처리마다 1초 걸리는 대기열의 소비 코루틴, 1과 2를 넣고 1의 처리 시작
     * When: 소비 코루틴 취소
     * Then: 1만 처리 시작, onFailure 미호출, 소비 코루틴은 취소로 끝남
     */
    @Test
    fun test_04_cancelledWhileHandlingSkipsRemaining() = runTest {
        /** Given **/
        val started = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val queue = SerialEventQueue<Int>()
        val consumer = consumerScope.launch {
            queue.consume(onFailure = { failures += it }) { event ->
                started += event
                delay(1.seconds)
            }
        }
        queue.offer(1)
        queue.offer(2)
        // 1의 처리가 대기에 들어갈 때까지 실행
        runCurrent()

        /** When **/
        consumer.cancel()
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(1), started)
        assertTrue(failures.isEmpty())
        assertTrue(consumer.isCancelled)
    }

    /**
     * 처리가 대기 없이 끝난 사이에 소비 코루틴이 취소돼도 이미 받아 둔 다음 이벤트를 처리하지 않는지 검증
     *
     * Given: 1의 처리가 대기 없이 소비 코루틴 자신을 취소하는 대기열의 소비 코루틴
     * When: 1, 2를 넣음
     * Then: 1만 처리, onFailure 미호출, 소비 코루틴은 취소로 끝남
     */
    @Test
    fun test_05_cancelledBetweenEventsSkipsRemaining() = runTest {
        /** Given **/
        val handled = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val queue = SerialEventQueue<Int>()
        val consumer = consumerScope.launch {
            queue.consume(onFailure = { failures += it }) { event ->
                handled += event
                if (event == 1) {
                    currentCoroutineContext().cancel()
                }
            }
        }

        /** When **/
        queue.offer(1)
        queue.offer(2)
        advanceUntilIdle()

        /** Then **/
        assertEquals(listOf(1), handled)
        assertTrue(failures.isEmpty())
        assertTrue(consumer.isCancelled)
    }

    /**
     * 닫으면 새 이벤트를 버리고 이미 받은 이벤트까지 처리한 뒤 consume이 끝나는지 검증
     *
     * Given: 처리한 이벤트를 모으는 대기열의 소비 코루틴
     * When: 1을 넣고 닫은 뒤 2를 넣음
     * Then: 1은 넣기 성공·2는 실패, 1만 처리, 소비 코루틴은 취소 없이 끝남
     */
    @Test
    fun test_06_closeStopsAfterReceivedEvents() = runTest {
        /** Given **/
        val handled = mutableListOf<Int>()
        val queue = SerialEventQueue<Int>()
        val consumer = consumerScope.launch {
            queue.consume(onFailure = {}) { event -> handled += event }
        }

        /** When **/
        val firstOffered = queue.offer(1)
        queue.close()
        val secondOffered = queue.offer(2)
        advanceUntilIdle()

        /** Then **/
        assertTrue(firstOffered)
        assertFalse(secondOffered)
        assertEquals(listOf(1), handled)
        assertTrue(consumer.isCompleted)
        assertFalse(consumer.isCancelled)
    }
}
