package com.black.feature.sleeproutine.model

import java.net.URI
import java.net.URISyntaxException

/**
 * 수면 루틴 설정
 *
 * @property youtubeUrl 수면 루틴에서 여는 YouTube 주소
 * @property durationMinutes 시작부터 YouTube 정지·방해 금지 해제까지의 시간(분)
 * @property volumeEnabled 시작할 때 미디어 볼륨을 맞출지 여부
 * @property volumeLevel 맞출 미디어 볼륨 단계(기기 최대보다 크면 적용할 때 최대로 맞춤)
 */
data class SleepRoutineSettings(
    val youtubeUrl: String = "",
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val volumeEnabled: Boolean = true,
    val volumeLevel: Int = DEFAULT_VOLUME_LEVEL,
) {
    companion object {
        /** Tasker SleepTracking Task와 같은 1시간 */
        const val DEFAULT_DURATION_MINUTES = 60
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 600

        /** Tasker SleepTracking Task가 쓰던 미디어 볼륨 단계 */
        const val DEFAULT_VOLUME_LEVEL = 6

        private val YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
        )
        private val WEB_SCHEMES = setOf("http", "https")

        /**
         * http(s) YouTube 주소 여부
         *
         * 앞뒤 공백, 스킴·호스트 대소문자는 무시.
         * `music.youtube.com`은 YouTube Music 앱으로 열려 YouTube 재생 제어(`com.google.android.youtube` 세션)로 정지할 수 없어 허용하지 않음.
         */
        fun isValidYoutubeUrl(url: String): Boolean {
            val uri = try {
                URI(url.trim())
            } catch (e: URISyntaxException) {
                return false
            }
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return scheme in WEB_SCHEMES && host in YOUTUBE_HOSTS
        }

        /**
         * 정지 시간 입력을 분으로 변환
         *
         * 정수가 아니거나 범위 밖이면 null.
         */
        fun parseDurationMinutes(input: String): Int? =
            input.trim().toIntOrNull()?.takeIf { it in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES }
    }
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
    SCREEN_OFF,
}
