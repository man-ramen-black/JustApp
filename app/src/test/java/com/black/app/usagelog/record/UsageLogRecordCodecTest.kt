package com.black.app.usagelog.record

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageLogRecordCodec]의 JSON 한 줄 변환과 복원 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogRecordCodecTest : BaseTest() {

    /**
     * 알림 기록의 전송 JSON 한 줄이 설계 형식과 같은 키·값인지 검증
     *
     * Given: 모든 필드가 채워진 알림 게시 기록
     * When: toJsonLine 호출
     * Then: 첫 두 키가 id, type이고 나머지가 설계 예시와 같은 키·값
     */
    @Test
    fun test_01_notificationJsonLineMatchesSpec() = runTest {
        /** Given **/
        val record = NotificationPostedRecord(
            "0|com.kakao.talk|2|null|10164", "com.kakao.talk", "카카오톡", 1762999000000, "홍길동", "내일 몇 시?", "msg",
        )
        val expected = Json.parseToJsonElement(
            """{"id":"a1b2-7","type":"notification","t":1762999000000,"key":"0|com.kakao.talk|2|null|10164","package":"com.kakao.talk","appName":"카카오톡","title":"홍길동","text":"내일 몇 시?","category":"msg"}""",
        ).jsonObject

        /** When **/
        val line = UsageLogRecordCodec.toJsonLine("a1b2-7", record)

        /** Then **/
        val actual = Json.parseToJsonElement(line).jsonObject
        assertEquals(listOf("id", "type"), actual.keys.take(2))
        assertEquals(expected, actual)
    }

    /**
     * 모든 기록이 JSON을 거쳐 원래대로 복원되는지 검증
     *
     * Given: 화면 3종과 Wi-Fi 2종을 포함한 9가지 기록
     * When: encode 후 decode
     * Then: 모두 원본과 같음
     */
    @Test
    fun test_02_everyRecordRoundTrips() = runTest {
        /** Given **/
        val records = allRecords()

        /** When / Then **/
        assertEquals(9, records.size)
        records.forEach { record ->
            assertEquals(record, UsageLogRecordCodec.decode(UsageLogRecordCodec.encode(record)))
        }
    }

    /**
     * type 값이 설계와 같은지 검증
     *
     * Given: 9가지 기록
     * When: encode한 JSON의 type 읽기
     * Then: 설계의 type 문자열과 같음
     */
    @Test
    fun test_03_typeNamesMatchSpec() = runTest {
        /** Given / When **/
        val types = allRecords().map {
            Json.parseToJsonElement(UsageLogRecordCodec.encode(it)).jsonObject["type"]!!.jsonPrimitive.content
        }

        /** Then **/
        assertEquals(
            listOf(
                "app_usage", "screen_on", "screen_off", "unlock", "notification",
                "notification_removed", "wifi_connected", "wifi_disconnected", "gap",
            ),
            types,
        )
    }

    /**
     * null 필드가 생략되지 않고 null로 쓰이는지 검증
     *
     * Given: title이 null인 알림, home이 null인 Wi-Fi 기록
     * When: toJsonLine 호출
     * Then: 두 줄 모두 해당 키를 null 값으로 포함
     */
    @Test
    fun test_04_nullFieldsWrittenAsNull() = runTest {
        /** Given / When **/
        val notificationLine = UsageLogRecordCodec.toJsonLine(
            "x-1", NotificationPostedRecord("k", "p", "a", 1, null, null, null),
        )
        val wifiLine = UsageLogRecordCodec.toJsonLine("x-2", WifiConnectedRecord(1, "ssid", null, false))

        /** Then **/
        assertTrue(notificationLine.contains("\"title\":null"))
        assertTrue(wifiLine.contains("\"home\":null"))
    }

    /**
     * 줄바꿈과 따옴표가 포함된 본문이 한 줄에 담기는지 검증
     *
     * Given: 개행과 큰따옴표가 든 알림 본문
     * When: toJsonLine 호출 후 다시 파싱
     * Then: 줄에 개행 문자가 없고 파싱한 본문이 원문과 같음
     */
    @Test
    fun test_05_newlineAndQuoteStayOnOneLine() = runTest {
        /** Given **/
        val text = "첫 줄\n\"둘째\" 줄"

        /** When **/
        val line = UsageLogRecordCodec.toJsonLine(
            "x-1", NotificationPostedRecord("k", "p", "a", 1, "t", text, null),
        )

        /** Then **/
        assertFalse(line.contains('\n'))
        assertEquals(text, Json.parseToJsonElement(line).jsonObject["text"]!!.jsonPrimitive.content)
    }

    /**
     * 앱 사용·공백 기록의 t가 시작 시각인지 검증
     *
     * Given: 앱 사용 기록과 공백 기록
     * When: toJsonLine 호출
     * Then: t가 start와 같고 나머지 키·값이 설계 형식과 같음
     */
    @Test
    fun test_06_appUsageAndGapLinesUseStartAsTime() = runTest {
        /** Given / When **/
        val appLine = UsageLogRecordCodec.toJsonLine(
            "x-1", AppUsageRecord("com.google.android.youtube", "YouTube", 1000, 61000),
        )
        val gapLine = UsageLogRecordCodec.toJsonLine("x-2", GapRecord(1000, 5000, GapCause.COLLECTION_OFF))

        /** Then **/
        assertEquals(
            Json.parseToJsonElement(
                """{"id":"x-1","type":"app_usage","t":1000,"package":"com.google.android.youtube","appName":"YouTube","start":1000,"end":61000}""",
            ),
            Json.parseToJsonElement(appLine),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"id":"x-2","type":"gap","t":1000,"start":1000,"end":5000,"cause":"collection_off"}""",
            ),
            Json.parseToJsonElement(gapLine),
        )
    }

    /**
     * 복원할 수 없는 JSON이 null로 처리되는지 검증
     *
     * Given: 깨진 JSON, 알 수 없는 type의 JSON
     * When: decode 호출
     * Then: 둘 다 null
     */
    @Test
    fun test_07_undecodableJsonReturnsNull() = runTest {
        /** Given / When / Then **/
        assertNull(UsageLogRecordCodec.decode("{깨진"))
        assertNull(UsageLogRecordCodec.decode("""{"type":"unknown","t":1}"""))
    }

    private fun allRecords(): List<UsageLogRecord> = listOf(
        AppUsageRecord("com.a", "A", 1000, 2000),
        ScreenOnRecord(3000),
        ScreenOffRecord(4000),
        UnlockRecord(5000),
        NotificationPostedRecord("k", "com.a", "A", 6000, "제목", null, "msg"),
        NotificationRemovedRecord("k", "com.a", 7000, RemovalReason.CLICK),
        WifiConnectedRecord(8000, "home-ssid", true, false),
        WifiDisconnectedRecord(9000, null, null, true),
        GapRecord(10000, 11000, GapCause.UNSENT_DELETED),
    )
}
