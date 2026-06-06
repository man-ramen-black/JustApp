package com.black.core.viewmodel

import com.black.test.BaseTest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EventViewModel]의 타입 이벤트 전송·구독 동작 검증
 */
class EventViewModelTest : BaseTest() {

    /** 테스트용 이벤트 */
    private sealed class TestEvent : ViewModelEvent {
        data object EventFirst : TestEvent()
        data object EventSecond : TestEvent()
        data class EventValue(val value: Int) : TestEvent()
    }

    /** 테스트 대상 ViewModel */
    private class TestEventViewModel : EventViewModel()

    /**
     * 구독 전 보낸 이벤트가 누적되고 첫 구독 시 순서대로 수신되는지 검증
     *
     * Given: 구독자 없이 EventFirst, EventValue(1) 전송
     * When: events 첫 구독 후 2개 수집
     * Then: [EventFirst, EventValue(1)] 순서 그대로 수신
     */
    @Test
    fun test_01_eventsBeforeSubscriptionBufferedInOrder() = runTest {
        /** Given **/
        val viewModel = TestEventViewModel()
        viewModel.sendEvent(TestEvent.EventFirst)
        viewModel.sendEvent(TestEvent.EventValue(1))
        waitCoroutine()

        /** When **/
        val received = mutableListOf<ViewModelEvent>()
        viewModel.events.take(2).toList(received)

        /** Then **/
        assertEquals(listOf(TestEvent.EventFirst, TestEvent.EventValue(1)), received)
    }

    /**
     * 구독 중 보낸 이벤트가 즉시 수신되는지 검증
     *
     * Given: events 구독 확립
     * When: EventFirst, EventSecond 전송
     * Then: 구독자가 두 이벤트를 순서대로 수신하고 수집 완료
     */
    @Test
    fun test_02_eventsWhileSubscribedDeliveredImmediately() = runTest {
        /** Given **/
        val viewModel = TestEventViewModel()
        val received = mutableListOf<ViewModelEvent>()
        val collectJob = launch { viewModel.events.take(2).toList(received) }
        waitCoroutine()

        /** When **/
        viewModel.sendEvent(TestEvent.EventFirst)
        viewModel.sendEvent(TestEvent.EventSecond)
        waitCoroutine()

        /** Then **/
        assertTrue(collectJob.isCompleted)
        assertEquals(listOf(TestEvent.EventFirst, TestEvent.EventSecond), received)
    }

    /**
     * 추가 구독자는 구독 이후 발생한 이벤트만 수신하는지 검증
     *
     * Given: 첫 구독자 수신 중 EventFirst 전달 완료
     * When: 두 번째 구독자 구독 후 EventSecond 전송
     * Then: 두 번째 구독자는 EventSecond만 수신
     */
    @Test
    fun test_03_lateSubscriberReceivesOnlyEventsAfterSubscription() = runTest {
        /** Given **/
        val viewModel = TestEventViewModel()
        val firstReceived = mutableListOf<ViewModelEvent>()
        launch { viewModel.events.take(2).toList(firstReceived) }
        waitCoroutine()
        viewModel.sendEvent(TestEvent.EventFirst)
        waitCoroutine()

        /** When **/
        val secondReceived = mutableListOf<ViewModelEvent>()
        val secondJob = launch { viewModel.events.take(1).toList(secondReceived) }
        waitCoroutine()
        viewModel.sendEvent(TestEvent.EventSecond)
        waitCoroutine()

        /** Then **/
        assertTrue(secondJob.isCompleted)
        assertEquals(listOf(TestEvent.EventSecond), secondReceived)
    }

    /**
     * 추가 구독이 발생해도 기존 구독자는 계속 수신하는지 검증
     *
     * Given: 첫 구독자 수신 중 EventFirst 전달 완료
     * When: 두 번째 구독자 구독 후 EventSecond 전송
     * Then: 첫 구독자는 [EventFirst, EventSecond] 모두 수신
     */
    @Test
    fun test_04_existingSubscriberKeepsReceivingAfterNewSubscription() = runTest {
        /** Given **/
        val viewModel = TestEventViewModel()
        val firstReceived = mutableListOf<ViewModelEvent>()
        val firstJob = launch { viewModel.events.take(2).toList(firstReceived) }
        waitCoroutine()
        viewModel.sendEvent(TestEvent.EventFirst)
        waitCoroutine()

        /** When **/
        launch { viewModel.events.take(1).toList(mutableListOf()) }
        waitCoroutine()
        viewModel.sendEvent(TestEvent.EventSecond)
        waitCoroutine()

        /** Then **/
        assertTrue(firstJob.isCompleted)
        assertEquals(listOf(TestEvent.EventFirst, TestEvent.EventSecond), firstReceived)
    }

    /**
     * 전체 구독 해지 후 보낸 이벤트가 재누적되어 재구독 시 수신되는지 검증
     *
     * Given: 구독 후 전체 해지
     * When: EventFirst 전송 후 재구독
     * Then: 재구독자가 EventFirst 수신
     */
    @Test
    fun test_05_eventsAfterAllUnsubscribedBufferedAgain() = runTest {
        /** Given **/
        val viewModel = TestEventViewModel()
        val firstJob = launch { viewModel.events.collect {} }
        waitCoroutine()
        firstJob.cancelAndJoin()

        /** When **/
        viewModel.sendEvent(TestEvent.EventFirst)
        waitCoroutine()
        val received = mutableListOf<ViewModelEvent>()
        viewModel.events.take(1).toList(received)

        /** Then **/
        assertEquals(listOf(TestEvent.EventFirst), received)
    }
}
