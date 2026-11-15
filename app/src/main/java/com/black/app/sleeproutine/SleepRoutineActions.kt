package com.black.app.sleeproutine

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

    fun youtubePlaybackState(): YoutubePlaybackState

    fun playYoutube()

    fun pauseYoutube()
}

/**
 * 수면 루틴에서 실패해 건너뛴 단계
 *
 * 서비스가 단계별 토스트로 안내
 */
enum class SleepRoutineFailure {
    DO_NOT_DISTURB,
    SLEEP_TRACKING,
    YOUTUBE,
}
