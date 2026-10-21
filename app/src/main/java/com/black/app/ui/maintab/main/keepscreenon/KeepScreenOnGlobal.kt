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

    /**
     * 활성 상태 반전 후 반전된 활성 여부 반환
     */
    @MainThread
    fun toggle(context: Context): Boolean {
        if (isActive.value) {
            deactivate(context)
        } else {
            activate(context)
        }
        return isActive.value
    }

    /**
     * 오버레이 창 추가·화면 꺼짐 Receiver 등록 후 활성 여부 반환
     */
    @MainThread
    fun activate(context: Context): Boolean {
        if (isActive.value) {
            Log.w("already active")
            return true
        }

        val applicationContext = context.applicationContext
        if (!PermissionHelper.canDrawOverlays(applicationContext)) {
            Log.w("cannot draw overlays")
            return false
        }

        val view = View(applicationContext)
        try {
            OverlayViewUtil.attachView(view) { windowParams ->
                windowParams.width = 1
                windowParams.height = 1
                windowParams.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                windowParams.title = WINDOW_TITLE
            }
        } catch (e: WindowManager.BadTokenException) {
            Log.e("attach overlay failed : $e")
            return false
        } catch (e: SecurityException) {
            Log.e("attach overlay failed : $e")
            return false
        }

        overlayView = view
        screenOffReceiver = registerScreenOffReceiver(applicationContext)
        mutableIsActive.value = true
        return true
    }

    /**
     * 오버레이 창 제거·화면 꺼짐 Receiver 해제
     */
    @MainThread
    fun deactivate(context: Context) {
        if (!isActive.value) {
            return
        }

        overlayView?.let { OverlayViewUtil.detachView(it) }
        overlayView = null
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
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }
}
