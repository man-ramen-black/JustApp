package com.black.app.assistanttask.notion

import android.util.Log
import com.black.test.BaseTest
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.net.InetAddress
import java.net.ServerSocket

/** [NotionHttpClient]가 보내는 요청과 응답 읽기 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotionHttpClientTest : BaseTest() {

    /**
     * 요청 하나를 받아 요청 줄과 헤더 줄을 돌려주고 [responseText]로 응답
     *
     * 본문은 Content-Length만큼 읽고 버림. ISO-8859-1은 바이트와 문자가 1:1이라 문자 수가 바이트 수와 같음.
     */
    private fun ServerSocket.receiveOneRequest(responseText: String): List<String> = accept().use { socket ->
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
            write(responseText.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
        headerLines
    }

    private fun headerValues(lines: List<String>, name: String): List<String> =
        lines.filter { it.startsWith("$name:", ignoreCase = true) }.map { it.substringAfter(':').trim() }

    /**
     * 노션 요청 줄과 헤더가 맞는지 검증
     *
     * Given: 200으로 응답하는 로컬 서버
     * When: queryDataSource 호출
     * Then: 요청 줄 POST /v1/data_sources/ds-1/query, Authorization·Notion-Version·Content-Type·User-Agent 헤더, 200과 본문
     */
    @Test
    fun test_01_sendsNotionHeadersAndPath() = runTest {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            /** Given **/
            val request = async(Dispatchers.IO) {
                server.receiveOneRequest("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")
            }

            /** When **/
            val response = NotionHttpClient("http://127.0.0.1:${server.localPort}")
                .queryDataSource("tok", "ds-1", """{"page_size":100}""")

            /** Then **/
            val lines = request.await()
            assertEquals("POST /v1/data_sources/ds-1/query HTTP/1.1", lines.first())
            assertEquals(listOf("Bearer tok"), headerValues(lines, "Authorization"))
            assertEquals(listOf("2025-09-03"), headerValues(lines, "Notion-Version"))
            assertEquals(listOf("application/json"), headerValues(lines, "Content-Type"))
            assertEquals(listOf("JustApp-AssistantTask/1"), headerValues(lines, "User-Agent"))
            assertEquals(NotionHttpResponse(200, "{}", null), response)
        }
    }

    /**
     * 오류 응답의 본문과 Retry-After를 읽는지 검증
     *
     * Given: 429, Retry-After 2, 본문으로 응답하는 로컬 서버
     * When: queryDataSource 호출
     * Then: statusCode 429, retryAfterSeconds 2, body 그대로
     */
    @Test
    fun test_02_readsErrorBodyAndRetryAfter() = runTest {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            /** Given **/
            val errorBody = """{"object":"error"}"""
            async(Dispatchers.IO) {
                server.receiveOneRequest(
                    "HTTP/1.1 429 Too Many Requests\r\nRetry-After: 2\r\n" +
                        "Content-Length: ${errorBody.length}\r\nConnection: close\r\n\r\n$errorBody"
                )
            }

            /** When **/
            val response = NotionHttpClient("http://127.0.0.1:${server.localPort}")
                .queryDataSource("tok", "ds-1", "{}")

            /** Then **/
            assertEquals(NotionHttpResponse(429, errorBody, 2L), response)
        }
    }

    /**
     * 연결 예외가 상태 코드 null로 돌아오는지 검증
     *
     * Given: 닫힌 포트
     * When: queryDataSource 호출
     * Then: statusCode·body·retryAfterSeconds 모두 null, 경고 로그에 예외 클래스 이름만 있고 주소·토큰은 없음
     */
    @Test
    fun test_03_connectionFailureReturnsNullStatus() = runTest {
        /** Given **/
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

        /** When **/
        val response = NotionHttpClient("http://127.0.0.1:$closedPort").queryDataSource("tok", "ds-1", "{}")

        /** Then **/
        assertNull(response.statusCode)
        assertEquals(NotionHttpResponse(null, null, null), response)
        verify { Log.w(any<String>(), match<String> { it.contains("연결 실패: ConnectException") }) }
        verify(exactly = 0) { Log.w(any<String>(), match<String> { it.contains("127.0.0.1") || it.contains("tok") }) }
    }

    /**
     * 페이지 추가 요청의 줄과 헤더가 조회와 같은지 검증
     *
     * Given: 200과 본문으로 응답하는 로컬 서버
     * When: createPage 호출
     * Then: 요청 줄 POST /v1/pages, 조회와 같은 헤더, 응답 상태와 본문
     */
    @Test
    fun test_04_createPageSendsPostWithSameHeaders() = runTest {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            /** Given **/
            val request = async(Dispatchers.IO) {
                server.receiveOneRequest("HTTP/1.1 200 OK\r\nContent-Length: 11\r\nConnection: close\r\n\r\n{\"id\":\"p1\"}")
            }

            /** When **/
            val response = NotionHttpClient("http://127.0.0.1:${server.localPort}").createPage("tok", """{"parent":{}}""")

            /** Then **/
            val lines = request.await()
            assertEquals("POST /v1/pages HTTP/1.1", lines.first())
            assertEquals(listOf("Bearer tok"), headerValues(lines, "Authorization"))
            assertEquals(listOf("2025-09-03"), headerValues(lines, "Notion-Version"))
            assertEquals(listOf("application/json"), headerValues(lines, "Content-Type"))
            assertEquals(listOf("JustApp-AssistantTask/1"), headerValues(lines, "User-Agent"))
            assertEquals(NotionHttpResponse(200, """{"id":"p1"}""", null), response)
        }
    }

    /**
     * 응답하지 않는 서버에서 호출 코루틴을 취소하면 바로 끝나는지 검증
     *
     * Given: 연결은 받지만 아무것도 쓰지 않는 로컬 서버
     * When: queryDataSource 호출 200ms 뒤 호출한 코루틴 취소
     * Then: 30초 읽기 시간 제한을 기다리지 않고 2초 안에 끝나며, 끊긴 연결을 연결 실패 경고로 남기지 않음
     */
    @Test
    fun test_05_cancelDisconnectsBlockedRead() = runTest {
        // 실제 소켓 대기와 경과 시간을 재므로 가상 시간이 아닌 실제 시간 디스패처에서 실행
        withContext(Dispatchers.Default) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                /** Given **/
                val accepted = async(Dispatchers.IO) { server.accept() }
                val startMillis = System.currentTimeMillis()

                /** When **/
                withTimeoutOrNull(200) {
                    NotionHttpClient("http://127.0.0.1:${server.localPort}").queryDataSource("tok", "ds-1", "{}")
                }

                /** Then **/
                val elapsedMillis = System.currentTimeMillis() - startMillis
                assertTrue("elapsed=$elapsedMillis", elapsedMillis < 2_000)
                verify(exactly = 0) { Log.w(any<String>(), match<String> { it.contains("연결 실패") }) }
                accepted.await().close()
            }
        }
    }
}
