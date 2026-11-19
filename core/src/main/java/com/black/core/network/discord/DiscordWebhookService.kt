package com.black.core.network.discord

import com.black.core.network.NetworkResult
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * 디스코드 웹후크 Retrofit 서비스
 *
 * 웹후크 주소마다 경로에 토큰이 들어 있어 전체 주소를 `@Url`로 받음.
 * 응답 본문은 쓰지 않아 [Unit]으로 받으며, 429의 대기 시간은 [NetworkResult.HttpFailure]의 `Retry-After` 헤더로 읽음.
 * 디스코드는 헤더와 본문의 `retry_after`에 같은 값을 주므로 본문은 해석하지 않음.
 * 요청은 [DiscordWebhook.send]가 만들고, 서비스는 [DiscordWebhook.service]를 씀.
 */
interface DiscordWebhookService {

    /** [payload]와 첨부 파트 [file] 하나를 multipart/form-data로 보냄 */
    @Multipart
    @POST
    suspend fun execute(
        @Url webhookUrl: String,
        @Part("payload_json") payload: DiscordWebhookPayload,
        @Part file: MultipartBody.Part,
    ): NetworkResult<Unit>
}
