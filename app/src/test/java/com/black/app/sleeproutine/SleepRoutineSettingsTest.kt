package com.black.app.sleeproutine

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SleepRoutineSettings]의 YouTube 주소 검증, 정지 시간 변환, 기본값 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SleepRoutineSettingsTest : BaseTest() {

    /**
     * YouTube 주소로 인정하는 형식 검증
     *
     * Given: 허용 호스트의 http(s) 주소, 앞뒤 공백 주소, 대문자 스킴·호스트 주소, `?`가 두 번인 주소
     * When: 주소 검증
     * Then: 모두 true
     */
    @Test
    fun test_01_isValidYoutubeUrlAcceptsYoutubeUrls() = runTest {
        /** Given **/
        val urls = listOf(
            "https://youtu.be/lQ0fS2meTYQ?t=600",
            "https://www.youtube.com/watch?v=abc",
            "http://m.youtube.com/watch?v=abc",
            "https://youtube.com/watch?v=abc",
            "  https://youtu.be/abc  ",
            "HTTPS://YOUTU.BE/abc",
            "https://youtu.be/lQ0fS2meTYQ?si=knU7b4zbb8IEXMtX?t=10m",
        )

        /** When / Then **/
        urls.forEach { assertTrue(it, SleepRoutineSettings.isValidYoutubeUrl(it)) }
    }

    /**
     * YouTube 주소로 인정하지 않는 형식 검증
     *
     * Given: 빈 값, 스킴 없음, 다른 스킴·호스트, 호스트 위장, YouTube Music 주소, URI 오류 주소
     * When: 주소 검증
     * Then: 모두 false
     */
    @Test
    fun test_02_isValidYoutubeUrlRejectsInvalidUrls() = runTest {
        /** Given **/
        val urls = listOf(
            "",
            "   ",
            "youtu.be/abc",
            "ftp://youtu.be/abc",
            "https://example.com/watch?v=abc",
            "https://music.youtube.com/watch?v=abc",
            "https://notyoutube.com/abc",
            "https://youtu.be.evil.com/abc",
            "https://exa mple.com",
        )

        /** When / Then **/
        urls.forEach { assertFalse(it, SleepRoutineSettings.isValidYoutubeUrl(it)) }
    }

    /**
     * 범위 안 정수 입력의 분 변환 검증
     *
     * Given: 경계값과 공백 포함 정수 문자열
     * When: 분 변환
     * Then: 해당 정수 반환
     */
    @Test
    fun test_03_parseDurationMinutesReturnsMinutesInRange() = runTest {
        /** Given / When / Then **/
        assertEquals(1, SleepRoutineSettings.parseDurationMinutes("1"))
        assertEquals(600, SleepRoutineSettings.parseDurationMinutes("600"))
        assertEquals(60, SleepRoutineSettings.parseDurationMinutes(" 60 "))
    }

    /**
     * 범위 밖이거나 정수가 아닌 입력의 분 변환 검증
     *
     * Given: 0, 601, 음수, 문자, 빈 값, 소수 문자열
     * When: 분 변환
     * Then: 모두 null
     */
    @Test
    fun test_04_parseDurationMinutesReturnsNullForInvalidInput() = runTest {
        /** Given / When / Then **/
        listOf("0", "601", "-5", "abc", "", "1.5").forEach {
            assertNull(it, SleepRoutineSettings.parseDurationMinutes(it))
        }
    }

    /**
     * 기본 설정값 검증
     *
     * Given: 인자 없이 생성한 설정
     * When: 각 값 확인
     * Then: URL 빈 값, 60분, 볼륨 켜짐, 6단계
     */
    @Test
    fun test_05_defaultSettings() = runTest {
        /** Given **/
        val settings = SleepRoutineSettings()

        /** When / Then **/
        assertEquals("", settings.youtubeUrl)
        assertEquals(60, settings.durationMinutes)
        assertTrue(settings.volumeEnabled)
        assertEquals(6, settings.volumeLevel)
    }
}
