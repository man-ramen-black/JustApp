package com.black.app.assistanttask.notion

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket

/**
 * [NotionHttpClient.updatePage]의 PATCH 요청을 기기에서 확인하는 테스트
 *
 * JVM의 `HttpURLConnection`은 PATCH를 거부(`ProtocolException`)하고 Android 구현은 받아들여, PATCH는 기기에서만 확인됨.
 */
@RunWith(AndroidJUnit4::class)
class NotionHttpClientDeviceTest {

    /**
     * 완료 체크 요청이 PATCH 요청 줄로 나가고 응답 상태를 읽는지 검증
     *
     * Given: 200으로 응답하는 기기 안 로컬 서버
     * When: updatePage 호출
     * Then: 요청 줄 PATCH /v1/pages/p-1, 응답 상태 200
     */
    @Test
    fun test_01_updatePageSendsPatch() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            /** Given **/
            val requestLine = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val headerLines = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    val contentLength = headerLines
                        .first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(':').trim().toInt()
                    // 본문은 Content-Length만큼 읽고 버림
                    val body = CharArray(contentLength)
                    var readCount = 0
                    while (readCount < contentLength) {
                        val count = reader.read(body, readCount, contentLength - readCount)
                        if (count < 0) break
                        readCount += count
                    }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray(Charsets.ISO_8859_1))
                        flush()
                    }
                    headerLines.first()
                }
            }

            /** When **/
            val response = NotionHttpClient("http://127.0.0.1:${server.localPort}")
                .updatePage("tok", "p-1", NotionTaskFormat.updateDoneBody(true))

            /** Then **/
            assertEquals("PATCH /v1/pages/p-1 HTTP/1.1", requestLine.await())
            assertEquals(200, response.statusCode)
        }
    }
}
