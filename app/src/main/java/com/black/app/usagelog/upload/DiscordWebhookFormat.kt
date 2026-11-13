package com.black.app.usagelog.upload

import com.black.core.util.JsonUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** 웹후크 전송 결과 */
sealed interface WebhookResult {
    /** 2xx 응답 */
    data object Success : WebhookResult

    /** 429 응답, [retryAfterMillis] 뒤에 다시 보내야 함 */
    data class RateLimited(val retryAfterMillis: Long) : WebhookResult

    /**
     * 그 밖의 실패
     *
     * [statusCode]는 응답을 받지 못했으면 null. [message]에는 웹후크 주소를 담지 않음.
     */
    data class Failure(val statusCode: Int?, val message: String) : WebhookResult
}

/** 묶음 하나를 웹후크로 보내는 수단 */
fun interface WebhookSender {
    suspend fun send(webhookUrl: String, content: String, fileName: String, attachment: ByteArray): WebhookResult
}

/** 디스코드 웹후크 요청 본문 생성과 응답 해석 (네트워크 호출 없음) */
object DiscordWebhookFormat {

    private val WEBHOOK_URL_PATTERN =
        Regex("https://(discord\\.com|discordapp\\.com|ptb\\.discord\\.com|canary\\.discord\\.com)/api/webhooks/[0-9]+/[A-Za-z0-9_-]+")

    private const val LINE_BREAK = "\r\n"
    private const val DEFAULT_RETRY_AFTER_MILLIS = 5000L
    private const val FAILURE_MESSAGE_LIMIT = 200

    /**
     * 허용된 디스코드 호스트의 웹후크 주소인지 검사
     *
     * 앞뒤 공백·쿼리·추가 경로가 있으면 false. 호출하는 쪽이 먼저 공백을 잘라야 함.
     */
    fun isValidWebhookUrl(url: String): Boolean = WEBHOOK_URL_PATTERN.matches(url)

    /** payload_json 부분과 files[0] 부분으로 이루어진 multipart/form-data 본문 */
    fun multipartBody(boundary: String, content: String, fileName: String, attachment: ByteArray): ByteArray {
        val payloadJson = checkNotNull(
            JsonUtil.to<WebhookPayload>(
                WebhookPayload(content, listOf(WebhookAttachment(0, fileName))),
                true,
                JsonUtil.kotlinSerializationCompact
            )
        )
        val output = ByteArrayOutputStream()
        output.write(
            ("--$boundary$LINE_BREAK" +
                "Content-Disposition: form-data; name=\"payload_json\"$LINE_BREAK" +
                "Content-Type: application/json$LINE_BREAK$LINE_BREAK" +
                "$payloadJson$LINE_BREAK" +
                "--$boundary$LINE_BREAK" +
                "Content-Disposition: form-data; name=\"files[0]\"; filename=\"$fileName\"$LINE_BREAK" +
                "Content-Type: application/octet-stream$LINE_BREAK$LINE_BREAK").toByteArray(Charsets.UTF_8)
        )
        output.write(attachment)
        output.write("$LINE_BREAK--$boundary--$LINE_BREAK".toByteArray(Charsets.UTF_8))
        return output.toByteArray()
    }

    /**
     * 응답을 [WebhookResult]로 해석
     *
     * 429의 대기 시간은 본문의 retry_after, 없으면 [retryAfterHeader]의 순서로 읽고(둘 다 초 단위 소수 가능)
     * 올림한 밀리초로 바꿈. 둘 다 없으면 5초. 실패 메시지는 본문 앞 200자.
     */
    fun parseResponse(statusCode: Int, body: String?, retryAfterHeader: String?): WebhookResult = when (statusCode) {
        in 200..299 -> WebhookResult.Success
        429 -> {
            val seconds = body?.let {
                JsonUtil.from<RateLimitBody>(it, true, JsonUtil.kotlinSerializationCompact, logInput = false)?.retryAfter
            } ?: retryAfterHeader?.trim()?.toDoubleOrNull()
            val millis = seconds?.takeIf { it.isFinite() && it >= 0 }?.let { ceil(it * 1000).toLong() }
            WebhookResult.RateLimited(millis ?: DEFAULT_RETRY_AFTER_MILLIS)
        }
        else -> WebhookResult.Failure(statusCode, body.orEmpty().take(FAILURE_MESSAGE_LIMIT))
    }

    @Serializable
    private data class WebhookPayload(val content: String, val attachments: List<WebhookAttachment>)

    @Serializable
    private data class WebhookAttachment(val id: Int, val filename: String)

    @Serializable
    private data class RateLimitBody(@SerialName("retry_after") val retryAfter: Double? = null)
}
