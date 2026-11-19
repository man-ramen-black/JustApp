package com.black.core.network

import okhttp3.Headers

/**
 * Retrofit 서비스 함수의 호출 결과
 *
 * 서비스 함수를 `suspend fun …: NetworkResult<Dto>`로 선언하면 [NetworkResultCallAdapterFactory]가 응답을 이 타입으로 바꿈.
 * 코루틴 취소는 결과로 바꾸지 않고 취소 예외로 전파.
 */
sealed interface NetworkResult<out T> {

    /**
     * 2xx 응답과 해석한 본문
     *
     * 본문이 없는 응답(204, 205)은 데이터 타입을 [Unit]으로 선언했을 때만 성공.
     */
    data class Success<out T>(val data: T, val statusCode: Int, val headers: Headers) : NetworkResult<T>

    /**
     * 2xx가 아닌 응답
     *
     * 오류 본문은 Retrofit이 끝까지 읽어 버퍼링하지만 내용은 담지 않음.
     * 그래서 오류 본문을 받다 끊기면 이 결과가 아니라 [ConnectionFailure].
     */
    data class HttpFailure(val statusCode: Int, val headers: Headers) : NetworkResult<Nothing>

    /**
     * 응답을 받지 못함
     *
     * 연결 실패, 타임아웃, 본문을 읽다 끊긴 경우와, 요청을 만들지 못해 보내지 않은 경우(잘못된 주소, 요청 본문 변환 실패)를 포함.
     */
    data class ConnectionFailure(val cause: Throwable) : NetworkResult<Nothing>

    /**
     * 2xx 응답의 본문 해석 실패
     *
     * 본문이 없는 응답을 [Unit]이 아닌 타입으로 받은 경우도 포함.
     */
    data class ParseFailure(val cause: Throwable) : NetworkResult<Nothing>
}

/**
 * 로그에 남길 결과 요약
 *
 * 상태 코드(`status=404`)나 예외 클래스 이름(`connection=IOException`, `parse=SerializationException`)만 담음.
 * 응답 본문과 예외 메시지에는 요청 주소(웹후크 토큰 등)나 사용자 내용이 담길 수 있기 때문.
 */
fun NetworkResult<*>.logSummary(): String = when (this) {
    is NetworkResult.Success -> "status=$statusCode"
    is NetworkResult.HttpFailure -> "status=$statusCode"
    is NetworkResult.ConnectionFailure -> "connection=${cause::class.java.simpleName}"
    is NetworkResult.ParseFailure -> "parse=${cause::class.java.simpleName}"
}
