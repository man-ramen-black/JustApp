package com.black.app.usagelog.upload

import com.black.test.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.net.InetAddress
import java.net.ServerSocket

/** [DiscordWebhookClient]가 보내는 요청 헤더 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DiscordWebhookClientTest : BaseTest() {

    /**
     * 요청 하나를 받아 헤더 줄을 돌려주고 204로 응답
     *
     * 본문은 Content-Length만큼 읽고 버림. ISO-8859-1은 바이트와 문자가 1:1이라 문자 수가 바이트 수와 같음.
     */
    private fun ServerSocket.receiveOneRequest(): List<String> = accept().use { socket ->
        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val headerLines = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val contentLength = headerLines
            .first { it.startsWith("Content-Length:", ignoreCase = true) }
            .substringAfter(':').trim().toInt()
        val body = CharArray(contentLength)
        var readCount = 0
        while (readCount < contentLength) {
            val count = reader.read(body, readCount, contentLength - readCount)
            if (count < 0) break
            readCount += count
        }
        socket.getOutputStream().apply {
            write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            flush()
        }
        headerLines
    }

    /**
     * 기기 정보가 담기지 않는 고정 User-Agent로 보내는지 검증
     *
     * Given: 요청 하나를 받는 로컬 서버
     * When: 그 서버 주소로 send 호출
     * Then: Success, User-Agent 헤더가 "JustApp-UsageLog/1" 하나
     */
    @Test
    fun test_01_sendsFixedUserAgent() = runTest {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            /** Given **/
            val request = async(Dispatchers.IO) { server.receiveOneRequest() }

            /** When **/
            val result = DiscordWebhookClient().send(
                "http://127.0.0.1:${server.localPort}/api/webhooks/1/token",
                """{"kind":"justapp-usage-log","v":1,"seq":1}""",
                "usage-log-1.bin",
                byteArrayOf(1, 2, 3),
            )

            /** Then **/
            assertEquals(WebhookResult.Success, result)
            val userAgents = request.await()
                .filter { it.startsWith("User-Agent:", ignoreCase = true) }
                .map { it.substringAfter(':').trim() }
            assertEquals(listOf("JustApp-UsageLog/1"), userAgents)
        }
    }
}
