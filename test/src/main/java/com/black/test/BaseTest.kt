package com.black.test

import android.os.Looper
import android.util.Log
import androidx.annotation.CallSuper
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
open class BaseTest {
    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    @get:Rule
    val mockkRule by lazy { MockKRule(this) }

    @CallSuper
    @Before
    open fun before() {
        println("--------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------")
        mockkStatic(Log::class)
        val tag = slot<String>()
        val message = slot<String>()
        every { Log.v(capture(tag), capture(message)) }
            .answers {
                println("${tag.captured} [V] ${message.captured}")
                1
            }
        every { Log.d(capture(tag), capture(message)) }
            .answers {
                println("${tag.captured} [D] ${message.captured}")
                1
            }
        every { Log.i(capture(tag), capture(message)) }
            .answers {
                println("${tag.captured} [I] ${message.captured}")
                1
            }
        every { Log.w(capture(tag), capture(message)) }
            .answers {
                println("${tag.captured} [W] ${message.captured}")
                1
            }
        every { Log.e(capture(tag), capture(message)) }
            .answers {
                println("${tag.captured} [E] ${message.captured}")
                1
            }
    }

    @CallSuper
    @After
    open fun after() {
        unmockkAll()
        println("--------------------------------------------------------------------------------")
        println("--------------------------------------------------------------------------------")
    }

    /**
     * launch, delay 등 코루틴이 끝날 때까지 대기
     * https://one-delay.tistory.com/116
     */
    protected fun waitCoroutine() {
        testDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * 가상 시간을 [delayTimeMillis]만큼 진행하고, 진행한 시각에 예약된 코루틴까지 실행
     *
     * 끝나지 않는 주기 작업처럼 [waitCoroutine]으로 대기열을 비울 수 없을 때 정해진 시간만큼만 진행하는 용도.
     * advanceTimeBy는 진행한 시각과 같은 시각에 예약된 작업을 실행하지 않아 runCurrent를 함께 호출
     */
    protected fun advanceCoroutineTime(delayTimeMillis: Long) {
        val scheduler = testDispatcherRule.testDispatcher.scheduler
        scheduler.advanceTimeBy(delayTimeMillis.milliseconds)
        scheduler.runCurrent()
    }

    /**
     * Robolectric 메인 루퍼에 쌓인 작업(브로드캐스트 전달, View post 등) 실행
     *
     * 메인 루퍼는 코루틴 테스트 스케줄러와 별개라 [waitCoroutine]으로 비워지지 않음
     */
    protected fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Kotlin assert는 -ea 없이는 검사하지 않으므로 JUnit 단언 사용 */
    protected fun assert(name: String, value: Any?, expected: Any?) {
        Assert.assertEquals("Unexpected $name", expected, value)
        println("Expected $name : $value")
    }
}

/** https://lovestudycom.tistory.com/entry/Testing-Kotlin-coroutines-on-Android */
class TestDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()): TestWatcher() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}