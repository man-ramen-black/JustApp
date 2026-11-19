package com.black.core.system

import java.nio.charset.Charset

/**
 * 문자 저장소(SMS·MMS 표) 행을 [SmsMessage]로 해석하는 규칙
 *
 * 받은 문자와 보낸 문자만 해석하고 임시 저장·보내는 중·실패·내려받기 전 MMS, 배달·읽음 보고는 null.
 */
object SmsMessageRules {
    // Telephony 상수값(JVM 유닛 테스트에서 안드로이드 상수에 기대지 않으려고 복사)
    const val SMS_TYPE_INBOX = 1
    const val SMS_TYPE_SENT = 2
    const val MMS_BOX_INBOX = 1
    const val MMS_BOX_SENT = 2
    const val MMS_TYPE_SEND_REQ = 128
    const val MMS_TYPE_RETRIEVE_CONF = 132
    const val MMS_ADDRESS_FROM = 137
    const val MMS_ADDRESS_TO = 151
    const val MMS_ADDRESS_CC = 130
    const val MMS_ADDRESS_BCC = 129

    /** MMS 주소 표에서 자기 번호 자리에 들어가는 값 */
    const val ADDRESS_TOKEN = "insert-address-token"

    private const val CONTENT_TYPE_TEXT = "text/plain"
    private const val CONTENT_TYPE_SMIL = "application/smil"

    // IANA 문자 집합 MIBenum
    private const val MIB_US_ASCII = 3
    private const val MIB_ISO_8859_1 = 4
    private const val MIB_UTF_8 = 106
    private const val MIB_KS_C_5601 = 36
    private const val MIB_EUC_KR = 38
    private const val MIB_UCS2 = 1000
    private const val MIB_UTF_16 = 1015

    private const val CHARSET_CP949 = "x-windows-949"
    private const val CHARSET_EUC_KR = "EUC-KR"

    /**
     * 한국어 MIBenum을 해석할 문자 집합
     *
     * KS_C_5601-1987과 EUC-KR 표기로 보낸 문자도 실제로는 확장 한글(똠·햏 등)을 담은 CP949 바이트일 수 있어,
     * EUC-KR의 상위 집합인 CP949로 해석하고 기기에 CP949가 없으면 EUC-KR로 해석. 둘 다 없으면 null.
     */
    private val KOREAN_CHARSET: Charset? by lazy {
        listOf(CHARSET_CP949, CHARSET_EUC_KR).firstOrNull(Charset::isSupported)?.let(Charset::forName)
    }

    /** MMS 주소 뒤에 붙는 `/TYPE=PLMN` 같은 주소 종류 꼬리 */
    private val ADDRESS_TYPE_SUFFIX = Regex("/TYPE=.*$", RegexOption.IGNORE_CASE)

    fun smsDirection(type: Int): SmsMessage.Direction? = when (type) {
        SMS_TYPE_INBOX -> SmsMessage.Direction.RECEIVED
        SMS_TYPE_SENT -> SmsMessage.Direction.SENT
        else -> null
    }

    /**
     * MMS 방향 판정
     *
     * 받은함은 내려받기를 마친 메시지(`m_type` 132), 보낸함은 보낸 메시지(`m_type` 128)만 인정하고
     * 내려받기 알림(130)·배달 보고(134)·읽음 보고(136)는 제외.
     */
    fun mmsDirection(messageBox: Int, messageType: Int): SmsMessage.Direction? = when {
        messageBox == MMS_BOX_INBOX && messageType == MMS_TYPE_RETRIEVE_CONF -> SmsMessage.Direction.RECEIVED
        messageBox == MMS_BOX_SENT && messageType == MMS_TYPE_SEND_REQ -> SmsMessage.Direction.SENT
        else -> null
    }

    fun fromSms(row: SmsRow, nameOf: (String) -> String?): SmsMessage? {
        val direction = smsDirection(row.type) ?: return null
        return SmsMessage(
            id = row.id,
            mms = false,
            time = row.date,
            direction = direction,
            peers = peersOf(listOfNotNull(row.address), nameOf),
            text = row.body?.takeIf { it.isNotBlank() },
        )
    }

