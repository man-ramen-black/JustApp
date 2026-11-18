package com.black.app.assistanttask.notion

import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * 노션 API 응답 한 건
 *
 * [statusCode]가 null이면 응답을 받지 못한 연결 예외. [retryAfterSeconds]는 `Retry-After` 헤더를 초로 읽은 값.
 */
data class NotionHttpResponse(val statusCode: Int?, val body: String?, val retryAfterSeconds: Long?)

/** 노션 data source 조회, 페이지 추가, 페이지 수정 요청을 보내는 창구 */
interface NotionTaskApi {
    /** [dataSourceId]의 data source를 [requestBody]로 조회 */
    suspend fun queryDataSource(token: String, dataSourceId: String, requestBody: String): NotionHttpResponse

    /** [requestBody]로 페이지 추가 */
    suspend fun createPage(token: String, requestBody: String): NotionHttpResponse

    /** [pageId] 페이지의 속성을 [requestBody]로 수정 */
    suspend fun updatePage(token: String, pageId: String, requestBody: String): NotionHttpResponse
}

/**
 * [HttpURLConnection]으로 노션 요청을 보내는 [NotionTaskApi]
 *
 * 토큰·요청 본문·응답 본문은 로그에 남기지 않음. `dataSourceId`·`pageId`는 형식 검증 없이 경로에 그대로 이어 붙이므로
 * 저장 전에 호출하는 쪽이 검증해야 함.
 *
 * [updatePage]의 PATCH는 Android 구현에서만 동작함. JVM의 [HttpURLConnection]은 PATCH를 거부
 * (`ProtocolException`)하므로 PATCH는 JVM 유닛 테스트로 확인할 수 없고 기기 테스트로 확인.
 */
class NotionHttpClient(private val baseUrl: String) : NotionTaskApi {
    @Inject
    constructor() : this(DEFAULT_BASE_URL)

    override suspend fun queryDataSource(token: String, dataSourceId: String, requestBody: String): NotionHttpResponse =
        send("POST", "/v1/data_sources/$dataSourceId/query", token, requestBody)

    override suspend fun createPage(token: String, requestBody: String): NotionHttpResponse =
        send("POST", "/v1/pages", token, requestBody)

    override suspend fun updatePage(token: String, pageId: String, requestBody: String): NotionHttpResponse =
        send("PATCH", "/v1/pages/$pageId", token, requestBody)

    /**
     * 요청을 보내고 응답을 읽음
     *
     * 막힌 소켓 읽기는 코루틴 취소로 끊기지 않으므로 읽기를 자식 코루틴에서 돌리고, 기다리는 쪽이 취소되면
     * [HttpURLConnection.disconnect]로 끊음. `NonCancellable` 안에서 부르면 취소되지 않아 끊지도 않음.
     */
    private suspend fun send(method: String, path: String, token: String, requestBody: String): NotionHttpResponse =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val body = requestBody.toByteArray(Charsets.UTF_8)
                val openedConnection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = TIMEOUT_MILLIS
                    readTimeout = TIMEOUT_MILLIS
                    doOutput = true
                    setFixedLengthStreamingMode(body.size)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Notion-Version", NOTION_VERSION)
                    setRequestProperty("Content-Type", "application/json")
                    // 기본 User-Agent(http.agent)에는 기기 모델과 빌드 정보가 담기므로 고정값으로 덮음
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                connection = openedConnection

                coroutineScope {
                    val exchange = async(Dispatchers.IO) { exchange(openedConnection, body) }
                    try {
                        exchange.await()
                    } catch (exception: CancellationException) {
                        // 막힌 읽기·쓰기를 끊어 자식 코루틴이 바로 끝나게 함
                        openedConnection.disconnect()
                        throw exception
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // 취소 때 끊은 연결의 자식 예외(SocketException 등)가 취소 대신 올라오므로 연결 실패로 남기지 않고 취소를 다시 던짐
                currentCoroutineContext().ensureActive()
                // 예외 메시지에 주소가 담길 수 있어 클래스 이름만 남김
                Log.w("[NotionHttpClient] 연결 실패: ${exception::class.java.simpleName}")
                NotionHttpResponse(null, null, null)
            } finally {
                connection?.disconnect()
            }
        }

    /** [connection]에 [body]를 쓰고 응답을 읽는 블로킹 I/O */
    private fun exchange(connection: HttpURLConnection, body: ByteArray): NotionHttpResponse {
        connection.outputStream.use { it.write(body) }

        val statusCode = connection.responseCode
        // 2xx만 inputStream, 그 밖은 errorStream이며 둘 다 null일 수 있음
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
        return NotionHttpResponse(statusCode, responseBody, connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.notion.com"
        private const val TIMEOUT_MILLIS = 30_000
        private const val NOTION_VERSION = "2025-09-03"
        private const val USER_AGENT = "JustApp-AssistantTask/1"
    }
}
