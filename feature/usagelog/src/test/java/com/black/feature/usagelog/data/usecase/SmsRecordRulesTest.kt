package com.black.feature.usagelog.data.usecase

import com.black.core.system.MmsAddress
import com.black.core.system.MmsPart
import com.black.core.system.MmsRow
import com.black.core.system.SmsMessage
import com.black.core.system.SmsMessageRules
import com.black.core.system.SmsRow
import com.black.feature.usagelog.model.SmsDirection
import com.black.feature.usagelog.model.SmsPeer
import com.black.feature.usagelog.model.SmsRecord
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [SmsRecordRules.toRecord]의 문자 키, 방향, 상대, 제목·본문, MMS 여부, 첨부 수 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmsRecordRulesTest : BaseTest() {

    private fun message(
        id: Long = 1,
        mms: Boolean = false,
        time: Long = 777,
        direction: SmsMessage.Direction = SmsMessage.Direction.RECEIVED,
        peers: List<SmsMessage.Peer> = emptyList(),
        subject: String? = null,
        text: String? = null,
        attachments: Int = 0,
    ) = SmsMessage(id, mms, time, direction, peers, subject, text, attachments)

    /**
     * SMS 문자 키가 id와 시각으로 만들어지는지 검증
     *
     * Given: id 3, 시각 777인 SMS 문자
     * When: toRecord 호출
     * Then: 키 sms:3:777, 시각 777, mms false
     */
    @Test
    fun test_01_smsKeyUsesIdAndTime() = runTest {
        /** Given / When **/
        val record = SmsRecordRules.toRecord(message(id = 3, time = 777))

        /** Then **/
        assertEquals("sms:3:777", record.messageKey)
        assertEquals(777L, record.time)
        assertEquals(false, record.mms)
    }

    /**
     * MMS 문자 키가 id와 밀리초 시각으로 만들어지는지 검증
     *
     * Given: id 5, 시각 1000000인 MMS 문자
     * When: toRecord 호출
     * Then: 키 mms:5:1000000, mms true
     */
    @Test
    fun test_02_mmsKeyUsesIdAndMillisecondTime() = runTest {
        /** Given / When **/
        val record = SmsRecordRules.toRecord(message(id = 5, mms = true, time = 1_000_000))

        /** Then **/
        assertEquals("mms:5:1000000", record.messageKey)
        assertEquals(true, record.mms)
    }

    /**
     * 받은·보낸 방향이 기록 방향으로 바뀌는지 검증
     *
     * Given: 받은 문자와 보낸 문자
     * When: toRecord 호출
     * Then: 각각 SmsDirection.RECEIVED, SmsDirection.SENT
     */
    @Test
    fun test_03_directionIsConverted() = runTest {
        /** Given / When **/
        val received = SmsRecordRules.toRecord(message(direction = SmsMessage.Direction.RECEIVED))
        val sent = SmsRecordRules.toRecord(message(direction = SmsMessage.Direction.SENT))

        /** Then **/
        assertEquals(SmsDirection.RECEIVED, received.direction)
        assertEquals(SmsDirection.SENT, sent.direction)
    }

    /**
     * 상대 주소와 이름이 순서대로 옮겨지는지 검증
     *
     * Given: 이름이 있는 상대 010과 이름이 없는 상대 020
     * When: toRecord 호출
     * Then: SmsPeer("010", "이름"), SmsPeer("020", null) 순서
     */
    @Test
    fun test_04_peersAreConverted() = runTest {
        /** Given **/
        val peers = listOf(SmsMessage.Peer("010", "이름"), SmsMessage.Peer("020", null))

        /** When **/
        val record = SmsRecordRules.toRecord(message(peers = peers))

        /** Then **/
        assertEquals(listOf(SmsPeer("010", "이름"), SmsPeer("020", null)), record.peers)
    }

    /**
     * MMS 문자의 모든 필드가 기록으로 옮겨지는지 검증
     *
     * Given: 제목, 본문, 첨부 2개, 상대가 있는 보낸 MMS 문자
     * When: toRecord 호출
     * Then: 기록 전체가 기대값과 같음
     */
    @Test
    fun test_05_mmsFieldsAreCopied() = runTest {
        /** Given **/
        val source = message(
            id = 9, mms = true, time = 2_000_000, direction = SmsMessage.Direction.SENT,
            peers = listOf(SmsMessage.Peer("010", null)), subject = "안내", text = "본문", attachments = 2,
        )

        /** When **/
        val record = SmsRecordRules.toRecord(source)

        /** Then **/
        assertEquals(
            SmsRecord(2_000_000, "mms:9:2000000", SmsDirection.SENT, listOf(SmsPeer("010", null)), "안내", "본문", true, 2),
            record,
        )
    }

    /**
     * 문자 저장소 행을 해석해 바꾼 기록의 키가 저장소 `_id`·`date` 형식을 지키는지 검증
     *
     * Given: _id 3, date 777인 받은 SMS 행과 _id 5, date 1000초인 받은 MMS 행
     * When: SmsMessageRules로 해석한 뒤 toRecord 호출
     * Then: 키가 sms:3:777, mms:5:1000000이고 MMS 본문과 첨부 수가 옮겨짐
     */
    @Test
    fun test_06_rowsKeepStoredKeyFormat() = runTest {
        /** Given **/
        val sms = SmsMessageRules.fromSms(SmsRow(3, 1, 777, "010", "본문")) { null }!!
        val parts = listOf(MmsPart("text/plain", "첫째"), MmsPart("image/jpeg", null))
        val addresses = listOf(MmsAddress("020", 137))
        val mms = SmsMessageRules.fromMms(MmsRow(5, 1, 132, 1000, null, null), parts, addresses) { null }!!

        /** When **/
        val smsRecord = SmsRecordRules.toRecord(sms)
        val mmsRecord = SmsRecordRules.toRecord(mms)

        /** Then **/
        assertEquals("sms:3:777", smsRecord.messageKey)
        assertEquals("mms:5:1000000", mmsRecord.messageKey)
        assertEquals("첫째", mmsRecord.text)
        assertEquals(1, mmsRecord.attachments)
    }
}
