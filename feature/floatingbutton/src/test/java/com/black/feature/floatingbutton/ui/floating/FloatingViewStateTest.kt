package com.black.feature.floatingbutton.ui.floating

import androidx.lifecycle.Observer
import com.black.feature.floatingbutton.data.datastore.FloatingDataStore
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [FloatingViewState] 시계가 관찰하는 동안만 갱신되는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingViewStateTest : BaseTest() {

    private val dataStore: FloatingDataStore = mockk {
        every { getSizeFlow() } returns flowOf(null)
        every { getPaddingFlow() } returns flowOf(null)
        every { getMarginFlow() } returns flowOf(null)
        every { getRadiusFlow() } returns flowOf(null)
        every { getOpacityFlow() } returns flowOf(null)
    }

    /**
     * 관찰하는 동안 시계가 갱신되고, 관찰을 끝내면 시계 수집이 멈추는지 검증
     *
     * Given: 시계를 관찰하는 상태 객체
     * When: 시간을 진행해 시계 값을 받은 뒤 관찰 종료
     * Then: 관찰 중 시계 값이 채워지고, 관찰 종료 뒤 남은 작업이 끝까지 진행되어 대기열이 비워짐(무한 갱신이 멈춤)
     */
    @Test
    fun test_01_clockStopsWithoutObserver() = runTest {
        /** Given **/
        val state = FloatingViewState(dataStore)
        val observer = Observer<String> { }
        state.clock.observeForever(observer)

        /** When **/
        advanceCoroutineTime(250)
        val clockWhileObserved = state.clock.value
        state.clock.removeObserver(observer)
        // 갱신이 멈추지 않으면 무한 지연 반복이라 이 호출이 끝나지 않음
        waitCoroutine()

        /** Then **/
        assertTrue(!clockWhileObserved.isNullOrEmpty())
    }
}
