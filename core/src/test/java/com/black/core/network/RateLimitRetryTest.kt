package com.black.core.network

import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okhttp3.Headers.Companion.headersOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import retrofit2.http.GET
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** [RateLimitRetry]의 429 대기, 재시도 횟수, 최대 대기, 결과 처리와 취소 동작 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RateLimitRetryTest : BaseTest() {

    @Serializable
    data class Item(val id: Int)

    interface ItemService {
        @GET("items/1")
        suspend fun getItem(): NetworkResult<Item>
    }

    /** [results]를 차례로 돌려주고 불린 횟수를 세는 요청 */
    private class FakeRequest(vararg results: NetworkResult<String>) {
        private val remaining = ArrayDeque(results.toList())

        var count = 0
            private set

        suspend fun invoke(): NetworkResult<String> {
            count++
            return remaining.removeFirst()
        }
    }

    private val success = NetworkResult.Success("done", 200, headersOf())

    /** `Retry-After`가 [retryAfter]인 429 결과(null이면 헤더 없음) */
    private fun tooManyRequests(retryAfter: String? = null): NetworkResult.HttpFailure {
        val headers = if (retryAfter == null) headersOf() else headersOf("Retry-After", retryAfter)
        return NetworkResult.HttpFailure(429, headers)
    }

    /**
     * 429의 `Retry-After` 초만큼 기다린 뒤 다시 보내 성공 결과를 돌려주는지 검증
     *
     * Given: 429(Retry-After 3) 다음 성공을 돌려주는 요청
     * When: 최대 3번, 최대 60초로 send 호출
     * Then: 성공 결과, 요청 2번, 가상 시간 3초 경과
     */
    @Test
    fun test_01_waitsRetryAfterThenResends() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests("3"), success)

        /** When **/
        val result = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { request.invoke() }

        /** Then **/
        assertEquals(success, result)
        assertEquals(2, request.count)
        assertEquals(3_000L, currentTime)
    }

    /**
     * 소수인 `Retry-After`를 밀리초로 올림해 기다리는지 검증
     *
     * Given: 429(Retry-After 1.5), 429(Retry-After 0.0001) 다음 성공을 돌려주는 요청
     * When: 최대 3번, 최대 60초로 send 호출
     * Then: 성공 결과, 요청 3번, 가상 시간 1.501초 경과
     */
    @Test
    fun test_02_waitsFractionalRetryAfter() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests("1.5"), tooManyRequests(" 0.0001 "), success)

        /** When **/
        val result = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { request.invoke() }

        /** Then **/
        assertEquals(success, result)
        assertEquals(3, request.count)
        assertEquals(1_501L, currentTime)
    }

    /**
     * 최대 재시도 횟수를 다 쓰면 마지막 429 결과를 돌려주는지 검증
     *
     * Given: 429(Retry-After 1)만 세 번 돌려주는 요청
     * When: 최대 2번, 최대 60초로 send 호출
     * Then: 429 결과, 요청 3번(첫 요청과 재시도 2번), 가상 시간 2초 경과
     */
    @Test
    fun test_03_stopsAfterMaxRetries() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests("1"), tooManyRequests("1"), tooManyRequests("1"))

        /** When **/
        val result = RateLimitRetry.send(maxRetries = 2, maxWait = 60.seconds) { request.invoke() }

        /** Then **/
        assertEquals(429, (result as NetworkResult.HttpFailure).statusCode)
        assertEquals(3, request.count)
        assertEquals(2_000L, currentTime)
    }

    /**
     * 기다릴 시간이 최대 대기 시간을 넘으면 기다리지 않고 429 결과를 바로 돌려주고, 같으면 기다리는지 검증
     *
     * Given: 429(Retry-After 61) 다음 성공을 돌려주는 요청, 429(Retry-After 60) 다음 성공을 돌려주는 요청
     * When: 두 요청을 각각 최대 3번, 최대 60초로 send 호출
     * Then: 첫 요청은 429 결과, 요청 1번, 대기 없음. 둘째 요청은 성공 결과, 요청 2번, 가상 시간 60초 경과
     */
    @Test
    fun test_04_returnsImmediatelyWhenWaitExceedsMax() = runTest {
        /** Given **/
        val tooLong = FakeRequest(tooManyRequests("61"), success)
        val atLimit = FakeRequest(tooManyRequests("60"), success)

        /** When **/
        val tooLongResult = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { tooLong.invoke() }
        val timeAfterTooLong = currentTime
        val atLimitResult = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { atLimit.invoke() }

        /** Then **/
        assertEquals(429, (tooLongResult as NetworkResult.HttpFailure).statusCode)
        assertEquals(1, tooLong.count)
        assertEquals(0L, timeAfterTooLong)
        assertEquals(success, atLimitResult)
        assertEquals(2, atLimit.count)
        assertEquals(60_000L, currentTime)
    }

    /**
     * `Retry-After`가 없거나 읽을 수 없거나 음수면 기본 대기 시간(1초)만큼 기다리는지 검증
     *
     * Given: 헤더 없는 429, Retry-After soon, Retry-After -1 다음 성공을 돌려주는 요청
     * When: 기본 대기 시간을 지정하지 않고 최대 3번, 최대 60초로 send 호출
     * Then: 성공 결과, 요청 4번, 가상 시간 3초 경과
     */
    @Test
    fun test_05_waitsDefaultWhenHeaderMissingOrInvalid() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests(), tooManyRequests("soon"), tooManyRequests("-1"), success)

        /** When **/
        val result = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { request.invoke() }

        /** Then **/
        assertEquals(success, result)
        assertEquals(4, request.count)
        assertEquals(3_000L, currentTime)
    }

    /**
     * 지정한 기본 대기 시간을 헤더 없는 429에 쓰는지 검증
     *
     * Given: 헤더 없는 429 다음 성공을 돌려주는 요청
     * When: 기본 대기 시간 5초로 send 호출
     * Then: 성공 결과, 가상 시간 5초 경과
     */
    @Test
    fun test_06_usesGivenDefaultWait() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests(), success)

        /** When **/
        val result = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds, defaultWait = 5.seconds) { request.invoke() }

        /** Then **/
        assertEquals(success, result)
        assertEquals(5_000L, currentTime)
    }

    /**
     * 429가 아닌 실패는 다시 보내지 않는지 검증
     *
     * Given: 500 응답을 돌려주는 요청, 연결 실패를 돌려주는 요청
     * When: 두 요청을 각각 send 호출
     * Then: 받은 실패를 그대로 돌려주고 요청은 각각 1번, 대기 없음
     */
    @Test
    fun test_07_doesNotRetryOtherFailures() = runTest {
        /** Given **/
        val serverError = NetworkResult.HttpFailure(500, headersOf("Retry-After", "1"))
        val connectionFailure = NetworkResult.ConnectionFailure(IOException("refused"))
        val serverErrorRequest = FakeRequest(serverError, success)
        val connectionFailureRequest = FakeRequest(connectionFailure, success)

        /** When **/
        val serverErrorResult = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { serverErrorRequest.invoke() }
        val connectionFailureResult = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { connectionFailureRequest.invoke() }

        /** Then **/
        assertEquals(serverError, serverErrorResult)
        assertEquals(1, serverErrorRequest.count)
        assertEquals(connectionFailure, connectionFailureResult)
        assertEquals(1, connectionFailureRequest.count)
        assertEquals(0L, currentTime)
    }

    /**
     * 결과 처리 함수에 다시 보낼 429는 넘기지 않고 마지막 결과만 한 번 넘기는지 검증
     *
     * Given: 429(Retry-After 1) 다음 성공을 돌려주는 요청
     * When: 받은 결과를 기록하고 상태 코드를 돌려주는 처리 함수로 send 호출
     * Then: 처리 함수는 성공 결과로 한 번만 불리고, send는 처리 함수가 돌려준 200을 돌려줌
     */
    @Test
    fun test_08_handleReceivesOnlyFinalResult() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests("1"), success)
        val handled = mutableListOf<NetworkResult<String>>()

        /** When **/
        val statusCode = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds, request = { request.invoke() }) { result ->
            handled += result
            (result as NetworkResult.Success).statusCode
        }

        /** Then **/
        assertEquals(listOf<NetworkResult<String>>(success), handled)
        assertEquals(200, statusCode)
    }

    /**
     * 시도 컨텍스트로 NonCancellable을 넘기면 요청 도중 취소돼도 그 결과를 끝까지 처리하는지 검증
     *
     * Given: 응답을 기다리게 하는 요청, NonCancellable 시도 컨텍스트로 send를 부르는 작업
     * When: 요청이 응답을 기다리는 동안 작업 취소 후 응답 도착
     * Then: 처리 함수가 그 응답으로 불리고 작업은 취소 상태
     */
    @Test
    fun test_09_nonCancellableAttemptKeepsResult() = runTest {
        /** Given **/
        val response = CompletableDeferred<NetworkResult<String>>()
        val handled = mutableListOf<NetworkResult<String>>()
        val job = launch {
            RateLimitRetry.send(
                maxRetries = 3,
                maxWait = 60.seconds,
                attemptContext = NonCancellable,
                request = { response.await() },
            ) { result ->
                handled += result
            }
        }
        runCurrent()

        /** When **/
        job.cancel()
        response.complete(success)
        job.join()

        /** Then **/
        assertEquals(listOf<NetworkResult<String>>(success), handled)
        assertTrue(job.isCancelled)
    }

    /**
     * 시도 컨텍스트가 NonCancellable이어도 재시도 대기는 취소되는지 검증
     *
     * Given: 429(Retry-After 10)를 돌려준 뒤 성공을 돌려줄 요청, NonCancellable 시도 컨텍스트로 send를 부르는 작업
     * When: 첫 요청 뒤 대기 중에 작업 취소
     * Then: 다시 보내지 않아 요청 1번, 처리 함수는 불리지 않고 작업은 취소 상태
     */
    @Test
    fun test_10_retryWaitIsCancellable() = runTest {
        /** Given **/
        val request = FakeRequest(tooManyRequests("10"), success)
        val handled = mutableListOf<NetworkResult<String>>()
        val job = launch {
            RateLimitRetry.send(
                maxRetries = 3,
                maxWait = 60.seconds,
                attemptContext = NonCancellable,
                request = { request.invoke() },
            ) { result ->
                handled += result
            }
        }
        runCurrent()

        /** When **/
        job.cancel()
        advanceUntilIdle()

        /** Then **/
        assertEquals(1, request.count)
        assertTrue(handled.isEmpty())
        assertTrue(job.isCancelled)
    }

    /**
     * 서버의 429 응답 헤더를 읽어 기다린 뒤 다시 보내는지 검증
     *
     * Given: 429(Retry-After 2) 다음 200을 응답하는 MockWebServer
     * When: 서비스 호출을 최대 3번, 최대 60초로 send 호출
     * Then: Success(Item(1)), 서버가 받은 요청 2번, 가상 시간 2초 경과
     */
    @Test
    fun test_11_retriesThroughNetwork() = runTest {
        /** Given **/
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1}"""))
        server.start()
        val service = Network.service(ItemService::class.java, server.url("/").toString())

        try {
            /** When **/
            // 실제 소켓 I/O가 끝난 뒤에만 가상 시간 대기가 돌아 둘이 겹치지 않음
            val result = RateLimitRetry.send(maxRetries = 3, maxWait = 60.seconds) { service.getItem() }

            /** Then **/
            assertEquals(Item(1), (result as NetworkResult.Success).data)
            assertEquals(2, server.requestCount)
            assertEquals(2_000L, currentTime)
        } finally {
            server.shutdown()
        }
    }
}
