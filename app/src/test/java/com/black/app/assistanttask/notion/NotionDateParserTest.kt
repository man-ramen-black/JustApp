package com.black.app.assistanttask.notion

import com.black.app.assistanttask.AssistantTaskDue
import com.black.test.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.TimeZone

/** [NotionDateParser]의 기한·시각 해석과 시각 문자열 변환 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotionDateParserTest : BaseTest() {

    /**
     * 날짜만 있는 기한이 DateOnly로 해석되는지 검증
     *
     * Given: 날짜만 있는 start
     * When: parseDue 호출
     * Then: DateOnly
     */
    @Test
    fun test_01_parsesDateOnly() = runTest {
        /** Given / When **/
        val due = NotionDateParser.parseDue("2026-11-18", null)

        /** Then **/
        assertEquals(AssistantTaskDue.DateOnly("2026-11-18"), due)
    }

    /**
     * 오프셋이 있는 시각이 그 오프셋 기준으로 해석되는지 검증
     *
     * Given: +09:00 오프셋 시각
     * When: parseDue 호출
     * Then: 해당 epoch millis의 DateTime
     */
    @Test
    fun test_02_parsesDateTimeWithOffset() = runTest {
        /** Given / When **/
        val due = NotionDateParser.parseDue("2026-11-18T09:30:00.000+09:00", null)

        /** Then **/
        assertEquals(AssistantTaskDue.DateTime(1794961800000), due)
    }

    /**
     * 오프셋이 없으면 time_zone 필드를 쓰는지 검증
     *
     * Given: 오프셋 없는 시각과 Asia/Seoul
     * When: parseDue 호출
     * Then: 서울 기준 DateTime
     */
    @Test
    fun test_03_usesTimeZoneFieldWithoutOffset() = runTest {
        /** Given / When **/
        val due = NotionDateParser.parseDue("2026-11-18T09:30:00.000", "Asia/Seoul")

        /** Then **/
        assertEquals(AssistantTaskDue.DateTime(1794961800000), due)
    }

    /**
     * 오프셋과 time_zone이 모두 없으면 기본 시간대를 쓰는지 검증
     *
     * Given: 오프셋·time_zone 없는 시각과 UTC 기본 시간대
     * When: parseDue 호출
     * Then: UTC 기준 DateTime
     */
    @Test
    fun test_04_usesDefaultZoneWithoutOffsetAndTimeZone() = runTest {
        /** Given / When **/
        val due = NotionDateParser.parseDue("2026-11-18T09:30:00", null, TimeZone.getTimeZone("UTC"))

        /** Then **/
        assertEquals(AssistantTaskDue.DateTime(1794994200000), due)
    }

    /**
     * 형식이 맞지 않는 입력이 null이 되는지 검증
     *
     * Given: 한글 문자열, null, 빈 문자열
     * When: parseDue 호출
     * Then: 모두 null
     */
    @Test
    fun test_05_returnsNullForMalformed() = runTest {
        /** Given / When / Then **/
        assertNull(NotionDateParser.parseDue("내일", null))
        assertNull(NotionDateParser.parseDue(null, null))
        assertNull(NotionDateParser.parseDue("", null))
    }

    /**
     * 시각 문자열과 epoch millis 상호 변환 검증
     *
     * Given: UTC 시각 문자열, epoch millis, 잘못된 문자열
     * When: parseTimestamp·formatTimestamp 호출
     * Then: 서로 대응하는 값, 잘못된 문자열은 null
     */
    @Test
    fun test_06_parsesAndFormatsTimestamp() = runTest {
        /** Given / When / Then **/
        assertEquals(1794971520000, NotionDateParser.parseTimestamp("2026-11-18T03:12:00.000Z"))
        assertEquals("2026-11-18T03:12:00.000Z", NotionDateParser.formatTimestamp(1794971520000))
        assertNull(NotionDateParser.parseTimestamp("x"))
    }

    /**
     * epoch millis가 오프셋 없는 지역 시각 문자열로 바뀌는지 검증
     *
     * Given: 2026-11-20 09:30(서울)의 epoch millis
     * When: formatLocalDateTime 호출
     * Then: 초까지 있고 오프셋 없는 문자열
     */
    @Test
    fun test_07_formatsLocalDateTime() = runTest {
        /** Given / When **/
        val text = NotionDateParser.formatLocalDateTime(1795134600000, TimeZone.getTimeZone("Asia/Seoul"))

        /** Then **/
        assertEquals("2026-11-20T09:30:00", text)
    }

    /**
     * 존재하지 않는 날짜가 null이 되는지 검증
     *
     * Given: 2월 30일
     * When: parseDue 호출
     * Then: null
     */
    @Test
    fun test_08_returnsNullForNonexistentDate() = runTest {
        /** Given / When / Then **/
        assertNull(NotionDateParser.parseDue("2026-02-30", null))
    }

    /**
     * 모르는 time_zone이 기본 시간대로 해석되는지 검증
     *
     * Given: 오프셋 없는 시각, 모르는 time_zone, 서울 기본 시간대
     * When: parseDue 호출
     * Then: 기본 시간대 기준 DateTime
     */
    @Test
    fun test_09_unknownTimeZoneFallsBackToDefaultZone() = runTest {
        /** Given / When **/
        val due = NotionDateParser.parseDue("2026-11-18T09:30:00", "Mars/Base", TimeZone.getTimeZone("Asia/Seoul"))

        /** Then **/
        assertEquals(AssistantTaskDue.DateTime(1794961800000), due)
    }
}
