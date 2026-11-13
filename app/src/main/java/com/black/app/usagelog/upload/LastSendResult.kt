package com.black.app.usagelog.upload

import kotlinx.serialization.Serializable

/** 마지막 전송 시도의 시각과 성공 여부 */
@Serializable
data class LastSendResult(val time: Long, val success: Boolean)
