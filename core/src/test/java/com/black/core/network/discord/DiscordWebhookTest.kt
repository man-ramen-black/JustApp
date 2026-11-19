package com.black.core.network.discord

import com.black.core.network.Network
import com.black.core.network.NetworkResult
import com.black.core.util.JsonUtil
import com.black.test.BaseTest
import com.black.test.captureDebugLogs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DiscordWebhook]의 웹후크 주소 검사와 multipart 요청 형식, 응답 결과를 MockWebServer로 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DiscordWebhookTest : BaseTest() {

    private lateinit var server: MockWebServer

    @Before
    fun setupServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun shutdownServer() {
        Network.setLoggingEnabled(false)
        server.shutdown()
    }

    /**
     * 허용 호스트의 웹후크 주소가 유효한지 검증
     *
     * Given: discord.com, discordapp.com, ptb, canary 주소
     * When: isValidUrl 호출
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
        urls.forEach { assertTrue(it, DiscordWebhook.isValidUrl(it)) }
    }

    /**
     * 형식이 어긋난 주소가 거부되는지 검증
     *
     * Given: http, 다른 호스트, 토큰 없음, 앞뒤 공백, 숫자가 아닌 id, 쿼리, 추가 경로, 다른 주소 안에 넣은 주소
     * When: isValidUrl 호출
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
        urls.forEach { assertFalse(it, DiscordWebhook.isValidUrl(it)) }
    }

    /**
     * 사용자 정보, 다른 포트, 빈 쿼리와 프래그먼트, 끝 슬래시, 인코딩한 조각, 허용하지 않는 토큰 문자가 거부되는지 검증
     *
     * Given: 각 조건만 어긋난 discord.com 웹후크 주소와 주소가 아닌 문자열
     * When: isValidUrl 호출
     * Then: 모두 false
     */
    @Test
    fun test_03_rejectsUserInfoPortFragmentAndEncodedPath() = runTest {
        /** Given **/
        val urls = listOf(
            "https://user@discord.com/api/webhooks/123/abc",
            "https://user:secret@discord.com/api/webhooks/123/abc",
            "https://discord.com:8443/api/webhooks/123/abc",
            "https://discord.com/api/webhooks/123/abc?",
            "https://discord.com/api/webhooks/123/abc#",
            "https://discord.com/api/webhooks/123/abc#token",
            "https://discord.com/api/webhooks/123/",
            "https://discord.com/api/webhooks/123/abc/",
            "https://discord.com/%61pi/webhooks/123/abc",
            "https://discord.com/api/webhooks/123/ab%2Fc",
            "https://discord.com/api/webhooks/123/ab.c",
            "https://discord.com/api/webhooks/-1/abc",
            "https://sub.discord.com/api/webhooks/123/abc",
            "https://discord.com./api/webhooks/123/abc",
            "discord.com/api/webhooks/123/abc",
            ""
        )

        /** When / Then **/
        urls.forEach { assertFalse(it, DiscordWebhook.isValidUrl(it)) }
    }

    /**
     * scheme과 호스트의 대소문자, 명시한 기본 포트는 HttpUrl이 정리한 값으로 보아 유효한지 검증
     *
     * Given: 대문자 scheme, 대문자가 섞인 호스트, 명시한 443 포트의 웹후크 주소
     * When: isValidUrl 호출
     * Then: 모두 true
     */
    @Test
    fun test_04_acceptsNormalizedSchemeHostAndDefaultPort() = runTest {
        /** Given **/
        val urls = listOf(
            "HTTPS://discord.com/api/webhooks/123/abc",
            "https://Discord.COM/api/webhooks/123/abc",
            "https://discord.com:443/api/webhooks/123/abc"
        )

        /** When / Then **/
        urls.forEach { assertTrue(it, DiscordWebhook.isValidUrl(it)) }
    }

    /**
     * send가 payload_json 파트와 files[0] 첨부 파트로 이루어진 multipart POST를 보내고 204를 성공으로 돌려주는지 검증
     *
     * Given: 204 응답, 머리말 한 줄, 파일 이름, 0과 음수와 줄바꿈 바이트가 섞인 첨부
     * When: 웹후크 서비스로 서버의 /api/webhooks/1/token에 send 호출
     * Then: POST, 경로, multipart Content-Type, User-Agent JustApp,
     * payload_json 파트의 이름과 JSON Content-Type과 content, attachments[0]의 id 0과 파일 이름,
     * files[0] 파트의 이름과 파일 이름과 octet-stream Content-Type과 첨부 바이트, 다른 파트 없음, Success(Unit, 204)
     */
    @Test
    fun test_05_sendPostsPayloadJsonAndFilePart() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(204))
        val content = """{"kind":"justapp-usage-log","v":1,"seq":5}"""
        val attachment = byteArrayOf(0, -1, 10, 13, 65, 127, -128)

        /** When **/
        val result = DiscordWebhook.send(
            DiscordWebhook.service,
            server.url("/api/webhooks/1/token").toString(),
            content,
            "usage-log-5.bin",
            attachment,
        )

        /** Then **/
        val success = result as NetworkResult.Success
        assertEquals(Unit, success.data)
        assertEquals(204, success.statusCode)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/webhooks/1/token", request.path)
        assertEquals("JustApp", request.getHeader("User-Agent"))
        val contentType = checkNotNull(request.getHeader("Content-Type")).toMediaType()
        assertEquals("multipart/form-data", "${contentType.type}/${contentType.subtype}")
        MultipartReader(request.body, checkNotNull(contentType.parameter("boundary"))).use { reader ->
            val payloadPart = checkNotNull(reader.nextPart())
            assertEquals("form-data; name=\"payload_json\"", payloadPart.headers["Content-Disposition"])
            assertTrue(checkNotNull(payloadPart.headers["Content-Type"]).startsWith("application/json"))
            val payload = JsonUtil.kotlinSerialization.parseToJsonElement(payloadPart.body.readUtf8()).jsonObject
            assertEquals(content, payload.getValue("content").jsonPrimitive.content)
            val attachmentInfo = payload.getValue("attachments").jsonArray.single().jsonObject
            assertEquals(0, attachmentInfo.getValue("id").jsonPrimitive.int)
            assertEquals("usage-log-5.bin", attachmentInfo.getValue("filename").jsonPrimitive.content)

            val filePart = checkNotNull(reader.nextPart())
            assertEquals("form-data; name=\"files[0]\"; filename=\"usage-log-5.bin\"", filePart.headers["Content-Disposition"])
            assertEquals("application/octet-stream", filePart.headers["Content-Type"])
            assertArrayEquals(attachment, filePart.body.readByteArray())

            assertNull(reader.nextPart())
        }
    }

    /**
     * 429 응답을 Retry-After 헤더가 담긴 HttpFailure로 돌려주는지 검증
     *
     * Given: Retry-After 헤더 2와 retry_after 본문이 있는 429 응답
     * When: send 호출
     * Then: HttpFailure(429), Retry-After 헤더 2
     */
    @Test
    fun test_06_rateLimitReturnsHttpFailureWithRetryAfter() = runTest {
        /** Given **/
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "2")
                .setBody("""{"message":"You are being rate limited.","retry_after":2.0,"global":false}""")
        )

        /** When **/
        val result = DiscordWebhook.send(
            DiscordWebhook.service,
            server.url("/api/webhooks/1/token").toString(),
            "content",
            "usage-log-1.bin",
            byteArrayOf(1),
        )

        /** Then **/
        val failure = result as NetworkResult.HttpFailure
        assertEquals(429, failure.statusCode)
        assertEquals("2", failure.headers["Retry-After"])
    }

    /**
     * 로그를 켜도 웹후크 서비스 요청은 로그를 남기지 않아 경로의 토큰이 드러나지 않는지 검증
     *
     * Given: Network 태그 로그 수집, 로그 켬, 204 응답
     * When: 웹후크 서비스로 서버의 /api/webhooks/1/secret-token에 send 호출
     * Then: 결과는 Success, Network 태그 로그 없음
     */
    @Test
    fun test_07_serviceDoesNotLogWebhookToken() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(204))

        /** When **/
        val result = DiscordWebhook.send(
            DiscordWebhook.service,
            server.url("/api/webhooks/1/secret-token").toString(),
            "content",
            "usage-log-1.bin",
            byteArrayOf(1),
        )

        /** Then **/
        assertTrue(result is NetworkResult.Success)
        assertTrue(logLines.isEmpty())
    }

    /**
     * 웹후크 서비스 설정으로 보낸 호출이 호출 전체 제한 없이 연결, 쓰기, 읽기 각 30초 제한으로 도는지 검증
     *
     * Given: 204 응답, 지나가는 호출의 제한 시간을 기록하는 인터셉터를 createService로 덧붙인 웹후크 서비스
     * When: 서버의 /api/webhooks/1/token에 send 호출
     * Then: 결과는 Success, 서버가 받은 경로는 /api/webhooks/1/token,
     * 기록한 호출 전체 제한 시간은 0, 연결, 쓰기, 읽기 제한 시간은 각 30,000ms
     */
    @Test
    fun test_08_serviceCallUsesStepTimeouts() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(204))
        var recorded: CallTimeouts? = null
        val service = DiscordWebhook.createService { builder ->
            builder.addInterceptor { chain ->
                recorded = CallTimeouts(
                    callTimeoutNanos = chain.call().timeout().timeoutNanos(),
                    connectTimeoutMillis = chain.connectTimeoutMillis(),
                    writeTimeoutMillis = chain.writeTimeoutMillis(),
                    readTimeoutMillis = chain.readTimeoutMillis(),
                )
                chain.proceed(chain.request())
            }
        }

        /** When **/
        val result = DiscordWebhook.send(
            service,
            server.url("/api/webhooks/1/token").toString(),
            "content",
            "usage-log-1.bin",
            byteArrayOf(1),
        )

        /** Then **/
        assertTrue(result is NetworkResult.Success)
        assertEquals("/api/webhooks/1/token", server.takeRequest().path)
        assertEquals(CallTimeouts(0, 30_000, 30_000, 30_000), recorded)
    }

    /** 인터셉터가 지나가는 호출에서 읽은 호출 전체 제한 시간과 단계별 제한 시간 */
    private data class CallTimeouts(
        val callTimeoutNanos: Long,
        val connectTimeoutMillis: Int,
        val writeTimeoutMillis: Int,
        val readTimeoutMillis: Int,
    )
}
