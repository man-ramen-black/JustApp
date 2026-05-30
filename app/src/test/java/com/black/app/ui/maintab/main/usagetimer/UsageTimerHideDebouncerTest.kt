package com.black.app.ui.maintab.main.usagetimer

import com.black.test.BaseTest
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerHideDebouncer] 지연 숨김·취소로 타이머 깜빡임을 방지하는지 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerHideDebouncerTest : BaseTest() {

    private var hideCount = 0
    private val scope = CoroutineScope(testDispatcherRule.testDispatcher)
    private val debouncer = UsageTimerHideDebouncer(scope, HIDE_DELAY) { hideCount++ }

    /**
     * 숨김을 즉시 실행하지 않고 지연 시간 경과 후 실행하는지 검증
     *
     * Given: 디바운서
     * When: schedule 호출
     * Then: 호출 직후엔 미실행, 지연 시간 경과 후 1회 실행
     */
    @Test
    fun test_01_scheduleHidesAfterDelay() {
        /** Given / When **/
        debouncer.schedule()

        /** Then **/
        assertEquals(0, hideCount)
        waitCoroutine()
        assertEquals(1, hideCount)
    }

    /**
     * 지연 중 cancel(표시 요청)하면 숨김이 실행되지 않는지 검증(깜빡임 방지 핵심)
     *
     * Given: 디바운서
     * When: schedule 직후 cancel
     * Then: 지연 시간이 지나도 숨김 미실행
     */
    @Test
    fun test_02_cancelBeforeDelayPreventsHide() {
        /** Given / When **/
        debouncer.schedule()
        debouncer.cancel()

        /** Then **/
        waitCoroutine()
        assertEquals(0, hideCount)
    }

    /**
     * cancel 이후 다시 schedule하면 정상적으로 숨김이 실행되는지 검증
     *
     * Given: 디바운서
     * When: schedule → cancel → schedule
     * Then: 지연 시간 경과 후 1회 실행
     */
    @Test
    fun test_03_scheduleAfterCancelHidesAgain() {
        /** Given / When **/
        debouncer.schedule()
        debouncer.cancel()
        debouncer.schedule()

        /** Then **/
        waitCoroutine()
        assertEquals(1, hideCount)
    }

    /**
     * 숨김 예약 중 중복 schedule해도 한 번만 실행되는지 검증(기한 연장·중복 방지)
     *
     * Given: 디바운서
     * When: schedule 연속 2회
     * Then: 지연 시간 경과 후 1회만 실행
     */
    @Test
    fun test_04_repeatedScheduleHidesOnce() {
        /** Given / When **/
        debouncer.schedule()
        debouncer.schedule()

        /** Then **/
        waitCoroutine()
        assertEquals(1, hideCount)
    }

    companion object {
        private const val HIDE_DELAY = 500L
    }
}
