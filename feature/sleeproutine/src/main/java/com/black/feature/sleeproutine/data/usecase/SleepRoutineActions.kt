package com.black.feature.sleeproutine.data.usecase

import com.black.core.system.MediaPlaybackState

/**
 * [SleepRoutineRunner]가 진행하는 기기 동작
 *
 * 서비스는 실제 시스템 호출 구현을, 유닛 테스트는 가짜 구현을 넘김
 */
interface SleepRoutineActions {
    /** 방해 금지 켜기, 접근 권한이 없어 켜지 못하면 false */
    fun enableDoNotDisturb(): Boolean

    fun disableDoNotDisturb()

    /** Sleep as Android 수면 기록 시작, 실행하지 못하면 false */
    fun startSleepTracking(): Boolean

    fun applyMediaVolume(level: Int)

    /** YouTube 앱으로 URL 열기, 열지 못하면 false */
    fun openYoutube(url: String): Boolean

    fun goHome()

    /** 알림 접근이 허용돼 YouTube 재생 상태를 읽을 수 있는지 여부 */
    fun canObserveYoutube(): Boolean

    fun youtubePlaybackState(): MediaPlaybackState

    fun playYoutube()

    fun pauseYoutube()

    /** 화면 끄기, 접근성 서비스가 꺼져 있거나 API 28 미만이면 false */
    fun lockScreen(): Boolean
}
