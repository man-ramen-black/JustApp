package com.black.app.usagelog.upload

import com.black.app.model.StoredUsageLogRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.util.Random
import java.util.zip.GZIPInputStream

/** [UsageLogBatchEncoder]의 전송 본문 생성, 암호화, 크기 분할 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogBatchEncoderTest : BaseTest() {

    private val context = BatchContext(1763000000000, SendReason.HOME_LEAVE, "SM-S921N", "com.black.app", 540, "install-1")

    /** 시각을 흩뿌려 gzip으로 거의 줄지 않는 가짜 기록 */
    private fun records(count: Int, firstId: Long = 1): List<StoredUsageLogRecord> {
        val random = Random(42)
        return List(count) { StoredUsageLogRecord(firstId + it, ScreenOnRecord(random.nextLong(1, Long.MAX_VALUE))) }
    }

    private fun decryptToText(attachment: ByteArray, key: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(UsageLogCipher.decrypt(attachment, key))).readBytes().toString(Charsets.UTF_8)

    /**
     * 첫 줄이 meta 줄인지 검증
     *
     * Given: 전송 맥락과 순번 123
     * When: plainText 호출
     * Then: 첫 줄이 설계 형식의 meta JSON
     */
    @Test
    fun test_01_plainTextFirstLineIsMeta() = runTest {
        /** Given / When **/
        val text = UsageLogBatchEncoder.plainText(123, context, records(1))

        /** Then **/
        assertEquals(
            """{"type":"meta","v":1,"seq":123,"sentAt":1763000000000,"reason":"home_leave","device":"SM-S921N","app":"com.black.app","tzOffsetMinutes":540}""",
            text.lines().first(),
        )
    }

    /**
     * 기록 id가 설치 id와 행 id를 합친 값인지 검증
     *
     * Given: 행 id 7인 기록
     * When: plainText 호출
     * Then: 기록 줄에 "id":"install-1-7"
     */
    @Test
    fun test_02_recordIdsCombineInstallIdAndRowId() = runTest {
        /** Given / When **/
        val text = UsageLogBatchEncoder.plainText(1, context, records(1, firstId = 7))

        /** Then **/
        assertTrue(text.lines()[1].contains("\"id\":\"install-1-7\""))
    }

    /**
     * 첨부를 복호화하고 gunzip하면 plainText가 나오는지 검증
     *
     * Given: 기록 3건과 키
     * When: encode 호출
     * Then: 첨부를 복호화하고 압축 해제한 문자열이 plainText와 같음
     */
    @Test
    fun test_03_decryptAndGunzipGivesPlainText() = runTest {
        /** Given **/
        val key = UsageLogCipher.generateKey()
        val stored = records(3)

        /** When **/
        val batches = UsageLogBatchEncoder.encode(stored, 9, context, key)

        /** Then **/
        assertEquals(1, batches.size)
        assertEquals(UsageLogBatchEncoder.plainText(9, context, stored), decryptToText(batches[0].attachment, key))
        assertEquals(listOf(1L, 2L, 3L), batches[0].recordIds)
    }

    /**
     * 머리말과 파일 이름이 명세와 같은지 검증
     *
     * Given: 순번 5
     * When: encode 호출
     * Then: header와 fileName이 명세 값
     */
    @Test
    fun test_04_headerAndFileNameMatchSpec() = runTest {
        /** Given / When **/
        val batch = UsageLogBatchEncoder.encode(records(1), 5, context, UsageLogCipher.generateKey()).single()

        /** Then **/
        assertEquals("""{"kind":"justapp-usage-log","v":1,"seq":5}""", batch.header)
        assertEquals("usage-log-5.bin", batch.fileName)
    }

    /**
     * 한도를 넘으면 앞뒤로 나눠 순번을 이어 붙이는지 검증
     *
     * Given: 기록 50건과 한도 600바이트
     * When: encode 호출
     * Then: 묶음 2개 이상, 순번 연속, recordIds 순서 유지, 기록 2건 이상인 묶음은 한도 이하
     */
    @Test
    fun test_05_splitsWhenOverLimit() = runTest {
        /** Given **/
        val stored = records(50)

        /** When **/
        val batches = UsageLogBatchEncoder.encode(stored, 100, context, UsageLogCipher.generateKey(), maxAttachmentBytes = 600)

        /** Then **/
        assertTrue(batches.size >= 2)
        assertEquals(List(batches.size) { 100L + it }, batches.map { it.sequence })
        assertEquals(stored.map { it.id }, batches.flatMap { it.recordIds })
        batches.filter { it.recordIds.size >= 2 }.forEach { assertTrue(it.attachment.size <= 600) }
    }
}
