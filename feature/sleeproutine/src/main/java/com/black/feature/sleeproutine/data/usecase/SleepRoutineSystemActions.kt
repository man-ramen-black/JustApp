package com.black.feature.sleeproutine.data.usecase

import android.content.Context
import com.black.core.system.DoNotDisturbController
import com.black.core.system.MediaPlaybackState
import com.black.core.system.MediaSessionPlaybackController
import com.black.core.system.MediaVolumeController
import com.black.core.system.SystemActions

/** [SleepRoutineActions]의 실제 기기 동작 구현 */
class SleepRoutineSystemActions(private val context: Context) : SleepRoutineActions {
    private val doNotDisturbController = DoNotDisturbController(context)
    private val mediaVolumeController = MediaVolumeController(context)
    private val youtubePlaybackController = MediaSessionPlaybackController(context, SleepRoutineLauncher.YOUTUBE_PACKAGE)
    private val launcher = SleepRoutineLauncher(context)

    override fun enableDoNotDisturb(): Boolean = doNotDisturbController.enable()

    override fun disableDoNotDisturb() = doNotDisturbController.disable()

    override fun startSleepTracking(): Boolean = launcher.startSleepTracking()

    override fun applyMediaVolume(level: Int) = mediaVolumeController.apply(level)

    override fun openYoutube(url: String): Boolean = launcher.openYoutube(url)

    /** 홈 화면 실행 결과는 다음 단계 진행에 쓰지 않아 무시 */
    override fun goHome() {
        SystemActions.goHome(context)
    }

    override fun canObserveYoutube(): Boolean = youtubePlaybackController.canObserve()

    override fun youtubePlaybackState(): MediaPlaybackState = youtubePlaybackController.playbackState()

    override fun playYoutube() = youtubePlaybackController.play()

    override fun pauseYoutube() = youtubePlaybackController.pause()

    /**
     * 접근성 서비스에 화면 잠금 요청
     *
     * 요청은 브로드캐스트라 서비스의 실행 결과를 받지 못하므로, 요청을 보낼 수 있는 상태면 true
     */
    override fun lockScreen(): Boolean = SystemActions.requestLockScreen(context)
}
