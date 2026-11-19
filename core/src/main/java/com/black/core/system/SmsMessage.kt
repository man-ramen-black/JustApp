package com.black.core.system

/**
 * 문자 저장소에서 읽은 받은·보낸 문자 한 건
 *
 * @property id SMS 표나 MMS 표의 `_id`. 두 표의 id가 겹칠 수 있어 [mms]와 함께 써야 문자 하나를 가리킴
 * @property mms MMS 표에서 읽은 문자면 true
 * @property time 밀리초 시각. MMS는 표의 초 단위 `date`에 1000을 곱한 값
 * @property peers 받은 문자는 보낸 사람, 보낸 문자는 받는 사람 목록
 * @property subject MMS 제목
 * @property text 본문. 없거나 공백뿐이면 null
 * @property attachments 본문과 배치 정보를 뺀 MMS 파트 수
 */
data class SmsMessage(
    val id: Long,
    val mms: Boolean,
    val time: Long,
    val direction: Direction,
    val peers: List<Peer>,
    val subject: String? = null,
    val text: String?,
    val attachments: Int = 0,
) {
    /** 문자 방향 */
    enum class Direction { RECEIVED, SENT }

    /**
     * 문자 상대
     *
     * @property name 연락처 표시 이름. 연락처 권한이 없거나 연락처에 없으면 null
     */
    data class Peer(val address: String, val name: String?)
}

/** SMS 표 한 행에서 [SmsMessage]를 만드는 데 쓰는 값 */
data class SmsRow(val id: Long, val type: Int, val date: Long, val address: String?, val body: String?)

/**
 * MMS 표 한 행에서 [SmsMessage]를 만드는 데 쓰는 값
 *
 * @property dateSeconds MMS 표의 `date`는 초 단위
 * @property subject `sub` 열 값. 원본 바이트를 ISO-8859-1 문자열로 옮겨 저장했을 수 있어 [subjectCharset]으로 복원해야 함
 * @property subjectCharset `sub_cs` 열의 문자 집합 MIBenum
 */
data class MmsRow(
    val id: Long,
    val messageBox: Int,
    val messageType: Int,
    val dateSeconds: Long,
    val subject: String?,
    val subjectCharset: Int?,
)

/** MMS 파트 한 개 */
data class MmsPart(val contentType: String?, val text: String?)

/** MMS 주소 한 개 */
data class MmsAddress(val address: String?, val type: Int)
