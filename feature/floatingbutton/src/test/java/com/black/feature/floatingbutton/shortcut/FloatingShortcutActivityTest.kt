package com.black.feature.floatingbutton.shortcut

import android.content.Intent
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.feature.floatingbutton.R
import com.black.feature.floatingbutton.service.FloatingForegroundService
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [StartShortcutActivity]·[StopShortcutActivity]가 만드는 플로팅 버튼 시작·중지 바로가기의 ID·이름과 ServiceActivity 경유 서비스 인텐트 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FloatingShortcutActivityTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()

    /** 바로가기 아이콘으로 쓰는 앱 아이콘 지정(라이브러리 모듈 테스트 매니페스트에는 앱 아이콘이 없어 0) */
    @Before
    fun setupApplicationIcon() {
        application.applicationInfo.icon = android.R.drawable.sym_def_app_icon
    }

    /**
     * 시작 바로가기가 플로팅 버튼 서비스를 시작 인텐트로 실행하는지 검증
     *
     * Given: StartShortcutActivity
     * When: getShortcutInfo 호출 후 바로가기 인텐트로 ServiceActivity 실행
     * Then: 켜기 바로가기 ID·이름, ACTION_VIEW, FloatingForegroundService 시작 인텐트에 EXTRA_STOP 없음
     */
    @Test
    fun test_01_startShortcutStartsFloatingService() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(StartShortcutActivity::class.java).get()

        /** When **/
        val shortcutInfo = activity.getShortcutInfo()
        val serviceIntent = launchServiceActivity(shortcutInfo.intent)

        /** Then **/
        assertEquals(activity.getString(R.string.shortcut_floating_on_id), shortcutInfo.id)
        assertEquals(activity.getString(R.string.shortcut_floating_on_label), shortcutInfo.shortLabel.toString())
        assertEquals(Intent.ACTION_VIEW, shortcutInfo.intent.action)
        assertEquals(FloatingForegroundService::class.java.name, serviceIntent.component?.className)
        assertFalse(serviceIntent.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /**
     * 중지 바로가기가 플로팅 버튼 서비스를 중지 인텐트로 실행하는지 검증
     *
     * Given: StopShortcutActivity
     * When: getShortcutInfo 호출 후 바로가기 인텐트로 ServiceActivity 실행
     * Then: 끄기 바로가기 ID·이름, ACTION_VIEW, FloatingForegroundService 시작 인텐트에 EXTRA_STOP true
     */
    @Test
    fun test_02_stopShortcutStopsFloatingService() = runTest {
        /** Given **/
        val activity = Robolectric.buildActivity(StopShortcutActivity::class.java).get()

        /** When **/
        val shortcutInfo = activity.getShortcutInfo()
        val serviceIntent = launchServiceActivity(shortcutInfo.intent)

        /** Then **/
        assertEquals(activity.getString(R.string.shortcut_floating_off_id), shortcutInfo.id)
        assertEquals(activity.getString(R.string.shortcut_floating_off_label), shortcutInfo.shortLabel.toString())
        assertEquals(Intent.ACTION_VIEW, shortcutInfo.intent.action)
        assertEquals(FloatingForegroundService::class.java.name, serviceIntent.component?.className)
        assertTrue(serviceIntent.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
    }

    /** 바로가기 인텐트로 ServiceActivity를 실행해 시작된 서비스 인텐트 */
    private fun launchServiceActivity(shortcutIntent: Intent): Intent {
        Robolectric.buildActivity(ServiceActivity::class.java, shortcutIntent).create()
        return shadowOf(application).nextStartedService
    }
}
