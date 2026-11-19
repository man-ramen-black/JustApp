package com.black.core.network

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [logSummary]의 결과별 로그 요약 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NetworkResultTest : BaseTest() {

    /**
     * 결과마다 상태 코드나 예외 클래스 이름만 담고 예외 메시지는 담지 않는지 검증
     *
     * Given: Success(200), HttpFailure(429), 메시지에 토큰이 든 IOException의 ConnectionFailure, SerializationException의 ParseFailure
     * When: 각 결과의 logSummary 호출
     * Then: status=200, status=429, connection=IOException, parse=SerializationException
     */
    @Test
    fun test_01_logSummaryContainsOnlyStatusOrExceptionClass() = runTest {
        /** Given **/
        val results = listOf(
            NetworkResult.Success(Unit, 200, Headers.headersOf()),
            NetworkResult.HttpFailure(429, Headers.headersOf()),
            NetworkResult.ConnectionFailure(IOException("https://discord.com/api/webhooks/1/secret-token")),
            NetworkResult.ParseFailure(SerializationException("할 일 내용")),
        )

        /** When **/
        val summaries = results.map { it.logSummary() }

        /** Then **/
        assertEquals(
            listOf("status=200", "status=429", "connection=IOException", "parse=SerializationException"),
            summaries,
        )
    }
}
