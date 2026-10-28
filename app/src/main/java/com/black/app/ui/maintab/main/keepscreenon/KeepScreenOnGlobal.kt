package com.black.app.ui.maintab.main.keepscreenon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.black.core.util.Log
import com.black.core.util.OverlayViewUtil
import com.black.core.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 켜짐 유지 상태·오버레이 창·화면 꺼짐 수신 관리
 *
 * FLAG_KEEP_SCREEN_ON을 가진 1x1 투명 오버레이 창으로 화면 자동 꺼짐 방지, 화면 꺼짐 수신 시 자동 해제
 * 프로세스 종료 시 창도 함께 제거되므로 상태 영속 저장 불필요
 */
object KeepScreenOnGlobal {
    /** dumpsys window 식별용 오버레이 창 제목 */
    const val WINDOW_TITLE = "KeepScreenOn"

    private val mutableIsActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = mutableIsActive.asStateFlow()

    private var overlayView: View? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    /** 활성 상태 반전 후 반전된 활성 여부 반환 */
    @MainThread
    fun toggle(context: Context): Boolean {
        if (isActive.value) {
            deactivate(context)
        } else {
            activate(context)
        }
        return isActive.value
    }

    /** 오버레이 창 추가·화면 꺼짐 Receiver 등록 후 활성 여부 반환 */
    @MainThread
    fun activate(context: Context): Boolean {
        // 이미 활성이면 창 중복 추가 방지
        if (isActive.value) {
            Log.w("already active")
            return true
        }

        // 다른 앱 위에 표시 권한 없으면 오버레이 창 추가 불가
        val applicationContext = context.applicationContext
        if (!PermissionHelper.canDrawOverlays(applicationContext)) {
            Log.w("cannot draw overlays")
            return false
        }

        // 화면 꺼짐 방지 전용 1x1 투명 오버레이 창 추가
        val view = View(applicationContext)
        try {
            OverlayViewUtil.attachView(view) { windowParams ->
                windowParams.width = 1
                windowParams.height = 1
                // attachView 기본 플래그(바깥 터치 감시 등)를 덮어써 터치·포커스를 아래 앱으로 통과
                windowParams.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                windowParams.title = WINDOW_TITLE
            }
        } catch (e: WindowManager.BadTokenException) {
            // 권한 확인 직후 회수 등으로 시스템이 창 추가 거부
            Log.e("attach overlay failed : $e")
            return false
        } catch (e: SecurityException) {
            Log.e("attach overlay failed : $e")
            return false
        }

        // 해제 시 제거할 창·Receiver 보관 후 활성 상태 반영
        overlayView = view
        screenOffReceiver = registerScreenOffReceiver(applicationContext)
        mutableIsActive.value = true
        return true
    }

    /** 오버레이 창 제거·화면 꺼짐 Receiver 해제 */
    @MainThread
    fun deactivate(context: Context) {
        // 이미 비활성이면 정리할 창·Receiver 없음
        if (!isActive.value) {
            return
        }

        // 창 제거로 화면 꺼짐 방지 해제
        overlayView?.let { OverlayViewUtil.detachView(it) }
        overlayView = null
        // 화면 꺼짐 수신 중단
        screenOffReceiver?.let { context.applicationContext.unregisterReceiver(it) }
        screenOffReceiver = null
        mutableIsActive.value = false
    }

    private fun registerScreenOffReceiver(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // 화면 꺼짐 수신 시 해제
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    Log.d("screen off")
                    deactivate(context)
                }
            }
        }
        // ACTION_SCREEN_OFF는 시스템 보호 브로드캐스트라 NOT_EXPORTED여도 수신, 다른 앱의 위조 전송만 차단
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }
}
