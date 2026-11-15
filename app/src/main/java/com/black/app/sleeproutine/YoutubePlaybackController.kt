package com.black.app.sleeproutine

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.black.app.usagelog.collect.UsageLogListenerService
import com.black.core.util.Log
import com.black.core.util.PermissionHelper

/**
 * YouTube 미디어 세션 재생 상태 조회와 재생·일시정지
 *
 * 활성 미디어 세션 목록은 알림 접근이 허용된 리스너 컴포넌트로만 받을 수 있어 기기 사용 기록의 [UsageLogListenerService]를 씀.
 * 알림 접근이 없으면 상태는 [YoutubePlaybackState.OTHER], 재생·일시정지는 시스템 미디어 키(마지막으로 재생한 앱이 받음)로 대신함.
 * 알림 접근이 있는데 YouTube 세션이 없으면 다른 음악 앱이 재생되지 않도록 재생·일시정지는 아무것도 하지 않음
 */
class YoutubePlaybackController(private val context: Context) {
    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listenerComponent = ComponentName(context, UsageLogListenerService::class.java)

    /**
     * YouTube 세션 상태를 읽을 수 있는지 여부
     *
     * 알림 접근 허용 여부와 같음
     */
    fun canObserve(): Boolean =
        PermissionHelper.isNotificationListenerEnabled(context, UsageLogListenerService::class.java)

    fun playbackState(): YoutubePlaybackState = when (findYoutubeController()?.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> YoutubePlaybackState.PLAYING
        PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> YoutubePlaybackState.PAUSED
        else -> YoutubePlaybackState.OTHER
    }

    fun play() {
        if (!canObserve()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return
        }
        findYoutubeController()?.transportControls?.play()
    }

    fun pause() {
        if (!canObserve()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return
        }
        findYoutubeController()?.transportControls?.pause()
    }

    /** 활성 세션 중 YouTube 세션, 알림 접근이 없거나 세션이 없으면 null */
    private fun findYoutubeController(): MediaController? {
        if (!canObserve()) return null
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null
        return try {
            sessionManager.getActiveSessions(listenerComponent).firstOrNull { it.packageName == YOUTUBE_PACKAGE }
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
