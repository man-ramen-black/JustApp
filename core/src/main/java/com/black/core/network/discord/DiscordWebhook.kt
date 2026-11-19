package com.black.core.network.discord

import com.black.core.network.Network
import com.black.core.network.NetworkLogLevel
import com.black.core.network.NetworkResult
import com.black.core.network.RateLimitRetry
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 디스코드 웹후크 주소 검사와 첨부 하나를 담은 메시지 전송
 *
 * 웹후크 주소에는 토큰이 들어 있어 로그와 예외 메시지에 남기지 않음.
 */
object DiscordWebhook {

    /** 웹후크를 받는 디스코드 호스트 */
    private val WEBHOOK_HOSTS = setOf("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com")

    /** 웹후크 경로 `api/webhooks/<id>/<토큰>`의 고정 조각 */
    private const val API_SEGMENT = "api"
    private const val WEBHOOKS_SEGMENT = "webhooks"
    private const val WEBHOOK_PATH_SIZE = 4

    /** 첨부 하나의 번호이며 파트 이름 `files[0]`과 payload의 `attachments[0].id`에 함께 씀 */
    private const val ATTACHMENT_ID = 0

    private val ATTACHMENT_MEDIA_TYPE = "application/octet-stream".toMediaType()

    /** 연결, 요청 쓰기, 응답 읽기 각각의 무응답 제한 시간(초) */
    private const val STEP_TIMEOUT_SECONDS = 30L

    /**
     * 웹후크 전송에 쓰는 서비스
     *
     * [Network.service]는 부를 때마다 Retrofit을 새로 만들므로 한 번만 만듦.
     * 제한 시간 테스트가 [createService]로 만든 서비스를 확인하므로 [createService]로만 만듦.
     */
    val service: DiscordWebhookService by lazy { createService() }

    /**
     * 웹후크 전송 서비스 생성
     *
     * 요청 로그는 남기지 않음. 로그는 요청 주소 전체를 남겨 경로의 웹후크 토큰이 드러나기 때문.
     * 제한 시간은 [applyTimeouts]를 따름.
     * 테스트는 [customize]로 인터셉터를 덧붙여 [service]와 같은 설정의 서비스를 만들 때 씀.
     *
     * @param customize [applyTimeouts] 뒤에 덧붙일 OkHttp 설정
     */
    internal fun createService(customize: ((OkHttpClient.Builder) -> Unit)? = null): DiscordWebhookService =
        Network.service(DiscordWebhookService::class.java, NetworkLogLevel.NONE) { builder ->
            applyTimeouts(builder)
            customize?.invoke(builder)
        }

    /**
     * 공용 [Network]의 호출 전체 제한 시간(30초)을 없애고 연결, 쓰기, 읽기 각각에 30초 무응답 제한을 둠
     *
     * 묶음 첨부 상한 8 MiB를 1 Mbps 업로드로 보내면 약 67초가 걸려 호출 전체 제한 30초를 넘기 때문.
     * 전체 시간의 상한은 전송 작업의 WorkManager 실행 시간 제한(10분)이 맡음.
     */
    private fun applyTimeouts(builder: OkHttpClient.Builder) {
        builder.callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * 허용된 디스코드 호스트의 웹후크 주소(`https://<호스트>/api/webhooks/<숫자 id>/<토큰>`)인지 검사
     *
     * 앞뒤 공백이 있으면 false이므로 호출하는 쪽이 먼저 공백을 잘라야 함.
     * 사용자 정보, 443이 아닌 포트, 추가 경로, 쿼리, 프래그먼트가 있으면 false. 토큰은 영문, 숫자, `_`, `-`만 허용.
     * 판단은 [HttpUrl]이 정리한 주소 기준이라 대문자 scheme과 호스트, 명시한 `:443`, `\` 구분자, 점 조각(`..`),
     * 조각 안의 탭과 줄바꿈처럼 [HttpUrl]이 정리하는 입력은 정리된 주소로 봄.
     * Retrofit도 `@Url`을 같은 [HttpUrl] 해석으로 정리해 보내므로 통과한 주소는 정리된 디스코드 웹후크 주소로만 나감.
     * 경로는 인코딩된 조각으로 봄(`%61pi`는 `api`가 아님).
     */
    fun isValidUrl(url: String): Boolean {
        if (url != url.trim()) {
            return false
        }
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return httpUrl.isHttps &&
            httpUrl.host in WEBHOOK_HOSTS &&
            httpUrl.port == HttpUrl.defaultPort(httpUrl.scheme) &&
            httpUrl.encodedUsername.isEmpty() &&
            httpUrl.encodedPassword.isEmpty() &&
            httpUrl.encodedQuery == null &&
            httpUrl.encodedFragment == null &&
            isWebhookPath(httpUrl.encodedPathSegments)
    }

    /**
     * [content]를 메시지 본문으로, [attachment]를 [fileName] 첨부 하나로 [webhookUrl]에 보낸 결과
     *
     * 재시도는 하지 않으므로 429를 다시 보내려면 [RateLimitRetry]로 감싸 씀.
     * 첨부 파트 이름은 `files[0]`, Content-Type은 `application/octet-stream`이고, payload의 `attachments`는 `[{id: 0, filename}]`.
     */
    suspend fun send(
        service: DiscordWebhookService,
        webhookUrl: String,
        content: String,
        fileName: String,
        attachment: ByteArray,
    ): NetworkResult<Unit> {
        val payload = DiscordWebhookPayload(content, listOf(DiscordWebhookAttachment(ATTACHMENT_ID, fileName)))
        val file = MultipartBody.Part.createFormData(
            "files[$ATTACHMENT_ID]",
            fileName,
            attachment.toRequestBody(ATTACHMENT_MEDIA_TYPE),
        )
        return service.execute(webhookUrl, payload, file)
    }

    /** 인코딩된 경로 조각이 `api`, `webhooks`, 숫자 id, 토큰 순서인지 확인 */
    private fun isWebhookPath(segments: List<String>): Boolean {
        if (segments.size != WEBHOOK_PATH_SIZE) {
            return false
        }
        val (api, webhooks, id, token) = segments
        return api == API_SEGMENT &&
            webhooks == WEBHOOKS_SEGMENT &&
            id.isNotEmpty() && id.all { it in '0'..'9' } &&
            token.isNotEmpty() && token.all { isTokenCharacter(it) }
    }

    /** 웹후크 토큰에 쓰는 문자(영문, 숫자, `_`, `-`)인지 확인 */
    private fun isTokenCharacter(character: Char): Boolean =
        character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '_' || character == '-'
}
