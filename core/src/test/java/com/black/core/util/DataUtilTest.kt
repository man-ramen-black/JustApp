package com.black.core.util

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** [DataUtil.formatDateTime]의 패턴·로케일·시간대 적용 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DataUtilTest : BaseTest() {

    /** 테스트 전 기본 시간대(테스트가 바꾼 기본 시간대 복원용) */
    private val originalTimeZone = TimeZone.getDefault()

    /** 실행 환경과 상관없이 같은 결과가 나오도록 기본 시간대를 UTC로 고정 */
    @Before
    fun setupTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /** 다른 테스트에 영향이 없도록 기본 시간대 복원 */
    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    /**
     * 지정한 시각을 패턴대로 포맷하는지 검증
     *
     * Given: 기본 시간대 UTC, 2023-11-14 22:13:20 UTC 시각
     * When: "yyyy.MM.dd HH:mm:ss"·Locale.US로 formatDateTime 호출
     * Then: "2023.11.14 22:13:20"
     */
    @Test
    fun test_01_formatsGivenTimeWithPattern() = runTest {
        /** Given **/
        // 기본 시간대는 setupTimeZone에서 UTC로 고정

        /** When **/
        val text = DataUtil.formatDateTime("yyyy.MM.dd HH:mm:ss", Locale.US, SAMPLE_TIME_MILLIS)

        /** Then **/
        assertEquals("2023.11.14 22:13:20", text)
    }

    /**
     * 같은 패턴이어도 로케일마다 그 로케일의 표기로 포맷하는지 검증
     *
     * Given: 기본 시간대 UTC, 2023-11-14 22:13:20 UTC 시각
     * When: 월 약칭 패턴 "MMM"으로 Locale.US, Locale.KOREA 순서로 formatDateTime 호출
     * Then: Locale.US는 "Nov", Locale.KOREA는 "11월"
     */
    @Test
    fun test_02_appliesLocalePerCall() = runTest {
        /** Given **/
        // 기본 시간대는 setupTimeZone에서 UTC로 고정

        /** When **/
        val usText = DataUtil.formatDateTime("MMM", Locale.US, SAMPLE_TIME_MILLIS)
        val koreanText = DataUtil.formatDateTime("MMM", Locale.KOREA, SAMPLE_TIME_MILLIS)

        /** Then **/
        assertEquals("Nov", usText)
        assertEquals("11월", koreanText)
    }

    /**
     * 같은 패턴·로케일로 다시 불러도 그 사이 바뀐 기본 시간대를 적용하는지 검증
     *
     * Given: 기본 시간대 UTC에서 한 번 포맷한 패턴·로케일
     * When: 기본 시간대를 Asia/Seoul로 바꾼 뒤 같은 패턴·로케일·시각으로 다시 formatDateTime 호출
     * Then: UTC 결과 "2023.11.14 22:13", Asia/Seoul 결과 "2023.11.15 07:13"
     */
    @Test
    fun test_03_followsDefaultTimeZoneChange() = runTest {
        /** Given **/
        val utcText = DataUtil.formatDateTime("yyyy.MM.dd HH:mm", Locale.US, SAMPLE_TIME_MILLIS)

        /** When **/
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        val seoulText = DataUtil.formatDateTime("yyyy.MM.dd HH:mm", Locale.US, SAMPLE_TIME_MILLIS)

        /** Then **/
        assertEquals("2023.11.14 22:13", utcText)
        assertEquals("2023.11.15 07:13", seoulText)
    }

    /**
     * 시각을 넘기지 않으면 호출 시점의 현재 시각을 포맷하는지 검증
     *
     * Given: 호출 직전·직후 현재 시각
     * When: 시각 없이 "yyyy.MM.dd HH:mm:ss"·Locale.US로 formatDateTime 호출
     * Then: 결과가 호출 직전 또는 직후 시각을 같은 패턴으로 포맷한 값 중 하나
     */
    @Test
    fun test_04_defaultsToCurrentTime() = runTest {
        /** Given **/
        val pattern = "yyyy.MM.dd HH:mm:ss"
        val before = System.currentTimeMillis()

        /** When **/
        val text = DataUtil.formatDateTime(pattern, Locale.US)

        /** Then **/
        val after = System.currentTimeMillis()
        val expectedTexts = listOf(before, after).map { SimpleDateFormat(pattern, Locale.US).format(Date(it)) }
        assertTrue("$text not in $expectedTexts", text in expectedTexts)
    }

    companion object {
        /** 2023-11-14 22:13:20 UTC */
        private const val SAMPLE_TIME_MILLIS = 1_700_000_000_000L
    }
}
