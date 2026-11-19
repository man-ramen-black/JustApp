package com.black.feature.usagelog.data.usecase

import com.black.core.system.SmsMessage
import com.black.core.system.SmsMessageRules
import com.black.feature.usagelog.model.SmsDirection
import com.black.feature.usagelog.model.SmsPeer
import com.black.feature.usagelog.model.SmsRecord

/**
 * 문자 저장소에서 읽은 [SmsMessage]를 [SmsRecord]로 바꾸는 규칙
 *
 * 받은·보낸 문자만 고르는 판정과 문자 저장소 행 해석은 [SmsMessageRules]가 맡음.
 */
object SmsRecordRules {

    /**
     * [message]를 기록으로 변환
     *
     * `messageKey`는 SMS면 `sms:<_id>:<date>`, MMS면 `mms:<_id>:<date 초×1000>`.
     * 폰의 중복 거르기(`SmsProgress.seenKeys`)와 PC의 유일 색인이 이 값을 쓰므로 형식을 바꾸면 같은 문자가 두 번 쌓임.
     */
    fun toRecord(message: SmsMessage): SmsRecord {
        val table = if (message.mms) "mms" else "sms"
        return SmsRecord(
            time = message.time,
            messageKey = "$table:${message.id}:${message.time}",
            direction = when (message.direction) {
                SmsMessage.Direction.RECEIVED -> SmsDirection.RECEIVED
                SmsMessage.Direction.SENT -> SmsDirection.SENT
            },
            peers = message.peers.map { SmsPeer(it.address, it.name) },
            subject = message.subject,
            text = message.text,
            mms = message.mms,
            attachments = message.attachments,
        )
    }
}
