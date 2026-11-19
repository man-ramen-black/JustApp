package com.black.core.network.discord

import kotlinx.serialization.Serializable

/**
 * 웹후크 요청의 `payload_json` 파트
 *
 * [attachments]의 `id`는 첨부 파트 이름 `files[<id>]`의 번호와 같아야 디스코드가 그 파트에 [DiscordWebhookAttachment.filename]을 붙임.
 */
@Serializable
data class DiscordWebhookPayload(val content: String, val attachments: List<DiscordWebhookAttachment>)

/** [DiscordWebhookPayload]에 담는 첨부 하나의 번호와 파일 이름 */
@Serializable
data class DiscordWebhookAttachment(val id: Int, val filename: String)
