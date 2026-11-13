package com.black.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * 처음 [count]개 값을 받을 때까지 수집
 *
 * 값이 [count]개보다 적게 오면 끝나지 않고 대기하므로 개수를 정확히 맞춤.
 * 구독 전에 낸 값은 Flow가 보관해 첫 구독자에게 줄 때만(EventViewModel.events) 받으므로,
 * 그런 Flow가 아니거나 값이 0개일 수 있으면 [collectDuring] 사용
 */
suspend fun <T> Flow<T>.collectValues(count: Int): List<T> = take(count).toList()

/**
 * [action] 실행 전에 [flow] 구독을 시작해 [action]과 그 뒤 대기열이 빌 때까지 낸 값을 모두 수집(없으면 빈 목록)
 *
 * 구독 전 쌓인 값을 첫 구독자에게 주는 Flow(EventViewModel.events)는 그 값도 함께 받으므로,
 * [action] 없이 호출하면 지금까지 쌓인 값 수집.
 * 구독은 같은 테스트 스케줄러의 Unconfined 디스패처로 바로 시작해 값을 낸 즉시 받고, 끝나면 구독 취소
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestScope.collectDuring(flow: Flow<T>, action: suspend () -> Unit = {}): List<T> {
    val receivedValues = mutableListOf<T>()
    // backgroundScope 작업은 advanceUntilIdle이 기다리지 않으므로, 값을 낸 즉시 받도록 Unconfined로 구독
    val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.toList(receivedValues)
    }
    try {
        action()
        testScheduler.advanceUntilIdle()
    } finally {
        collectJob.cancel()
    }
    return receivedValues
}