    fun fromMms(row: MmsRow, parts: List<MmsPart>, addresses: List<MmsAddress>, nameOf: (String) -> String?): SmsMessage? {
        val direction = mmsDirection(row.messageBox, row.messageType) ?: return null
        val peerTypes = if (direction == SmsMessage.Direction.RECEIVED) {
            setOf(MMS_ADDRESS_FROM)
        } else {
            setOf(MMS_ADDRESS_TO, MMS_ADDRESS_CC, MMS_ADDRESS_BCC)
        }
        return SmsMessage(
            id = row.id,
            mms = true,
            time = row.dateSeconds * 1000,
            direction = direction,
            peers = peersOf(addresses.filter { it.type in peerTypes }.mapNotNull { it.address }, nameOf),
            subject = decodeMmsSubject(row.subject, row.subjectCharset)?.takeIf { it.isNotBlank() },
            text = mmsText(parts),
            attachments = attachmentCount(parts),
        )
    }

    /**
     * MMS 제목 복원
     *
     * AOSP는 `sub`에 원본 바이트를 ISO-8859-1 문자로 옮겨 저장하고 문자 집합은 `sub_cs`(MIBenum)에 둠.
     * 모든 문자가 0xFF 이하일 때만 바이트로 되돌려 [charset]으로 해석하고, 모르는 문자 집합이거나
     * 이미 유니코드 문자열이면 그대로 둠.
     */
    fun decodeMmsSubject(subject: String?, charset: Int?): String? {
        if (subject == null || charset == null) {
            return subject
        }
        if (subject.any { it.code > 0xFF }) {
            return subject
        }
        val target = charsetOf(charset) ?: return subject
        return String(subject.toByteArray(Charsets.ISO_8859_1), target)
    }

    /**
     * IANA 문자 집합 MIBenum에 해당하는 [Charset], 모르면 null
     *
     * 제목과 파트 파일 본문에 함께 쓰며, 한국어 36·38은 [KOREAN_CHARSET]으로 해석.
     */
    fun charsetOf(mibEnum: Int): Charset? = when (mibEnum) {
        MIB_US_ASCII -> Charsets.US_ASCII
        MIB_ISO_8859_1 -> Charsets.ISO_8859_1
        MIB_UTF_8 -> Charsets.UTF_8
        MIB_UTF_16 -> Charsets.UTF_16
        MIB_UCS2 -> Charsets.UTF_16BE
        MIB_KS_C_5601, MIB_EUC_KR -> KOREAN_CHARSET
        else -> null
    }

    /** `text/plain` 파트를 순서대로 줄바꿈으로 이은 본문, 공백뿐이거나 없으면 null */
    fun mmsText(parts: List<MmsPart>): String? = parts
        .filter { it.contentType.equals(CONTENT_TYPE_TEXT, ignoreCase = true) }
        .mapNotNull { part -> part.text?.takeIf { it.isNotBlank() } }
        .joinToString("\n")
        .ifBlank { null }

    /**
     * 본문과 배치 정보를 뺀 파트 수
     *
     * 본문은 `text/plain`, 배치 정보는 `application/smil` 파트.
     */
    fun attachmentCount(parts: List<MmsPart>): Int = parts.count {
        !it.contentType.equals(CONTENT_TYPE_TEXT, ignoreCase = true) &&
            !it.contentType.equals(CONTENT_TYPE_SMIL, ignoreCase = true)
    }

    /** 주소 종류 꼬리와 자기 번호 자리표시를 뺀 상대 목록, 같은 주소는 한 번만 */
    private fun peersOf(addresses: List<String>, nameOf: (String) -> String?): List<SmsMessage.Peer> = addresses
        .map { it.replace(ADDRESS_TYPE_SUFFIX, "").trim() }
        .filter { it.isNotEmpty() && it != ADDRESS_TOKEN }
        .distinct()
        .map { SmsMessage.Peer(it, nameOf(it)) }
}
