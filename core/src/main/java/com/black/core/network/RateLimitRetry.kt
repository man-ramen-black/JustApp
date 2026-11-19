package com.black.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 429(요청 제한) 응답을 받으면 `Retry-After`만큼 기다렸다가 같은 요청을 다시 보내는 재시도
 *
 * 노션과 디스코드가 함께 씀. `Retry-After`는 초 단위 정수와 소수를 읽고, 날짜 형식은 읽지 않음.
 */
object RateLimitRetry {

    /** 요청 제한 응답의 상태 코드 */
    const val STATUS_TOO_MANY_REQUESTS = 429

    /** `Retry-After`가 없거나 읽을 수 없을 때의 기본 대기 시간 */
    val DEFAULT_WAIT: Duration = 1.seconds

    private const val RETRY_AFTER_HEADER = "Retry-After"

    /**
     * [request]를 보내고, 429면 기다렸다가 최대 [maxRetries]번 다시 보낸 마지막 결과
     *
     * 기다릴 시간이 [maxWait]를 넘거나 다시 보낼 횟수를 다 쓰면 기다리지 않고 그 429 결과를 그대로 돌려줌.
     * 요청과 그 결과 처리를 취소되지 않게 묶어야 하면 [request] 안에서 `withContext(NonCancellable)`로 감싸거나,
     * 처리까지 함께 묶을 때는 `handle`과 `attemptContext`를 받는 [send]를 씀.
     *
     * @param defaultWait `Retry-After`가 없거나 읽을 수 없거나 음수일 때 기다릴 시간
     */
    suspend fun <T> send(
        maxRetries: Int,
        maxWait: Duration,
        defaultWait: Duration = DEFAULT_WAIT,
        request: suspend () -> NetworkResult<T>,
    ): NetworkResult<T> = send(maxRetries, maxWait, defaultWait, EmptyCoroutineContext, request) { it }

    /**
     * [request]를 보내 다시 보내지 않을 결과를 [handle]로 처리한 값
     *
     * 429면 기다렸다가 최대 [maxRetries]번 다시 보내며, 다시 보낼 429는 [handle]에 넘기지 않음.
     * 기다릴 시간이 [maxWait]를 넘거나 다시 보낼 횟수를 다 쓰면 그 429 결과를 [handle]에 넘김.
     * 요청 한 번과 그 결과의 [handle]은 [attemptContext]에서 함께 돌고, 재시도 대기는 그 밖에서 돌아 언제든 취소할 수 있음.
     * 서버에 닿은 요청의 결과를 취소로 버리면 안 될 때 [attemptContext]로 `NonCancellable`을 넘김.
     *
     * @param defaultWait `Retry-After`가 없거나 읽을 수 없거나 음수일 때 기다릴 시간
     */
    suspend fun <T, R> send(
        maxRetries: Int,
        maxWait: Duration,
        defaultWait: Duration = DEFAULT_WAIT,
        attemptContext: CoroutineContext = EmptyCoroutineContext,
        request: suspend () -> NetworkResult<T>,
        handle: suspend (NetworkResult<T>) -> R,
    ): R {
        require(maxRetries >= 0) { "maxRetries는 0 이상: $maxRetries" }

        var retryCount = 0
        while (true) {
            // 요청 한 번과, 다시 보내지 않을 결과면 그 처리까지
            val attempt = withContext(attemptContext) {
                val result = request()
                val wait = retryWait(result, defaultWait)
                if (wait != null && retryCount < maxRetries && wait <= maxWait) {
                    Attempt.Retry(wait)
                } else {
                    Attempt.Done(handle(result))
                }
            }

            // 다시 보낼 429면 대기 후 반복
            when (attempt) {
                is Attempt.Done -> return attempt.value
                is Attempt.Retry -> {
                    delay(attempt.wait)
                    retryCount++
                }
            }
        }
    }

    /** [result]가 429면 `Retry-After`를 밀리초로 올림한 대기 시간, 429가 아니면 null */
    private fun retryWait(result: NetworkResult<*>, defaultWait: Duration): Duration? {
        if (result !is NetworkResult.HttpFailure || result.statusCode != STATUS_TOO_MANY_REQUESTS) {
            return null
        }
        val seconds = result.headers[RETRY_AFTER_HEADER]?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 }
            ?: return defaultWait
        return ceil(seconds * 1000).toLong().milliseconds
    }

    /** 요청 한 번의 결말 */
    private sealed interface Attempt<out R> {
        /** 다시 보내지 않을 결과를 처리한 값 */
        data class Done<out R>(val value: R) : Attempt<R>

        /** [wait]만큼 기다렸다가 다시 보냄 */
        data class Retry(val wait: Duration) : Attempt<Nothing>
    }
}
