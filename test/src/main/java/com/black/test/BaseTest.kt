package com.black.test

import android.util.Log
import androidx.annotation.CallSuper
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}

/**
 * https://lovestudycom.tistory.com/entry/Testing-Kotlin-coroutines-on-Android
 */
class TestDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()): TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}