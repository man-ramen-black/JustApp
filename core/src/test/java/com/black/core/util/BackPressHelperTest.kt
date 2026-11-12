package com.black.core.util

import android.app.Activity
import android.widget.Toast
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast

/** [BackPressHelper]의 두 번 눌러 종료 판정과 안내 토스트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BackPressHelperTest : BaseTest() {

    /**
     * 두 번 누르면 종료하고, 종료 뒤 첫 누름은 다시 안내만 하는지 검증
     *
     * Given: Activity
     * When: 연속 두 번 누른 뒤 한 번 더 누름
     * Then: 종료 콜백 1회, 안내 토스트 2회, 토스트 길이 LENGTH_SHORT
     */
    @Test
    fun test_01_secondPressKillsAndResets() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var killCount = 0

        /** When **/
        BackPressHelper.onBackPressed(activity, "message") { killCount++ }
        BackPressHelper.onBackPressed(activity, "message") { killCount++ }
        BackPressHelper.onBackPressed(activity, "message") { killCount++ }

        /** Then **/
        assertEquals(1, killCount)
        assertEquals(2, ShadowToast.shownToastCount())
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().duration)
    }
}
