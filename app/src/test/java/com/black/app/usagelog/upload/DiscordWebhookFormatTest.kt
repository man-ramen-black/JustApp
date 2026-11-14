package com.black.app.usagelog.upload

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DiscordWebhookFormat]의 주소 검사, multipart 본문 생성, 응답 해석 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DiscordWebhookFormatTest : BaseTest() {

    /**
     * 허용 호스트의 웹후크 주소가 유효한지 검증
     *
     * Given: discord.com, discordapp.com, ptb, canary 주소
     * When: isValidWebhookUrl 호출
     * Then: 모두 true
     */
    @Test
    fun test_01_validWebhookUrls() = runTest {
        /** Given **/
        val urls = listOf(
            "https://discord.com/api/webhooks/123/abc_DEF-1",
            "https://discordapp.com/api/webhooks/123/abc",
            "https://ptb.discord.com/api/webhooks/123/abc",
            "https://canary.discord.com/api/webhooks/123/abc"
        )

        /** When / Then **/
        urls.forEach { assertTrue(it, DiscordWebhookFormat.isValidWebhookUrl(it)) }
    }

    /**
     * 형식이 어긋난 주소가 거부되는지 검증
     *
     * Given: http, 다른 호스트, 토큰 없음, 앞뒤 공백, 숫자가 아닌 id, 쿼리, 추가 경로
     * When: isValidWebhookUrl 호출
     * Then: 모두 false
     */
    @Test
    fun test_02_invalidWebhookUrls() = runTest {
        /** Given **/
        val urls = listOf(
            "http://discord.com/api/webhooks/123/abc",
            "https://example.com/api/webhooks/123/abc",
            "https://discord.com/api/webhooks/123",
            " https://discord.com/api/webhooks/123/abc",
            "https://discord.com/api/webhooks/123/abc ",
            "https://discord.com/api/webhooks/abc/abc",
            "https://discord.com/api/webhooks/123/abc?wait=true",
            "https://discord.com/api/webhooks/123/abc/extra",
            "https://evil.com/https://discord.com/api/webhooks/123/abc"
        )

        /** When / Then **/
        urls.forEach { assertFalse(it, DiscordWebhookFormat.isValidWebhookUrl(it)) }
    }

    /**
     * multipart 본문에 payload_json과 첨부가 담기는지 검증
     *
     * Given: 헤더 한 줄, 파일 이름, 첨부 바이트
     * When: multipartBody 호출
     * Then: payload_json 부분, files[0] 부분, 첨부 바이트, 종료 경계가 모두 있음
     */
    @Test
    fun test_03_multipartContainsPayloadJsonAndFile() = runTest {
        /** Given **/
        val content = "{\"kind\":\"justapp-usage-log\",\"v\":1,\"seq\":5}"
        val attachment = "ATTACHMENT-BYTES".toByteArray()

        /** When **/
        val body = DiscordWebhookFormat.multipartBody("b", content, "usage-log-5.bin", attachment).toString(Charsets.ISO_8859_1)

        /** Then **/
        assertTrue(body.startsWith("--b\r\n"))
        assertTrue(body.contains("name=\"payload_json\""))
        assertTrue(body.contains("Content-Type: application/json"))
        assertTrue(body.contains("\"content\":\"{\\\"kind\\\":\\\"justapp-usage-log\\\",\\\"v\\\":1,\\\"seq\\\":5}\""))
        assertTrue(body.contains("\"attachments\":[{\"id\":0,\"filename\":\"usage-log-5.bin\"}]"))
        assertTrue(body.contains("name=\"files[0]\"; filename=\"usage-log-5.bin\""))
        assertTrue(body.contains("Content-Type: application/octet-stream"))
        assertTrue(body.contains("\r\n\r\nATTACHMENT-BYTES\r\n--b--\r\n"))
        assertTrue(body.endsWith("--b--\r\n"))
    }

    /**
     * 2xx 응답이 성공으로 해석되는지 검증
     *
     * Given: 상태 코드 200, 204
     * When: parseResponse 호출
     * Then: 모두 Success
     */
    @Test
    fun test_04_parse2xxSuccess() = runTest {
        /** Given / When / Then **/
        assertEquals(WebhookResult.Success, DiscordWebhookFormat.parseResponse(200, "{}", null))
        assertEquals(WebhookResult.Success, DiscordWebhookFormat.parseResponse(204, null, null))
    }

    /**
     * 429 응답에서 본문의 retry_after를 올림 밀리초로 읽는지 검증
     *
     * Given: retry_after 1.5와 0.0011, 추가 필드가 있는 본문
     * When: parseResponse 호출
     * Then: RateLimited(1500), RateLimited(2)
     */
    @Test
    fun test_05_parse429UsesBodyRetryAfter() = runTest {
        /** Given / When / Then **/
        assertEquals(
            WebhookResult.RateLimited(1500),
            DiscordWebhookFormat.parseResponse(429, "{\"retry_after\": 1.5}", "9")
        )
        assertEquals(
            WebhookResult.RateLimited(2),
            DiscordWebhookFormat.parseResponse(429, "{\"message\":\"x\",\"retry_after\":0.0011,\"global\":false}", null)
        )
    }

    /**
     * 429 응답에서 본문이 없으면 Retry-After 헤더를, 둘 다 없으면 5초를 쓰는지 검증
     *
     * Given: 본문 null과 헤더 "2", 본문 깨짐과 헤더 "0.5", 본문·헤더 없음
     * When: parseResponse 호출
     * Then: RateLimited(2000), RateLimited(500), RateLimited(5000)
     */
    @Test
    fun test_06_parse429FallsBackToHeader() = runTest {
        /** Given / When / Then **/
        assertEquals(WebhookResult.RateLimited(2000), DiscordWebhookFormat.parseResponse(429, null, "2"))
        assertEquals(WebhookResult.RateLimited(500), DiscordWebhookFormat.parseResponse(429, "<html>", "0.5"))
        assertEquals(WebhookResult.RateLimited(5000), DiscordWebhookFormat.parseResponse(429, "{}", null))
    }

    /**
     * 그 밖의 상태 코드가 본문 앞 200자를 담은 실패로 해석되는지 검증
     *
     * Given: 404와 짧은 본문, 500과 300자 본문, 본문 null
     * When: parseResponse 호출
     * Then: Failure에 상태 코드와 잘린 본문
     */
    @Test
    fun test_07_parseOtherFailure() = runTest {
        /** Given / When / Then **/
        assertEquals(WebhookResult.Failure(404, "not found"), DiscordWebhookFormat.parseResponse(404, "not found", null))
        assertEquals(WebhookResult.Failure(500, "x".repeat(200)), DiscordWebhookFormat.parseResponse(500, "x".repeat(300), null))
        assertEquals(WebhookResult.Failure(400, ""), DiscordWebhookFormat.parseResponse(400, null, null))
    }
}
