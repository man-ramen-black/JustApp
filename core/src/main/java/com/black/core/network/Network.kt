package com.black.core.network

import com.black.core.util.JsonUtil
import com.black.core.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 앱 전체가 함께 쓰는 OkHttp 클라이언트와 Retrofit 서비스 생성
 *
 * 서비스 함수는 `suspend fun …: NetworkResult<Dto>`로 선언하고, 요청과 응답 본문은 [JsonUtil.kotlinSerialization]으로 변환함.
 * 코루틴이 취소되면 OkHttp 호출도 끊김.
 * [service]는 부를 때마다 Retrofit을 새로 만들므로 서비스는 한 번 만들어 두고 씀.
 */
object Network {

    /** 연결, 요청 쓰기, 응답 읽기를 합한 호출 전체 제한 시간(초) */
    private const val CALL_TIMEOUT_SECONDS = 30L

    /**
     * 모든 요청에 쓰는 User-Agent
     *
     * 기본 값에는 라이브러리 버전이, `HttpURLConnection` 기본 값에는 기기 모델과 빌드 정보가 들어가 외부 서버로 나가므로 고정값으로 덮음.
     */
    private const val USER_AGENT = "JustApp"

    private const val LOG_TAG = "Network"

    private const val AUTHORIZATION_HEADER = "Authorization"

    /**
     * `@Url`로 전체 주소를 넘기는 서비스용 base URL
     *
     * Retrofit이 base URL을 요구해서 두는 값이며, 전체 주소를 넘긴 요청에는 쓰이지 않음.
     */
    private const val UNUSED_BASE_URL = "http://localhost/"

    /**
     * 요청 로그를 남길지 여부
     *
     * 실사용 앱(stable)도 core의 debug 변형을 써서 core만으로는 빌드 타입을 가릴 수 없음.
     * 그래서 기본은 꺼 두고 앱이 시작할 때 [setLoggingEnabled]로 켬.
     */
    @Volatile
    private var loggingEnabled = false

    /** 서비스가 지정한 값도 덮어쓰는 User-Agent 인터셉터 */
    private val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
    }

    /**
     * 모든 서비스가 함께 쓰는 OkHttp 클라이언트
     *
     * 연결 풀과 스레드를 나눠 쓰도록 하나만 둠. 개별 단계의 제한 시간은 두지 않고 호출 전체 제한 시간만 둠.
     * 로그 인터셉터는 서비스마다 수준이 달라 [service]가 User-Agent 인터셉터 뒤에 붙여 덮은 뒤의 요청을 남김.
     */
    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .build()
    }

    private val converterFactory by lazy {
        JsonUtil.kotlinSerialization.asConverterFactory("application/json".toMediaType())
    }

    /**
     * [baseUrl]을 기준으로 요청하는 [serviceClass] 서비스
     *
     * [baseUrl]은 `/`로 끝나야 함(`https://api.notion.com/`).
     *
     * @param logLevel [setLoggingEnabled]로 로그를 켰을 때 이 서비스가 남길 로그 수준.
     * 주소나 본문에 토큰, 사용자 내용이 담기는 서비스는 낮춤.
     * @param customize 이 서비스에만 덧붙일 OkHttp 설정(서비스별 제한 시간, 테스트용 인터셉터 등).
     * 공용 [client]의 `newBuilder()`에 적용해 만든 클라이언트를 쓰므로 연결 풀, 디스패처, User-Agent 인터셉터는 그대로 공유함.
     * 덧붙인 인터셉터는 User-Agent와 로그 인터셉터 뒤에서 돎.
     */
    fun <T> service(
        serviceClass: Class<T>,
        baseUrl: String,
        logLevel: NetworkLogLevel = NetworkLogLevel.HEADERS,
        customize: ((OkHttpClient.Builder) -> Unit)? = null,
    ): T {
        val serviceClient = client.newBuilder()
            .apply {
                if (logLevel != NetworkLogLevel.NONE) {
                    addInterceptor(ServiceLoggingInterceptor(logLevel))
                }
                customize?.invoke(this)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(serviceClient)
            .addConverterFactory(converterFactory)
            .addCallAdapterFactory(NetworkResultCallAdapterFactory)
            .build()
            .create(serviceClass)
    }

    /**
     * 함수마다 `@Url`로 전체 주소를 넘기는 [serviceClass] 서비스
     *
     * 디스코드 웹후크처럼 사용자가 입력한 주소로 보내는 서비스가 씀.
     * 그런 주소에는 토큰이 담길 수 있어 [logLevel] 기본값이 [NetworkLogLevel.NONE].
     * [NetworkLogLevel.HEADERS] 이상은 요청 주소 전체를 남기므로 주소에 비밀 값이 없을 때만 올림.
     *
     * @param logLevel 로그 수준이며 쓰임은 base URL을 받는 [service]와 같음
     * @param customize 이 서비스에만 덧붙일 OkHttp 설정이며 쓰임은 base URL을 받는 [service]와 같음
     */
    fun <T> service(
        serviceClass: Class<T>,
        logLevel: NetworkLogLevel = NetworkLogLevel.NONE,
        customize: ((OkHttpClient.Builder) -> Unit)? = null,
    ): T = service(serviceClass, UNUSED_BASE_URL, logLevel, customize)

    /** 서비스별 요청 로그 켜기와 끄기 */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    /**
     * [setLoggingEnabled]로 켰을 때만 [level] 수준으로 남기는 로그 인터셉터
     *
     * 토큰이 담긴 `Authorization` 헤더 값은 가림.
     */
    private class ServiceLoggingInterceptor(level: NetworkLogLevel) : Interceptor {

        private val delegate = HttpLoggingInterceptor { message -> Log.d(LOG_TAG, message) }.apply {
            this.level = when (level) {
                NetworkLogLevel.NONE -> HttpLoggingInterceptor.Level.NONE
                NetworkLogLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
                NetworkLogLevel.BODY -> HttpLoggingInterceptor.Level.BODY
            }
            redactHeader(AUTHORIZATION_HEADER)
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            if (!loggingEnabled) {
                return chain.proceed(chain.request())
            }
            return delegate.intercept(chain)
        }
    }
}

/**
 * [Network.service]의 서비스별 로그 수준
 *
 * [HEADERS]와 [BODY]는 요청 주소 전체를 남김.
 */
enum class NetworkLogLevel {
    /** 로그 없음 */
    NONE,

    /** 요청 줄, 응답 상태, 요청과 응답 헤더 */
    HEADERS,

    /** [HEADERS]에 요청과 응답 본문을 더함 */
    BODY,
}
