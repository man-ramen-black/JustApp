package com.black.app.sleeproutine

import android.content.Context

/** [SleepRoutineActions]의 실제 기기 동작 구현 */
class SleepRoutineSystemActions(context: Context) : SleepRoutineActions {
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
}
