package com.black.app.shortcut

import android.content.Intent
import com.black.app.R
import com.black.app.sleeproutine.SleepRoutineService
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [SleepRoutineShortcutActivity]가 만드는 수면 루틴 시작 바로가기의 ID·이름과 ServiceActivity 경유 서비스 인텐트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineShortcutActivityTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()

    /**
     * 바로가기가 수면 루틴 서비스를 시작 인텐트로 실행하는지 검증
     *
     * Given: SleepRoutineShortcutActivity
     * When: getShortcutInfo 호출 후 바로가기 인텐트로 ServiceActivity 실행
     * Then: 수면 루틴 바로가기 ID·이름, ACTION_VIEW, SleepRoutineService 시작 인텐트에 EXTRA_STOP 없음
     */
    @Test
    fun test_01_shortcutStartsSleepRoutineService() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(SleepRoutineShortcutActivity::class.java).get()

        /** When **/
        val shortcutInfo = activity.getShortcutInfo()
        val serviceIntent = launchServiceActivity(shortcutInfo.intent)

        /** Then **/
        assertEquals(activity.getString(R.string.shortcut_sleep_routine_id), shortcutInfo.id)
        assertEquals(activity.getString(R.string.shortcut_sleep_routine_label), shortcutInfo.shortLabel.toString())
        assertEquals(Intent.ACTION_VIEW, shortcutInfo.intent.action)
        assertEquals(SleepRoutineService::class.java.name, serviceIntent.component?.className)
        assertFalse(serviceIntent.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /** 바로가기 인텐트로 ServiceActivity를 실행해 시작된 서비스 인텐트 */
    private fun launchServiceActivity(shortcutIntent: Intent): Intent {
        Robolectric.buildActivity(ServiceActivity::class.java, shortcutIntent).create()
        return shadowOf(application).nextStartedService
    }
}
