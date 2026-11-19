package com.black.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * suspend 코드에서 [block]의 예외를 [Result]로 담아 돌려줌
 *
 * 이 코루틴이 취소됐을 때만 취소 예외를 다시 던지고, 살아 있는 코루틴에서 난 취소 예외는 실패 결과로 담음.
 * 취소 예외는 이 코루틴의 취소 말고도 안쪽 [withTimeout]의 시간 초과([TimeoutCancellationException]),
 * 취소된 다른 코루틴의 결과 대기에서도 남. 이런 예외까지 다시 던지면 바깥 코루틴은 실패가 아닌 취소로 조용히 끝나,
 * 소비 루프라면 이후 처리가 모두 멈추고 어디에도 실패가 남지 않음.
 * [withContext]에 [NonCancellable]을 넘긴 블록 안에서는 코루틴이 취소되지 않아 취소 예외가 모두 실패 결과가 됨.
 *
 * 표준 [runCatching]은 코루틴에서 쓰지 않음. [Throwable]을 모두 잡아 이 코루틴의 취소까지 실패 결과로 삼켜,
 * 취소된 코루틴이 멈추지 않고 다음 단계를 이어 가며 `OutOfMemoryError` 같은 [Error]도 실패 결과로 감춤.
 * 이 함수는 [Exception]만 잡고 [Error]는 그대로 던짐.
 *
 * `inline`이라 suspend 함수 안에서 부르면 [block]에서 suspend 함수를 부를 수 있음.
 */
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    // 이 코루틴이 취소됐으면 취소 전파
    currentCoroutineContext().ensureActive()
    // 살아 있는데 난 취소 예외(안쪽 withTimeout의 시간 초과 등)는 실패
    Result.failure(cancellation)
} catch (exception: Exception) {
    Result.failure(exception)
}
