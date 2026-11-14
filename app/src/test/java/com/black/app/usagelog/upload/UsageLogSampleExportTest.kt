package com.black.app.usagelog.upload

import com.black.app.model.StoredUsageLogRecord
import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.record.ScreenOffRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.UnlockRecord
import com.black.app.usagelog.record.UsageLogRecord
import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiDisconnectedRecord
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * PC 참고 스크립트의 복호화 확인에 쓰는 전송 묶음 표본 생성
 *
 * 고정 키(바이트 0..31)와 고정 nonce(바이트 0..11)로 가짜 기록만 암호화해 `app/build/usage-log-sample/`에 씀.
 * `tools/usage-log/test_usage_log.py`가 이 표본을 풀어 쌓고 조회 결과를 확인.
 * 기록 내용은 실제 기기와 무관한 가짜 값.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogSampleExportTest : BaseTest() {

    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { it.toByte() }

    /** UTC+9 기준 2025-11-12 00:00의 epoch ms */
    private val dayStart = 1762873200000L
    private val minute = 60_000L

    private fun at(hour: Int, minuteOfHour: Int): Long = dayStart + hour * 60 * minute + minuteOfHour * minute

    /** 설계 5.4절의 기록 종류를 모두 한 건 이상 담은 가짜 기록 */
    private fun fakeRecords(): List<StoredUsageLogRecord> {
        val records: List<UsageLogRecord> = listOf(
            WifiConnectedRecord(at(0, 0), "HomeWifi", true, false),
            ScreenOnRecord(at(7, 30)),
            UnlockRecord(at(7, 30) + 5_000),
            AppUsageRecord("com.kakao.talk", "카카오톡", at(7, 31), at(7, 41)),
            NotificationPostedRecord(
                "0|com.kakao.talk|2|null|10164", "com.kakao.talk", "카카오톡", at(7, 35), "홍길동", "내일 몇 시?", "msg",
            ),
            NotificationPostedRecord(
                "0|com.kakao.talk|3|null|10164", "com.kakao.talk", "카카오톡", at(7, 36), "홍길동", "줄바꿈\n\"따옴표\" 포함", "msg",
            ),
            NotificationRemovedRecord("0|com.kakao.talk|2|null|10164", "com.kakao.talk", at(7, 40), RemovalReason.CLICK),
            NotificationPostedRecord("0|com.example.shop|4|null|10170", "com.example.shop", "쇼핑", at(8, 0), null, null, null),
            NotificationRemovedRecord("0|com.example.shop|4|null|10170", "com.example.shop", at(8, 1), RemovalReason.DISMISS),
            ScreenOffRecord(at(8, 5)),
            WifiDisconnectedRecord(at(8, 30), "HomeWifi", true, false),
            ScreenOnRecord(at(12, 0)),
            AppUsageRecord("com.google.android.youtube", "YouTube", at(12, 1), at(12, 31)),
            ScreenOffRecord(at(12, 32)),
            WifiConnectedRecord(at(12, 40), "CafeWifi", false, false),
            WifiDisconnectedRecord(at(13, 10), "CafeWifi", false, false),
            GapRecord(at(13, 10), at(14, 0), GapCause.COLLECTION_OFF),
            WifiConnectedRecord(at(18, 30), "HomeWifi", true, true),
        )
        return records.mapIndexed { index, record -> StoredUsageLogRecord(index + 1L, record) }
    }

    /**
     * 표본 파일 세 종류와 머리말을 쓰고 복호화 결과가 평문과 같은지 검증
     *
     * Given: 고정 키·nonce와 가짜 기록 묶음 한 개
     * When: encode로 첨부를 만들어 표본 폴더에 씀
     * Then: 파일이 존재하고 sample.bin을 복호화·gunzip한 바이트가 plain.jsonl과 같음
     */
    @Test
    fun test_01_exportSampleFiles() = runTest {
        /** Given **/
        val context = BatchContext(at(23, 0), SendReason.PERIODIC, "SM-FAKE", "com.black.app", 540, "install-sample")
        val records = fakeRecords()
        val plainText = UsageLogBatchEncoder.plainText(1, context, records)

        /** When **/
        val batch = UsageLogBatchEncoder.encode(records, 1, context, key, Int.MAX_VALUE) { nonce }.single()
        val directory = File(System.getProperty("user.dir"), "build/usage-log-sample").apply { mkdirs() }
        File(directory, "sample.bin").writeBytes(batch.attachment)
        File(directory, "key.txt").writeText(UsageLogCipher.encodeKey(key))
        File(directory, "plain.jsonl").writeBytes(plainText.toByteArray(Charsets.UTF_8))
        File(directory, "header.txt").writeText(batch.header)

        /** Then **/
        listOf("sample.bin", "key.txt", "plain.jsonl", "header.txt").forEach { assertTrue(File(directory, it).exists()) }
        val decrypted = GZIPInputStream(ByteArrayInputStream(UsageLogCipher.decrypt(batch.attachment, key))).readBytes()
        assertEquals(File(directory, "plain.jsonl").readText(), decrypted.toString(Charsets.UTF_8))
        assertEquals(records.size + 1, plainText.lines().count { it.isNotEmpty() })
    }
}
