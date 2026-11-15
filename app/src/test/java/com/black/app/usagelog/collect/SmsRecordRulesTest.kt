package com.black.app.usagelog.collect

import com.black.app.usagelog.record.SmsDirection
import com.black.app.usagelog.record.SmsPeer
import com.black.app.usagelog.record.SmsRecord
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.nio.charset.Charset

/** [SmsRecordRules]의 SMS·MMS 행 방향 판정, 시각·키, 상대·본문·제목·첨부 변환 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmsRecordRulesTest : BaseTest() {

    private val noName: (String) -> String? = { null }

    private fun mmsRow(box: Int = 1, messageType: Int = 132, subject: String? = null, subjectCharset: Int? = null) =
        MmsRow(5, box, messageType, 1000, subject, subjectCharset)

    /** [text]를 [charset] 바이트로 만든 뒤 AOSP처럼 ISO-8859-1 문자열로 옮긴 `sub` 값 */
    private fun storedSubject(text: String, charset: Charset) = String(text.toByteArray(charset), Charsets.ISO_8859_1)

    /**
     * SMS type 1·2만 기록되고 나머지는 null인지 검증
     *
     * Given: type 1~6의 SMS 행
     * When: fromSms 호출
     * Then: 1은 받음, 2는 보냄, 3·4·5·6은 null
     */
    @Test
    fun test_01_smsOnlyInboxAndSentAreRecorded() = runTest {
        /** Given / When **/
        val directions = (1..6).map { type -> SmsRecordRules.fromSms(SmsRow(1, type, 10, "010", "본문"), noName)?.direction }

        /** Then **/
        assertEquals(listOf(SmsDirection.RECEIVED, SmsDirection.SENT, null, null, null, null), directions)
    }

    /**
     * SMS 행이 시각·키·본문·상대로 변환되는지 검증
     *
     * Given: 받은 SMS 행과 번호에 따라 이름이 달라지는 nameOf
     * When: fromSms 호출
     * Then: 기록 전체가 기대값과 같음
     */
    @Test
    fun test_02_smsRowFieldsAreConverted() = runTest {
        /** Given **/
        val nameOf: (String) -> String? = { address -> if (address == "010") "이름" else null }

        /** When **/
        val record = SmsRecordRules.fromSms(SmsRow(3, 1, 777, "010", "본문"), nameOf)

        /** Then **/
        assertEquals(
            SmsRecord(777, "sms:3:777", SmsDirection.RECEIVED, listOf(SmsPeer("010", "이름")), null, "본문", false, 0),
            record,
        )
    }

    /**
     * 비었거나 공백뿐인 SMS 본문이 null로 바뀌는지 검증
     *
     * Given: 본문이 빈 문자열, 공백과 줄바꿈뿐인 SMS 행
     * When: fromSms 호출
     * Then: 둘 다 text가 null
     */
    @Test
    fun test_03_blankSmsBodyBecomesNull() = runTest {
        /** Given / When / Then **/
        assertNull(SmsRecordRules.fromSms(SmsRow(1, 1, 10, "010", ""), noName)!!.text)
        assertNull(SmsRecordRules.fromSms(SmsRow(1, 1, 10, "010", " \n\t"), noName)!!.text)
    }

    /**
     * MMS 방향 판정이 msg_box와 m_type이 모두 맞을 때만 인정되는지 검증
     *
     * Given: 받은함 132, 받은함 130·134·136·0, 보낸함 128, 보낸함 130·134·0, 임시 저장·보내는 중·실패 함
     * When: fromMms 호출
     * Then: 받은함 132는 받음, 보낸함 128은 보냄, 나머지는 null
     */
    @Test
    fun test_04_mmsDirectionRequiresBoxAndType() = runTest {
        /** Given **/
        val rows = listOf(
            mmsRow(1, 132), mmsRow(1, 130), mmsRow(1, 134), mmsRow(1, 136), mmsRow(1, 0),
            mmsRow(2, 128), mmsRow(2, 130), mmsRow(2, 134), mmsRow(2, 0),
            mmsRow(3, 128), mmsRow(4, 128), mmsRow(5, 128),
        )

        /** When **/
        val results = rows.map { SmsRecordRules.fromMms(it, emptyList(), emptyList(), noName)?.direction }

        /** Then **/
        assertEquals(
            listOf(
                SmsDirection.RECEIVED, null, null, null, null,
                SmsDirection.SENT, null, null, null,
                null, null, null,
            ),
            results,
        )
    }

    /**
     * MMS 시각이 밀리초로 바뀌고 키에 반영되는지 검증
     *
     * Given: date가 초 단위 1000인 받은 MMS 행
     * When: fromMms 호출
     * Then: 시각 1000000, 키 mms:5:1000000, mms true
     */
    @Test
    fun test_05_mmsTimeIsMilliseconds() = runTest {
        /** Given / When **/
        val record = SmsRecordRules.fromMms(mmsRow(), emptyList(), emptyList(), noName)!!

        /** Then **/
        assertEquals(1_000_000L, record.time)
        assertEquals("mms:5:1000000", record.messageKey)
        assertEquals(true, record.mms)
    }

    /**
     * 받은 MMS는 보낸 사람(137)만, 보낸 MMS는 151·130·129만 상대로 쓰는지 검증
     *
     * Given: 137·151·130·129 주소가 모두 있는 MMS 주소 목록
     * When: 받은 행과 보낸 행으로 fromMms 호출
     * Then: 받은 행 상대는 137 주소, 보낸 행 상대는 나머지 세 주소
     */
    @Test
    fun test_06_mmsPeersByDirection() = runTest {
        /** Given **/
        val addresses = listOf(
            MmsAddress("from", 137), MmsAddress("to", 151), MmsAddress("cc", 130), MmsAddress("bcc", 129),
        )

        /** When **/
        val received = SmsRecordRules.fromMms(mmsRow(1), emptyList(), addresses, noName)!!
        val sent = SmsRecordRules.fromMms(mmsRow(2, 128), emptyList(), addresses, noName)!!

        /** Then **/
        assertEquals(listOf("from"), received.peers.map { it.address })
        assertEquals(listOf("to", "cc", "bcc"), sent.peers.map { it.address })
    }

    /**
     * 자리표시·빈 주소가 빠지고 같은 번호가 한 번만 남는지 검증
     *
     * Given: insert-address-token, 빈 주소, null, 공백 주소, 같은 번호 두 번
     * When: fromMms 호출
     * Then: 번호 한 개만 남음
     */
    @Test
    fun test_07_peersExcludeTokenAndBlankAndDuplicates() = runTest {
        /** Given **/
        val addresses = listOf(
            MmsAddress("insert-address-token", 151), MmsAddress("", 151), MmsAddress(null, 151),
            MmsAddress("  ", 151), MmsAddress("010", 151), MmsAddress("010", 130),
        )

        /** When **/
        val record = SmsRecordRules.fromMms(mmsRow(2, 128), emptyList(), addresses, noName)!!

        /** Then **/
        assertEquals(listOf(SmsPeer("010")), record.peers)
    }

    /**
     * text/plain 파트만 줄바꿈으로 이어지고 없거나 공백뿐이면 null인지 검증
     *
     * Given: text/plain 두 개와 이미지·smil 파트, text/plain이 없는 파트 목록, 공백 text/plain뿐인 파트 목록
     * When: mmsText 호출
     * Then: 앞은 두 본문을 줄바꿈으로 이은 값, 나머지 둘은 null
     */
    @Test
    fun test_08_mmsTextJoinsPlainParts() = runTest {
        /** Given **/
        val parts = listOf(
            MmsPart("application/smil", "<smil/>"), MmsPart("text/plain", "첫째"),
            MmsPart("image/jpeg", null), MmsPart("text/plain", "둘째"),
        )

        /** When / Then **/
        assertEquals("첫째\n둘째", SmsRecordRules.mmsText(parts))
        assertNull(SmsRecordRules.mmsText(listOf(MmsPart("image/jpeg", null))))
        assertNull(SmsRecordRules.mmsText(listOf(MmsPart("text/plain", " "), MmsPart("text/plain", "\n"))))
    }

    /**
     * 공백 text/plain 파트가 건너뛰어지는지 검증
     *
     * Given: 공백 파트가 사이에 낀 text/plain 파트 목록
     * When: mmsText 호출
     * Then: 공백 파트 없이 이어진 본문
     */
    @Test
    fun test_09_blankTextPartIsSkipped() = runTest {
        /** Given **/
        val parts = listOf(MmsPart("text/plain", "첫째"), MmsPart("text/plain", "  "), MmsPart("text/plain", "둘째"))

        /** When / Then **/
        assertEquals("첫째\n둘째", SmsRecordRules.mmsText(parts))
    }

    /**
     * 첨부 수가 text/plain·smil을 뺀 파트 수인지 검증
     *
     * Given: smil 1, text/plain 1, 이미지 2, contentType null 1
     * When: attachmentCount 호출
     * Then: 3
     */
    @Test
    fun test_10_attachmentCountExcludesTextAndSmil() = runTest {
        /** Given **/
        val parts = listOf(
            MmsPart("application/smil", null), MmsPart("text/plain", "본문"),
            MmsPart("image/jpeg", null), MmsPart("image/png", null), MmsPart(null, null),
        )

        /** When / Then **/
        assertEquals(3, SmsRecordRules.attachmentCount(parts))
    }

    /**
     * fromMms가 파트로 본문과 첨부 수를 채우는지 검증
     *
     * Given: smil, text/plain 두 개, 이미지 파트가 있는 받은 MMS
     * When: fromMms 호출
     * Then: text는 두 본문을 줄바꿈으로 이은 값, attachments는 1
     */
    @Test
    fun test_11_fromMmsFillsTextAndAttachments() = runTest {
        /** Given **/
        val parts = listOf(
            MmsPart("application/smil", "<smil/>"), MmsPart("text/plain", "첫째"),
            MmsPart("text/plain", "둘째"), MmsPart("image/jpeg", null),
        )

        /** When **/
        val record = SmsRecordRules.fromMms(mmsRow(), parts, emptyList(), noName)!!

        /** Then **/
        assertEquals("첫째\n둘째", record.text)
        assertEquals(1, record.attachments)
    }

    /**
     * 빈 제목이 null이고 있는 제목은 유지되는지 검증
     *
     * Given: 제목이 공백인 MMS 행과 "안내"인 MMS 행
     * When: fromMms 호출
     * Then: 각각 null, "안내"
     */
    @Test
    fun test_12_blankSubjectBecomesNull() = runTest {
        /** Given / When **/
        val blank = SmsRecordRules.fromMms(mmsRow(subject = " "), emptyList(), emptyList(), noName)!!
        val filled = SmsRecordRules.fromMms(mmsRow(subject = "안내"), emptyList(), emptyList(), noName)!!

        /** Then **/
        assertNull(blank.subject)
        assertEquals("안내", filled.subject)
    }

    /**
     * MMS 상대 이름이 nameOf 결과로 채워지는지 검증
     *
     * Given: 번호 010에 이름을 돌려주고 020에 null을 돌려주는 nameOf
     * When: fromMms 호출
     * Then: 010은 이름, 020은 null
     */
    @Test
    fun test_13_peerNameComesFromNameOf() = runTest {
        /** Given **/
        val addresses = listOf(MmsAddress("010", 137), MmsAddress("020", 137))

        /** When **/
        val record = SmsRecordRules.fromMms(mmsRow(), emptyList(), addresses) { if (it == "010") "이름" else null }!!

        /** Then **/
        assertEquals(listOf(SmsPeer("010", "이름"), SmsPeer("020", null)), record.peers)
    }

    /**
     * 주소 뒤 /TYPE= 꼬리가 대소문자 상관없이 제거되고 같은 번호로 합쳐지는지 검증
     *
     * Given: +821012345678/TYPE=PLMN, 같은 번호의 /type=plmn, 꼬리 없는 번호, 이메일 주소 꼬리
     * When: fromMms 호출
     * Then: 번호는 꼬리 없이 한 번, 이메일도 꼬리 없이 남음. nameOf에는 꼬리 없는 주소가 전달됨
     */
    @Test
    fun test_14_addressTypeSuffixIsStripped() = runTest {
        /** Given **/
        val addresses = listOf(
            MmsAddress("+821012345678/TYPE=PLMN", 137), MmsAddress("+821012345678/type=plmn", 137),
            MmsAddress("a@b.com/TYPE=EMAIL", 137),
        )
        val asked = mutableListOf<String>()

        /** When **/
        val record = SmsRecordRules.fromMms(mmsRow(), emptyList(), addresses) { asked += it; null }!!

        /** Then **/
        assertEquals(listOf("+821012345678", "a@b.com"), record.peers.map { it.address })
        assertEquals(listOf("+821012345678", "a@b.com"), asked)
    }

    /**
     * UTF-8 바이트가 ISO-8859-1 문자열로 저장된 제목이 복원되는지 검증
     *
     * Given: 한국어 UTF-8 바이트를 ISO-8859-1 문자열로 옮긴 제목과 문자 집합 106
     * When: decodeMmsSubject 호출
     * Then: 원래 한국어 제목
     */
    @Test
    fun test_15_utf8SubjectIsDecoded() = runTest {
        /** Given **/
        val stored = storedSubject("모임 안내", Charsets.UTF_8)

        /** When / Then **/
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject(stored, 106))
    }

    /**
     * 이미 유니코드인 제목은 문자 집합이 있어도 그대로 두는지 검증
     *
     * Given: 한국어 그대로 저장된 제목과 문자 집합 106
     * When: decodeMmsSubject 호출
     * Then: 같은 제목
     */
    @Test
    fun test_16_unicodeSubjectStaysUnchanged() = runTest {
        /** Given / When / Then **/
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject("모임 안내", 106))
    }

    /**
     * 문자 집합이 없거나 모르는 값이면 제목을 그대로 두는지 검증
     *
     * Given: ISO 문자열로 저장된 제목, 문자 집합 null과 알 수 없는 값 9999, 제목 null
     * When: decodeMmsSubject 호출
     * Then: 제목은 그대로, null 제목은 null
     */
    @Test
    fun test_17_unknownCharsetKeepsSubject() = runTest {
        /** Given **/
        val stored = storedSubject("모임 안내", Charsets.UTF_8)

        /** When / Then **/
        assertEquals(stored, SmsRecordRules.decodeMmsSubject(stored, null))
        assertEquals(stored, SmsRecordRules.decodeMmsSubject(stored, 9999))
        assertNull(SmsRecordRules.decodeMmsSubject(null, 106))
    }

    /**
     * EUC-KR과 UTF-16BE 제목이 복원되는지 검증
     *
     * Given: EUC-KR 바이트 제목(문자 집합 36, 38), UCS-2 바이트 제목(문자 집합 1000)
     * When: decodeMmsSubject 호출
     * Then: 모두 원래 한국어 제목
     */
    @Test
    fun test_18_eucKrAndUcs2SubjectsAreDecoded() = runTest {
        /** Given **/
        val eucKr = storedSubject("모임 안내", Charset.forName("EUC-KR"))
        val ucs2 = storedSubject("모임 안내", Charsets.UTF_16BE)

        /** When / Then **/
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject(eucKr, 36))
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject(eucKr, 38))
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject(ucs2, 1000))
    }

    /**
     * fromMms가 제목 문자 집합을 적용하는지 검증
     *
     * Given: UTF-8 바이트가 ISO 문자열로 저장된 제목과 문자 집합 106인 MMS 행
     * When: fromMms 호출
     * Then: subject가 복원된 한국어 제목
     */
    @Test
    fun test_19_fromMmsDecodesSubject() = runTest {
        /** Given **/
        val row = mmsRow(subject = storedSubject("모임 안내", Charsets.UTF_8), subjectCharset = 106)

        /** When **/
        val record = SmsRecordRules.fromMms(row, emptyList(), emptyList(), noName)!!

        /** Then **/
        assertEquals("모임 안내", record.subject)
    }

    /**
     * UTF-16과 US-ASCII 문자 집합이 해석되는지 검증
     *
     * Given: BOM이 붙은 UTF-16 바이트 제목(문자 집합 1015), US-ASCII 바이트 본문(문자 집합 3)
     * When: decodeMmsSubject, charsetOf 호출
     * Then: 제목은 원래 한국어 제목, 3은 US-ASCII로 해석해 원래 본문
     */
    @Test
    fun test_20_utf16AndUsAsciiCharsetsAreDecoded() = runTest {
        /** Given **/
        val utf16 = storedSubject("모임 안내", Charsets.UTF_16)
        val usAsciiBytes = "Meeting 7pm".toByteArray(Charsets.US_ASCII)

        /** When / Then **/
        assertEquals("모임 안내", SmsRecordRules.decodeMmsSubject(utf16, 1015))
        assertEquals(Charsets.US_ASCII, SmsRecordRules.charsetOf(3))
        assertEquals("Meeting 7pm", String(usAsciiBytes, SmsRecordRules.charsetOf(3)!!))
    }

    /**
     * 주소가 null인 SMS도 상대 없이 기록되는지 검증
     *
     * Given: address가 null인 받은 SMS 행, 받은 번호를 모으는 nameOf
     * When: fromSms 호출
     * Then: peers가 빈 기록, nameOf 호출 없음
     */
    @Test
    fun test_21_smsWithoutAddressHasNoPeers() = runTest {
        /** Given **/
        val asked = mutableListOf<String>()

        /** When **/
        val record = SmsRecordRules.fromSms(SmsRow(4, 1, 777, null, "본문")) { asked += it; null }

        /** Then **/
        assertEquals(SmsRecord(777, "sms:4:777", SmsDirection.RECEIVED, emptyList(), null, "본문", false, 0), record)
        assertEquals(emptyList<String>(), asked)
    }

    /**
     * KS X 1001 밖 확장 한글을 CP949 바이트로 저장한 제목이 문자 집합 36·38에서 복원되는지 검증
     *
     * Given: 똠·햏·뷁이 든 제목을 CP949 바이트로 만든 뒤 ISO 문자열로 옮긴 제목
     * When: 문자 집합 36, 38로 decodeMmsSubject 호출
     * Then: 둘 다 원래 제목
     */
    @Test
    fun test_22_cp949ExtendedHangulSubjectIsDecoded() = runTest {
        /** Given **/
        val stored = storedSubject(EXTENDED_HANGUL_TEXT, cp949)

        /** When / Then **/
        assertEquals(EXTENDED_HANGUL_TEXT, SmsRecordRules.decodeMmsSubject(stored, 36))
        assertEquals(EXTENDED_HANGUL_TEXT, SmsRecordRules.decodeMmsSubject(stored, 38))
    }

    /**
     * 파트 파일 본문을 읽는 문자 집합 36·38이 CP949 확장 한글을 복원하는지 검증
     *
     * Given: 똠·햏·뷁이 든 본문의 CP949 바이트
     * When: charsetOf(36), charsetOf(38)로 해석
     * Then: 둘 다 원래 본문
     */
    @Test
    fun test_23_cp949ExtendedHangulPartTextIsDecoded() = runTest {
        /** Given **/
        val bytes = EXTENDED_HANGUL_TEXT.toByteArray(cp949)

        /** When / Then **/
        assertEquals(EXTENDED_HANGUL_TEXT, String(bytes, SmsRecordRules.charsetOf(36)!!))
        assertEquals(EXTENDED_HANGUL_TEXT, String(bytes, SmsRecordRules.charsetOf(38)!!))
    }

    private companion object {
        /** KS X 1001 완성형 2,350자에 없어 EUC-KR로는 표현되지 않는 똠·햏·뷁을 섞은 문장 */
        const val EXTENDED_HANGUL_TEXT = "똠방각하 햏 뷁 모임"

        /** 테스트 바이트를 만드는 CP949, JVM 이름은 x-windows-949 */
        val cp949: Charset = Charset.forName("x-windows-949")
    }
}
