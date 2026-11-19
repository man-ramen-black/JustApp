package com.black.core.network

import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 반환 타입이 [NetworkResult]인 서비스 함수의 호출을 [NetworkResultCall]로 감싸는 팩토리
 *
 * suspend 서비스 함수는 Retrofit 안에서 `Call<NetworkResult<T>>`로 바뀌어 이 팩토리에 옴.
 * 응답 본문은 [ResponseBody]로 받아 Retrofit에 등록된 변환기로 직접 해석함.
 * Retrofit에 해석을 맡기면 해석 실패와 연결 실패가 같은 실패 콜백으로 와서 둘을 구분할 수 없기 때문.
 */
internal object NetworkResultCallAdapterFactory : CallAdapter.Factory() {

    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java || returnType !is ParameterizedType) {
            return null
        }
        val resultType = getParameterUpperBound(0, returnType)
        if (getRawType(resultType) != NetworkResult::class.java) {
            return null
        }
        check(resultType is ParameterizedType) { "NetworkResult의 데이터 타입 지정 필요: NetworkResult<Dto>" }

        // NetworkResult<Dto>의 Dto와 그 변환기(서비스 생성 뒤 첫 호출 때 한 번 찾음)
        val dataType = getParameterUpperBound(0, resultType)
        val converter = retrofit.responseBodyConverter<Any>(dataType, annotations)
        return NetworkResultCallAdapter(dataType, converter)
    }
}

/** [ResponseBody] 호출을 [NetworkResultCall]로 바꾸는 어댑터 */
private class NetworkResultCallAdapter(
    private val dataType: Type,
    private val converter: Converter<ResponseBody, Any>,
) : CallAdapter<ResponseBody, Call<NetworkResult<Any>>> {

    override fun responseType(): Type = ResponseBody::class.java

    override fun adapt(call: Call<ResponseBody>): Call<NetworkResult<Any>> = NetworkResultCall(call, dataType, converter)
}

/**
 * 응답과 실패를 [NetworkResult]로 바꿔 늘 성공 응답으로 돌려주는 [Call]
 *
 * 코루틴이 취소되면 Retrofit이 [cancel]을 불러 OkHttp 호출을 끊음.
 * 끊긴 호출도 [NetworkResult.ConnectionFailure]로 콜백되지만, 이미 취소된 코루틴은 그 결과를 받지 않고 취소로 끝남.
 * 호출 전체 타임아웃도 OkHttp 안에서는 취소로 처리되므로 [isCanceled]로 취소와 타임아웃을 가르지 않음.
 */
private class NetworkResultCall(
    private val delegate: Call<ResponseBody>,
    private val dataType: Type,
    private val converter: Converter<ResponseBody, Any>,
) : Call<NetworkResult<Any>> {

    override fun enqueue(callback: Callback<NetworkResult<Any>>) {
        delegate.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                callback.onResponse(this@NetworkResultCall, Response.success(toResult(response)))
            }

            override fun onFailure(call: Call<ResponseBody>, failure: Throwable) {
                callback.onResponse(this@NetworkResultCall, Response.success(NetworkResult.ConnectionFailure(failure)))
            }
        })
    }

    override fun execute(): Response<NetworkResult<Any>> {
        val result = try {
            toResult(delegate.execute())
        } catch (failure: Exception) {
            NetworkResult.ConnectionFailure(failure)
        }
        return Response.success(result)
    }

    /** 받은 응답을 상태 코드와 본문에 따라 [NetworkResult]로 바꿈 */
    private fun toResult(response: Response<ResponseBody>): NetworkResult<Any> {
        // 2xx가 아니면 Retrofit이 오류 본문을 끝까지 읽어 버퍼링해 두지만 내용은 쓰지 않음
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return NetworkResult.HttpFailure(response.code(), response.headers())
        }

        // Unit은 본문 유무와 상관없이 성공(Retrofit은 204, 205의 본문을 null로 줌)
        val body = response.body()
        if (dataType == Unit::class.java) {
            body?.close()
            return NetworkResult.Success(Unit, response.code(), response.headers())
        }
        if (body == null) {
            return NetworkResult.ParseFailure(IllegalStateException("본문 없는 응답: ${response.code()}"))
        }

        // 본문 해석
        // Exception만 잡으면 그 밖의 Throwable(AssertionError 등)이 Retrofit 콜백 밖으로 나가 콜백이 불리지 않고 suspend 호출이 끝나지 않음
        return try {
            val data = body.use { converter.convert(it) }
                ?: return NetworkResult.ParseFailure(IllegalStateException("본문 해석 결과 없음: ${response.code()}"))
            NetworkResult.Success(data, response.code(), response.headers())
        } catch (failure: Throwable) {
            throwIfFatal(failure)
            NetworkResult.ParseFailure(failure)
        }
    }

    /**
     * 복구할 수 없는 오류면 결과로 바꾸지 않고 다시 던짐
     *
     * 기준은 Retrofit `Utils.throwIfFatal`과 같음.
     * 다시 던진 오류는 Retrofit 콜백과 OkHttp 비동기 호출이 차례로 다시 던져 디스패처 스레드의 처리되지 않은 예외로 앱이 종료됨.
     * 변환기 오류를 Retrofit에 맡겼을 때와 같은 동작.
     * Retrofit 2.11.0 `OkHttpCall`, OkHttp 4.12.0 `RealCall.AsyncCall` 기준 동작이라 버전을 올리면 다시 확인해야 함.
     */
    private fun throwIfFatal(failure: Throwable) {
        if (failure is VirtualMachineError || failure is ThreadDeath || failure is LinkageError) {
            throw failure
        }
    }

    override fun clone(): Call<NetworkResult<Any>> = NetworkResultCall(delegate.clone(), dataType, converter)

    override fun isExecuted(): Boolean = delegate.isExecuted

    override fun cancel() = delegate.cancel()

    override fun isCanceled(): Boolean = delegate.isCanceled

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()
}
