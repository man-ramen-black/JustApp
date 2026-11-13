package com.black.app.ui.main.keepscreenon

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.black.app.ui.main.OverlayTestFixture
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** [KeepScreenOnReceiver] 액션별 켜기·끄기와 am broadcast 결과 반환 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val receiver = KeepScreenOnReceiver()

    /** 각 테스트 후 활성 상태 해제(싱글턴 상태 격리) */
    @After
    fun deactivateKeepScreenOn() {
        OverlayTestFixture.deactivateKeepScreenOn(context)
    }

    /**
     * 켜기 액션 수신 시 활성화 검증
     *
     * Given: 다른 앱 위에 표시 권한 허용, 비활성 상태
     * When: ACTION_ACTIVATE 수신
     * Then: 활성 상태, 창 1개 추가
     */
    @Test
    fun test_01_activateActionActivates() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)

        /** When **/
        receiver.onReceive(context, Intent(KeepScreenOnReceiver.ACTION_ACTIVATE))

        /** Then **/
        assertTrue(KeepScreenOnGlobal.isActive.value)
        assertEquals(1, OverlayTestFixture.attachedViews(context).size)
    }

    /**
     * 활성 중 켜기 액션을 다시 받아도 토글과 달리 꺼지지 않는지 검증
     *
     * Given: 권한 허용 후 활성화
     * When: ACTION_ACTIVATE 수신
     * Then: 활성 상태 유지, 창 1개 유지
     */
    @Test
    fun test_02_activateActionWhileActiveKeepsActive() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        KeepScreenOnGlobal.activate(context)

        /** When **/
        receiver.onReceive(context, Intent(KeepScreenOnReceiver.ACTION_ACTIVATE))

        /** Then **/
        assertTrue(KeepScreenOnGlobal.isActive.value)
        assertEquals(1, OverlayTestFixture.attachedViews(context).size)
    }

    /**
     * 끄기 액션 수신 시 해제 검증
     *
     * Given: 권한 허용 후 활성화
     * When: ACTION_DEACTIVATE 수신
     * Then: 비활성 상태, 창 제거
     */
    @Test
    fun test_03_deactivateActionDeactivates() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        KeepScreenOnGlobal.activate(context)

        /** When **/
        receiver.onReceive(context, Intent(KeepScreenOnReceiver.ACTION_DEACTIVATE))

        /** Then **/
        assertFalse(KeepScreenOnGlobal.isActive.value)
        assertTrue(OverlayTestFixture.attachedViews(context).isEmpty())
    }

    /**
     * am broadcast와 같은 순서 있는 브로드캐스트로 매니페스트 Receiver에 전달되고 활성 여부를 결과로 반환하는지 검증
     *
     * Given: 권한 허용, 비활성 상태
     * When: 패키지 지정 ACTION_ACTIVATE를 순서 있는 브로드캐스트로 전송
     * Then: 활성 상태, 결과 코드 1, 결과 데이터 "active=true"
     */
    @Test
    fun test_04_orderedBroadcastReturnsActiveResult() = runTest {
        /** Given **/
        ShadowSettings.setCanDrawOverlays(true)
        var resultCode = Activity.RESULT_CANCELED
        var resultData: String? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                resultCode = getResultCode()
                resultData = getResultData()
            }
        }

        /** When **/
        context.sendOrderedBroadcast(
            Intent(KeepScreenOnReceiver.ACTION_ACTIVATE).setPackage(context.packageName),
            null,
            resultReceiver,
            null,
            Activity.RESULT_CANCELED,
            null,
            null,
        )
        idleMainLooper()

        /** Then **/
        assertTrue(KeepScreenOnGlobal.isActive.value)
        assertEquals(1, resultCode)
        assertEquals("active=true", resultData)
    }

    /**
     * 매니페스트 Receiver가 공개되면서도 shell만 가진 DUMP 권한으로 보호되는지 검증
     *
     * Robolectric은 권한 없는 송신자의 브로드캐스트도 전달하므로 전달 여부가 아닌 등록 정보로 판정
     *
     * Given: 병합된 매니페스트
     * When: KeepScreenOnReceiver 등록 정보 조회
     * Then: exported, 요구 권한 DUMP
     */
    @Test
    fun test_05_receiverRequiresDumpPermission() = runTest {
        /** Given **/
        val componentName = ComponentName(context, KeepScreenOnReceiver::class.java)

        /** When **/
        val receiverInfo = context.packageManager.getReceiverInfo(componentName, 0)

        /** Then **/
        assertTrue(receiverInfo.exported)
        assertEquals(Manifest.permission.DUMP, receiverInfo.permission)
    }
}
