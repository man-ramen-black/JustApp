package com.black.feature.studypopup.receiver

import android.app.Application
import android.content.Intent
import com.black.feature.studypopup.overlay.StudyPopupGlobal
import com.black.test.BaseTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [ScreenReceiver] 등록 뒤 화면 켜짐·꺼짐이 [StudyPopupGlobal]에 전달되고 해제 뒤에는 끊기는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenReceiverTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()

    @Before
    fun mockStudyPopupGlobal() {
        mockkObject(StudyPopupGlobal)
        every { StudyPopupGlobal.onScreenOn(any()) } just Runs
        every { StudyPopupGlobal.onScreenOff() } just Runs
    }

    /** [ScreenReceiver]는 object라 등록 상태가 다음 테스트로 넘어가지 않도록 해제 */
    @After
    fun unregisterScreenReceiver() {
        ScreenReceiver.unregister(application)
    }

    /**
     * 등록하면 화면 켜짐·꺼짐을 순서대로 스터디 팝업에 전달하는지 검증
     *
     * Given: ScreenReceiver 등록
     * When: 화면 켜짐, 화면 꺼짐 브로드캐스트 순서로 전송
     * Then: 이 앱의 Context로 onScreenOn, 이어서 onScreenOff 호출
     */
    @Test
    fun test_01_registeredReceiverForwardsScreenOnAndOff() = runTest {
        /** Given **/
        ScreenReceiver.register(application)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idleMainLooper()

        /** Then **/
        verifyOrder {
            StudyPopupGlobal.onScreenOn(match { it.applicationContext === application })
            StudyPopupGlobal.onScreenOff()
        }
    }

    /**
     * 해제 없이 다시 등록해도 한 번만 전달하고, 해제하면 더 전달하지 않는지 검증
     *
     * Given: ScreenReceiver를 두 번 등록
     * When: 화면 켜짐 전송, 해제 뒤 화면 켜짐과 화면 꺼짐 전송
     * Then: onScreenOn 1회, onScreenOff 호출 없음
     */
    @Test
    fun test_02_duplicateRegisterForwardsOnceAndUnregisterStops() = runTest {
        /** Given **/
        ScreenReceiver.register(application)
        ScreenReceiver.register(application)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        idleMainLooper()
        ScreenReceiver.unregister(application)
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idleMainLooper()

        /** Then **/
        verify(exactly = 1) { StudyPopupGlobal.onScreenOn(any()) }
        verify(exactly = 0) { StudyPopupGlobal.onScreenOff() }
    }
}
