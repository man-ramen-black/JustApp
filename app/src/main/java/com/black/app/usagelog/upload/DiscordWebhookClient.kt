package com.black.app.usagelog.upload

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

/**
 * [HttpURLConnection]으로 디스코드 웹후크에 multipart 요청을 보내는 [WebhookSender]
 *
 * 웹후크 주소(토큰 포함)·본문·응답 본문은 로그에 남기지 않음. 예외 메시지에 주소가 실릴 수 있어
 * 실패 메시지에는 예외 클래스 이름만 담음.
 */
class DiscordWebhookClient @Inject constructor() : WebhookSender {

    override suspend fun send(webhookUrl: String, content: String, fileName: String, attachment: ByteArray): WebhookResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val boundary = "justapp-${UUID.randomUUID()}"
                val body = DiscordWebhookFormat.multipartBody(boundary, content, fileName, attachment)
                connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MILLIS
                    readTimeout = TIMEOUT_MILLIS
                    doOutput = true
                    setFixedLengthStreamingMode(body.size)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    // 기본 User-Agent(http.agent)에는 기기 모델과 빌드 정보가 담겨 디스코드로 나가므로 고정값으로 덮음
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                connection.outputStream.use { it.write(body) }

                val statusCode = connection.responseCode
                // 2xx만 inputStream, 그 밖은 errorStream이며 둘 다 null일 수 있음
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
                DiscordWebhookFormat.parseResponse(statusCode, responseBody, connection.getHeaderField("Retry-After"))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                WebhookResult.Failure(null, exception::class.java.simpleName)
            } finally {
                connection?.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000
        const val USER_AGENT = "JustApp-UsageLog/1"
    }
}
