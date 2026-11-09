package com.black.core.dialog

import android.app.Dialog
import androidx.activity.ComponentActivity
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric

/** [DialogManager]의 노출 방식별 동작과 Activity 파괴 시 정리 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DialogManagerTest : BaseTest() {

    /**
     * 같은 tag의 Dialog가 떠 있으면 CANCEL은 새 Dialog를 띄우지 않는지 검증
     *
     * Given: tag "a"로 Dialog 하나 노출
     * When: 같은 tag와 CANCEL로 다른 Dialog 노출 요청
     * Then: 첫 Dialog는 유지, 두 번째 Dialog는 미노출
     */
    @Test
    fun test_01_cancelSkipsWhenSameTagShowing() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val manager = DialogManager(activity)
        val first = Dialog(activity)
        manager.show(first, "a")

        /** When **/
        val second = Dialog(activity)
        manager.show(second, "a", DialogManager.ShowType.CANCEL)

        /** Then **/
        assertTrue(first.isShowing)
        assertFalse(second.isShowing)
    }

    /**
     * 같은 tag의 Dialog가 떠 있으면 UPDATE는 기존 Dialog를 닫고 새 Dialog를 띄우는지 검증
     *
     * Given: tag "a"로 Dialog 하나 노출
     * When: 같은 tag와 UPDATE로 다른 Dialog 노출 요청
     * Then: 첫 Dialog는 닫히고 두 번째 Dialog 노출
     */
    @Test
    fun test_02_updateReplacesSameTagDialog() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val manager = DialogManager(activity)
        val first = Dialog(activity)
        manager.show(first, "a")

        /** When **/
        val second = Dialog(activity)
        manager.show(second, "a", DialogManager.ShowType.UPDATE)

        /** Then **/
        assertFalse(first.isShowing)
        assertTrue(second.isShowing)
    }

    /**
     * Activity가 파괴되면 노출 중인 Dialog를 모두 닫는지 검증
     *
     * Given: Dialog 두 개 노출
     * When: Activity 파괴
     * Then: 두 Dialog 모두 닫힘
     */
    @Test
    fun test_03_activityDestroyDismissesDialogs() = runTest {
        /** Given **/
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val manager = DialogManager(controller.get())
        val first = Dialog(controller.get())
        val second = Dialog(controller.get())
        manager.show(first, "a")
        manager.show(second, "b")

        /** When **/
        controller.pause().stop().destroy()

        /** Then **/
        assertFalse(first.isShowing)
        assertFalse(second.isShowing)
    }
}
