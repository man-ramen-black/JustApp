package com.black.app.view

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * [BKChronometer.restart] 경과 시간 초기화 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BKChronometerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * restart 호출 시 경과 시간이 0으로 초기화되는지 검증(화면 껐다 켜질 때 타이머 초기화)
     *
     * Given: start 후 5초 경과
     * When: restart 호출
     * Then: 경과 시간 0
     */
    @Test
    fun test_01_restartResetsElapsedToZero() {
        /** Given **/
        val chronometer = BKChronometer(context)
        chronometer.start()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        assertEquals(5000L, SystemClock.elapsedRealtime() - chronometer.base)

        /** When **/
        chronometer.restart()

        /** Then **/
        assertEquals(0L, SystemClock.elapsedRealtime() - chronometer.base)
    }
}
