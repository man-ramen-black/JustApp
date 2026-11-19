package com.black.feature.usagelog.model

import kotlinx.serialization.Serializable

/** 기록 전송을 시작한 계기 */
enum class SendReason(val value: String) {
    PERIODIC("periodic"),
    HOME_ARRIVE("home_arrive"),
    HOME_LEAVE("home_leave"),
    MANUAL("manual"),
}

/** 마지막 전송 시도의 시각과 성공 여부 */
@Serializable
data class LastSendResult(val time: Long, val success: Boolean)

/** 전송 한 번의 결과 */
sealed interface UploadResult {
    /** 웹후크 주소나 암호화 키가 없거나 유효하지 않아 보내지 않음 */
    data object NotConfigured : UploadResult

    /** 미전송 기록이 없음 */
    data object NothingToSend : UploadResult

    /** [recordCount]건을 보냄 */
    data class Sent(val recordCount: Int) : UploadResult

    /** 전송 실패이며 [retryable]이면 나중에 다시 시도할 가치가 있음 */
    data class Failed(val retryable: Boolean) : UploadResult
}
