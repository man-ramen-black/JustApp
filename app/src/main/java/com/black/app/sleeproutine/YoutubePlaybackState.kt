package com.black.app.sleeproutine

/** 수면 루틴이 다음 단계로 넘어가는 기준으로 쓰는 YouTube 미디어 세션 재생 상태 */
enum class YoutubePlaybackState {
    /** 재생 중 */
    PLAYING,

    /** 일시정지·정지 */
    PAUSED,

    /** 세션 없음, 읽을 수 없음, 버퍼링 등 그 밖의 상태 */
    OTHER,
}
