package com.black.core.system

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/**
 * [targetPackage] 앱 미디어 세션의 재생 상태 조회와 재생·일시정지
 *
 * 활성 미디어 세션 목록은 알림 접근이 허용된 리스너 컴포넌트로만 받을 수 있어 앱의 알림 리스너([PermissionHelper.findNotificationListenerService])를 씀.
 * 알림 접근이 없으면 상태는 [MediaPlaybackState.OTHER], 재생·일시정지는 시스템 미디어 키(마지막으로 재생한 앱이 받음)로 대신함.
 * 알림 접근이 있는데 대상 앱 세션이 없으면 다른 음악 앱이 재생되지 않도록 재생·일시정지는 아무것도 하지 않음
 *
 * @param targetPackage 제어할 미디어 세션의 앱 패키지명
 */
class MediaSessionPlaybackController(
    private val context: Context,
    private val targetPackage: String,
) {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listenerComponent: ComponentName? = PermissionHelper.findNotificationListenerService(context)

    /**
     * 대상 앱 세션 상태를 읽을 수 있는지 여부
     *
     * 생성 때 찾은 알림 리스너의 알림 접근 허용 여부와 같음.
     * 수면 루틴이 짧은 주기로 상태를 읽어 매번 매니페스트를 조회하지 않도록 허용 목록만 다시 확인함.
     */
    fun canObserve(): Boolean =
        listenerComponent != null && PermissionHelper.isNotificationListenerEnabled(context, listenerComponent)

    fun playbackState(): MediaPlaybackState = when (findTargetController()?.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> MediaPlaybackState.PLAYING
        PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> MediaPlaybackState.PAUSED
        else -> MediaPlaybackState.OTHER
    }

    fun play() {
        if (!canObserve()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return
        }
        findTargetController()?.transportControls?.play()
    }

    fun pause() {
        if (!canObserve()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return
        }
        findTargetController()?.transportControls?.pause()
    }

    /** 활성 세션 중 대상 앱 세션, 알림 접근이 없거나 세션이 없으면 null */
    private fun findTargetController(): MediaController? {
        if (!canObserve() || listenerComponent == null) {
            return null
        }
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null
        return try {
            sessionManager.getActiveSessions(listenerComponent).firstOrNull { it.packageName == targetPackage }
        } catch (e: SecurityException) {
            // 확인 직후 알림 접근이 해제된 경우
            Log.w("getActiveSessions failed : $e")
            null
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}

/** [MediaSessionPlaybackController]가 읽은 대상 앱 미디어 세션의 재생 상태 */
enum class MediaPlaybackState {
    /** 재생 중 */
    PLAYING,

    /** 일시정지·정지 */
    PAUSED,

    /** 세션 없음, 읽을 수 없음, 버퍼링 등 그 밖의 상태 */
    OTHER,
}
