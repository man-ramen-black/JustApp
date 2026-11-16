package com.black.app.sleeproutine

import android.content.Context
import android.os.Build
import com.black.core.service.GlobalActionRequest
import com.black.core.util.AccessibilityServiceStatus

/** [SleepRoutineActions]의 실제 기기 동작 구현 */
class SleepRoutineSystemActions(private val context: Context) : SleepRoutineActions {
    private val doNotDisturbController = DoNotDisturbController(context)
    private val mediaVolumeController = MediaVolumeController(context)
    private val youtubePlaybackController = YoutubePlaybackController(context)
    private val launcher = SleepRoutineLauncher(context)

    override fun enableDoNotDisturb(): Boolean = doNotDisturbController.enable()

    override fun disableDoNotDisturb() = doNotDisturbController.disable()

    override fun startSleepTracking(): Boolean = launcher.startSleepTracking()

    override fun applyMediaVolume(level: Int) = mediaVolumeController.apply(level)

    override fun openYoutube(url: String): Boolean = launcher.openYoutube(url)

    /** 홈 화면 실행 결과는 다음 단계 진행에 쓰지 않아 무시 */
    override fun goHome() {
        launcher.goHome()
    }

    override fun canObserveYoutube(): Boolean = youtubePlaybackController.canObserve()

    override fun youtubePlaybackState(): YoutubePlaybackState = youtubePlaybackController.playbackState()

    override fun playYoutube() = youtubePlaybackController.play()

    override fun pauseYoutube() = youtubePlaybackController.pause()

    /**
     * 접근성 서비스에 화면 잠금 요청
     *
     * 요청은 브로드캐스트라 서비스의 실행 결과를 받지 못하므로, 요청을 보낼 수 있는 상태면 true
     */
    override fun lockScreen(): Boolean {
        // 화면 잠금 전역 동작이 없는 API 28 미만
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        // 요청을 받을 접근성 서비스가 꺼진 경우
        if (!AccessibilityServiceStatus(context).isAccessibilityServiceEnabled()) return false
        // 화면 잠금 요청 전송
        context.sendBroadcast(GlobalActionRequest.createLockScreenIntent(context))
        return true
    }
}
