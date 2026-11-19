package com.black.core.network

import com.black.core.util.JsonUtil
import com.black.test.BaseTest
import com.black.test.captureDebugLogs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [Network]의 클라이언트 설정과 [NetworkResult] 변환을 MockWebServer로 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NetworkTest : BaseTest() {

    @Serializable
    data class Item(val id: Int, val name: String)

    @Serializable
    data class DoneUpdate(val done: Boolean)

    /** 해석하면 [ErrorThrowingSerializer]가 Exception이 아닌 Throwable을 던지는 응답 타입 */
    @Serializable(with = ErrorThrowingSerializer::class)
    class BrokenItem

    /** 해석 중 AssertionError를 던지는 직렬화기 */
    object ErrorThrowingSerializer : KSerializer<BrokenItem> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BrokenItem", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BrokenItem) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): BrokenItem = throw AssertionError("broken")
    }

    /** base URL 기준으로 요청하는 테스트 서비스 */
    interface ItemService {
        @GET("items/1")
        suspend fun getItem(): NetworkResult<Item>

        @Headers("User-Agent: Custom-Agent")
        @GET("items/1")
        suspend fun getItemWithUserAgent(): NetworkResult<Item>

        @GET("items/1")
        suspend fun getItemWithToken(@Header("Authorization") token: String): NetworkResult<Item>

        @PATCH("items/{id}")
        suspend fun updateItem(@Path("id") id: String, @Body body: DoneUpdate): NetworkResult<Item>

        @POST("items/1/archive")
        suspend fun archive(): NetworkResult<Unit>

        @GET("items/1")
        suspend fun getBrokenItem(): NetworkResult<BrokenItem>
    }

    /** `@Url`로 전체 주소를 넘기는 테스트 서비스 */
    interface UrlService {
        @GET
        suspend fun getItem(@Url url: String): NetworkResult<Item>
    }

    private lateinit var server: MockWebServer

    private lateinit var service: ItemService

    @Before
    fun setupServer() {
        server = MockWebServer()
        server.start()
        service = Network.service(ItemService::class.java, server.url("/").toString())
    }

    @After
    fun shutdownServer() {
        Network.setLoggingEnabled(false)
        server.shutdown()
    }

    /**
     * 2xx 응답의 본문을 해석해 상태 코드, 헤더와 함께 돌려주는지 검증
     *
     * Given: 200 응답, Item JSON 본문, X-Request-Id 헤더
     * When: getItem 호출
     * Then: Success(Item(1, "first"), 200)과 X-Request-Id 헤더
     */
    @Test
    fun test_01_successParsesBody() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Request-Id", "abc").setBody("""{"id":1,"name":"first"}"""))

        /** When **/
        val result = service.getItem()

        /** Then **/
        val success = result as NetworkResult.Success
        assertEquals(Item(1, "first"), success.data)
        assertEquals(200, success.statusCode)
        assertEquals("abc", success.headers["X-Request-Id"])
    }

    /**
     * 2xx가 아닌 응답을 상태 코드와 헤더를 담은 HttpFailure로 돌려주는지 검증
     *
     * Given: 404 응답, 오류 본문, X-Request-Id 헤더
     * When: getItem 호출
     * Then: HttpFailure(404)와 X-Request-Id 헤더
     */
    @Test
    fun test_02_nonSuccessStatusReturnsHttpFailure() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(404).setHeader("X-Request-Id", "missing").setBody("""{"message":"not found"}"""))

        /** When **/
        val result = service.getItem()

        /** Then **/
        val failure = result as NetworkResult.HttpFailure
        assertEquals(404, failure.statusCode)
        assertEquals("missing", failure.headers["X-Request-Id"])
    }

    /**
     * 연결할 수 없는 서버를 부르면 ConnectionFailure로 돌려주는지 검증
     *
     * Given: 시작한 뒤 바로 닫은 서버의 주소로 만든 서비스
     * When: getItem 호출
     * Then: IOException을 원인으로 담은 ConnectionFailure
     */
    @Test
    fun test_03_unreachableServerReturnsConnectionFailure() = runTest {
        /** Given **/
        val closedServer = MockWebServer()
        closedServer.start()
        val closedUrl = closedServer.url("/").toString()
        closedServer.shutdown()
        val closedService = Network.service(ItemService::class.java, closedUrl)

        /** When **/
        val result = closedService.getItem()

        /** Then **/
        val failure = result as NetworkResult.ConnectionFailure
        assertTrue(failure.cause is IOException)
    }

    /**
     * 2xx 응답의 본문을 해석하지 못하면 ParseFailure로 돌려주는지 검증
     *
     * Given: 200 응답, 필수 필드 name이 빠진 본문
     * When: getItem 호출
     * Then: SerializationException을 원인으로 담은 ParseFailure
     */
    @Test
    fun test_04_invalidBodyReturnsParseFailure() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1}"""))

        /** When **/
        val result = service.getItem()

        /** Then **/
        val failure = result as NetworkResult.ParseFailure
        assertTrue(failure.cause is SerializationException)
    }

    /**
     * Unit으로 선언한 함수가 본문 없는 응답과 본문 있는 응답을 모두 성공으로 돌려주는지 검증
     *
     * Given: 204 응답, 이어서 본문이 있는 200 응답
     * When: archive를 두 번 호출
     * Then: Success(Unit, 204), Success(Unit, 200)
     */
    @Test
    fun test_05_unitAcceptsNoContentAndBody() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"archived":true}"""))

        /** When **/
        val noContent = service.archive()
        val withBody = service.archive()

        /** Then **/
        assertEquals(Unit, (noContent as NetworkResult.Success).data)
        assertEquals(204, noContent.statusCode)
        assertEquals(Unit, (withBody as NetworkResult.Success).data)
        assertEquals(200, withBody.statusCode)
    }

    /**
     * Unit이 아닌 타입으로 선언한 함수가 본문 없는 응답을 ParseFailure로 돌려주는지 검증
     *
     * Given: 204 응답
     * When: getItem 호출
     * Then: ParseFailure
     */
    @Test
    fun test_06_noContentForDataTypeReturnsParseFailure() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(204))

        /** When **/
        val result = service.getItem()

        /** Then **/
        assertTrue(result is NetworkResult.ParseFailure)
    }

    /**
     * PATCH 요청을 경로와 JSON 본문과 함께 보내는지 검증
     *
     * Given: 200 응답, Item JSON 본문
     * When: updateItem("page-1", DoneUpdate(true)) 호출
     * Then: 서버가 받은 요청은 PATCH /items/page-1, 본문은 DoneUpdate(true), Content-Type은 JSON, 결과는 Success
     */
    @Test
    fun test_07_patchSendsMethodAndBody() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"done"}"""))

        /** When **/
        val result = service.updateItem("page-1", DoneUpdate(true))

        /** Then **/
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/items/page-1", request.path)
        assertEquals(DoneUpdate(true), JsonUtil.kotlinSerialization.decodeFromString(DoneUpdate.serializer(), request.body.readUtf8()))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(Item(1, "done"), (result as NetworkResult.Success).data)
    }

    /**
     * User-Agent를 서비스가 지정한 값까지 고정값 JustApp으로 덮는지 검증
     *
     * Given: 200 응답 두 개
     * When: User-Agent를 지정하지 않은 getItem과 Custom-Agent를 지정한 getItemWithUserAgent 호출
     * Then: 두 요청 모두 User-Agent가 JustApp
     */
    @Test
    fun test_08_userAgentIsOverwritten() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))

        /** When **/
        service.getItem()
        service.getItemWithUserAgent()

        /** Then **/
        assertEquals("JustApp", server.takeRequest().getHeader("User-Agent"))
        assertEquals("JustApp", server.takeRequest().getHeader("User-Agent"))
    }

    /**
     * `@Header("Authorization")`로 넘긴 토큰을 그대로 보내는지 검증
     *
     * Given: 200 응답
     * When: getItemWithToken("Bearer secret-token") 호출
     * Then: 서버가 받은 Authorization 헤더가 Bearer secret-token
     */
    @Test
    fun test_09_authorizationHeaderIsSent() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))

        /** When **/
        service.getItemWithToken("Bearer secret-token")

        /** Then **/
        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))
    }

    /**
     * 로그를 켜지 않으면 요청과 응답 로그를 남기지 않는지 검증
     *
     * Given: 로그 수집, 200 응답
     * When: 로그를 켜지 않고 getItem 호출
     * Then: Network 태그 로그 없음
     */
    @Test
    fun test_10_loggingIsOffByDefault() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))

        /** When **/
        service.getItem()

        /** Then **/
        assertTrue(logLines.isEmpty())
    }

    /**
     * BODY 수준 서비스는 로그를 켜면 요청과 응답 본문까지 남기고 Authorization 값은 가리는지 검증
     *
     * Given: 로그 수집, 로그 켬, 200 응답, BODY 수준으로 만든 서비스
     * When: getItemWithToken("Bearer secret-token") 호출
     * Then: 요청 줄과 응답 본문 로그가 있고, Authorization 줄은 값이 가려지며, 토큰 문자열은 어느 줄에도 없음
     */
    @Test
    fun test_11_loggingRedactsAuthorization() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"logged"}"""))
        val bodyLogService = Network.service(ItemService::class.java, server.url("/").toString(), NetworkLogLevel.BODY)

        /** When **/
        bodyLogService.getItemWithToken("Bearer secret-token")

        /** Then **/
        val logs = logLines.toList()
        assertTrue(logs.any { it.contains("--> GET") })
        assertTrue(logs.any { it.contains("logged") })
        assertTrue(logs.any { it.contains("Authorization: ██") })
        assertFalse(logs.any { it.contains("secret-token") })
    }

    /**
     * base URL 없이 만든 서비스가 `@Url`로 넘긴 전체 주소로 요청하는지 검증
     *
     * Given: 200 응답, base URL 없이 만든 UrlService
     * When: 서버의 /hooks/1 전체 주소로 getItem 호출
     * Then: 서버가 받은 경로가 /hooks/1이고 결과는 Success
     */
    @Test
    fun test_12_urlServiceUsesFullUrl() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":2,"name":"hook"}"""))
        val urlService = Network.service(UrlService::class.java)

        /** When **/
        val result = urlService.getItem(server.url("/hooks/1").toString())

        /** Then **/
        assertEquals("/hooks/1", server.takeRequest().path)
        assertEquals(Item(2, "hook"), (result as NetworkResult.Success).data)
    }

    /**
     * 코루틴을 취소하면 결과 대신 취소로 끝나고 OkHttp 호출도 끊기는지 검증
     *
     * Given: 요청을 받고 응답하지 않는 서버, 실제 스레드에서 getItem을 기다리는 작업
     * When: 서버가 요청을 받은 뒤 작업 취소
     * Then: 작업 안에서 CancellationException이 나고 결과는 없으며, 진행 중인 OkHttp 호출이 호출 제한 시간(30초)보다 훨씬 빨리 0이 됨
     */
    @Test
    fun test_13_cancellationPropagatesAndCancelsCall() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        var thrown: Throwable? = null
        var returned: NetworkResult<Item>? = null
        // 실제 소켓을 기다리는 동안 가상 시간이 끼지 않도록 IO 스레드에서 실행
        val job = launch(Dispatchers.IO) {
            try {
                returned = service.getItem()
            } catch (cancellation: CancellationException) {
                thrown = cancellation
                throw cancellation
            }
        }
        withContext(Dispatchers.IO) {
            server.takeRequest(5, TimeUnit.SECONDS)
        }

        /** When **/
        job.cancelAndJoin()

        /** Then **/
        assertTrue(thrown is CancellationException)
        assertNull(returned)
        assertTrue(job.isCancelled)
        // 실제 시간으로 대기(IO 디스패처의 delay, withTimeout은 가상 시간을 쓰지 않음)
        withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                while (Network.client.dispatcher.runningCallsCount() > 0) {
                    delay(20.milliseconds)
                }
            }
        }
        assertEquals(0, Network.client.dispatcher.runningCallsCount())
    }

    /**
     * customize로 덧붙인 인터셉터가 그 서비스의 요청에만 적용되는지 검증
     *
     * Given: 200 응답 두 개, X-Service 헤더를 넣는 인터셉터를 customize로 붙인 서비스
     * When: customize한 서비스와 공용 서비스로 차례로 getItem 호출
     * Then: 첫 요청에만 X-Service 헤더가 있음
     */
    @Test
    fun test_14_customizeInterceptorAppliesOnlyToThatService() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))
        val customizedService = Network.service(ItemService::class.java, server.url("/").toString()) { builder ->
            builder.addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Service", "custom").build())
            }
        }

        /** When **/
        customizedService.getItem()
        service.getItem()

        /** Then **/
        assertEquals("custom", server.takeRequest().getHeader("X-Service"))
        assertNull(server.takeRequest().getHeader("X-Service"))
    }

    /**
     * customize로 호출 전체 제한 시간을 바꾸면 그 서비스에만 적용되는지 검증
     *
     * Given: 헤더를 500ms 늦게 보내는 200 응답 두 개, 호출 전체 제한 시간을 100ms로 줄인 서비스
     * When: 줄인 서비스와 공용 서비스로 차례로 getItem 호출
     * Then: 줄인 서비스는 InterruptedIOException을 원인으로 담은 ConnectionFailure, 공용 서비스는 Success
     */
    @Test
    fun test_15_customizeChangesCallTimeoutOfThatService() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody("""{"id":1,"name":"slow"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody("""{"id":1,"name":"slow"}"""))
        val shortTimeoutService = Network.service(ItemService::class.java, server.url("/").toString()) { builder ->
            builder.callTimeout(100, TimeUnit.MILLISECONDS)
        }

        /** When **/
        val timedOut = shortTimeoutService.getItem()
        val shared = service.getItem()

        /** Then **/
        val failure = timedOut as NetworkResult.ConnectionFailure
        assertTrue(failure.cause is InterruptedIOException)
        assertEquals(Item(1, "slow"), (shared as NetworkResult.Success).data)
    }

    /**
     * customize한 서비스에도 공용 User-Agent가 붙는지 검증
     *
     * Given: 200 응답 두 개, 읽기 제한 시간을 customize한 ItemService와 `@Url` 서비스
     * When: Custom-Agent를 지정한 getItemWithUserAgent와 서버 전체 주소로 `@Url` 서비스의 getItem 호출
     * Then: 두 요청 모두 User-Agent가 JustApp
     */
    @Test
    fun test_16_customizedServiceKeepsUserAgent() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"first"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":2,"name":"hook"}"""))
        val customize: (OkHttpClient.Builder) -> Unit = { builder -> builder.readTimeout(5, TimeUnit.SECONDS) }
        val customizedService = Network.service(ItemService::class.java, server.url("/").toString(), customize = customize)
        val customizedUrlService = Network.service(UrlService::class.java, customize = customize)

        /** When **/
        customizedService.getItemWithUserAgent()
        customizedUrlService.getItem(server.url("/hooks/1").toString())

        /** Then **/
        assertEquals("JustApp", server.takeRequest().getHeader("User-Agent"))
        assertEquals("JustApp", server.takeRequest().getHeader("User-Agent"))
    }

    /**
     * customize가 받는 빌더가 공용 클라이언트의 연결 풀과 디스패처를 쓰는지 검증
     *
     * Given: customize로 넘어온 빌더를 담아 두는 서비스 생성
     * When: 담아 둔 빌더로 클라이언트 생성
     * Then: 연결 풀과 디스패처가 공용 클라이언트와 같은 객체
     */
    @Test
    fun test_17_customizeSharesConnectionPoolAndDispatcher() = runTest {
        /** Given **/
        var receivedBuilder: OkHttpClient.Builder? = null
        Network.service(UrlService::class.java) { builder -> receivedBuilder = builder }

        /** When **/
        val customizedClient = checkNotNull(receivedBuilder).build()

        /** Then **/
        assertSame(Network.client.connectionPool, customizedClient.connectionPool)
        assertSame(Network.client.dispatcher, customizedClient.dispatcher)
    }

    /**
     * 2xx 본문 해석 중 Exception이 아닌 Throwable이 나도 결과가 돌아오는지 검증
     *
     * Given: 200 응답, 해석하면 AssertionError를 던지는 응답 타입
     * When: getBrokenItem 호출
     * Then: 호출이 끝나고 AssertionError를 원인으로 담은 ParseFailure
     */
    @Test
    fun test_18_nonExceptionThrowableWhileParsingReturnsParseFailure() = runTest {
        /** Given **/
        server.enqueue(MockResponse().setResponseCode(200).setBody(""""broken""""))

        /** When **/
        val result = service.getBrokenItem()

        /** Then **/
        val failure = result as NetworkResult.ParseFailure
        assertTrue(failure.cause is AssertionError)
    }

    /**
     * 기본 수준(HEADERS) 서비스는 로그를 켜도 요청과 응답 본문을 남기지 않는지 검증
     *
     * Given: 로그 수집, 로그 켬, 200 응답
     * When: 기본 수준으로 만든 서비스로 updateItem 호출
     * Then: 요청 줄과 응답 상태 로그는 있고, 요청 본문(DoneUpdate)과 응답 본문(name 값)은 어느 줄에도 없음
     */
    @Test
    fun test_19_headersLevelOmitsBodies() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"name":"secret-content"}"""))

        /** When **/
        service.updateItem("page-1", DoneUpdate(true))

        /** Then **/
        val logs = logLines.toList()
        assertTrue(logs.any { it.contains("--> PATCH") })
        assertTrue(logs.any { it.contains("<-- 200") })
        assertFalse(logs.any { it.contains("secret-content") })
        assertFalse(logs.any { it.contains("\"done\"") })
    }

    /**
     * NONE 수준 서비스는 로그를 켜도 아무 로그도 남기지 않는지 검증
     *
     * Given: 로그 수집, 로그 켬, 200 응답, NONE 수준으로 만든 `@Url` 서비스
     * When: 서버의 /hooks/secret-token 전체 주소로 getItem 호출
     * Then: Network 태그 로그 없음, 결과는 Success
     */
    @Test
    fun test_20_noneLevelLogsNothing() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":2,"name":"hook"}"""))
        val silentService = Network.service(UrlService::class.java, NetworkLogLevel.NONE)

        /** When **/
        val result = silentService.getItem(server.url("/hooks/secret-token").toString())

        /** Then **/
        assertTrue(logLines.isEmpty())
        assertEquals(Item(2, "hook"), (result as NetworkResult.Success).data)
    }

    /**
     * 2xx 응답 본문을 받다 연결이 끊기면 ParseFailure가 아니라 ConnectionFailure인지 검증
     *
     * Given: 본문 절반을 보낸 뒤 연결을 끊는 200 응답 두 개
     * When: 데이터 타입으로 받는 getItem과 Unit으로 받는 archive 호출
     * Then: 둘 다 IOException을 원인으로 담은 ConnectionFailure
     */
    @Test
    fun test_21_disconnectDuringSuccessBodyReturnsConnectionFailure() = runTest {
        /** Given **/
        val longBody = """{"id":1,"name":"${"x".repeat(4096)}"}"""
        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(longBody).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            )
        }

        /** When **/
        val dataResult = service.getItem()
        val unitResult = service.archive()

        /** Then **/
        assertTrue((dataResult as NetworkResult.ConnectionFailure).cause is IOException)
        assertTrue((unitResult as NetworkResult.ConnectionFailure).cause is IOException)
    }

    /**
     * 2xx가 아닌 응답의 오류 본문을 받다 연결이 끊기면 HttpFailure가 아니라 ConnectionFailure인지 검증
     *
     * Given: 본문 절반을 보낸 뒤 연결을 끊는 500 응답
     * When: getItem 호출
     * Then: IOException을 원인으로 담은 ConnectionFailure
     */
    @Test
    fun test_22_disconnectDuringErrorBodyReturnsConnectionFailure() = runTest {
        /** Given **/
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("error-${"x".repeat(4096)}")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        /** When **/
        val result = service.getItem()

        /** Then **/
        assertTrue((result as NetworkResult.ConnectionFailure).cause is IOException)
    }

    /**
     * 로그 수준을 정하지 않은 `@Url` 서비스는 로그를 켜도 아무 로그도 남기지 않는지 검증
     *
     * Given: 로그 수집, 로그 켬, 200 응답, 로그 수준 없이 만든 `@Url` 서비스
     * When: 서버의 /hooks/secret-token 전체 주소로 getItem 호출
     * Then: Network 태그 로그 없음, 결과는 Success
     */
    @Test
    fun test_23_urlServiceLogsNothingByDefault() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":2,"name":"hook"}"""))
        val urlService = Network.service(UrlService::class.java)

        /** When **/
        val result = urlService.getItem(server.url("/hooks/secret-token").toString())

        /** Then **/
        assertTrue(logLines.isEmpty())
        assertEquals(Item(2, "hook"), (result as NetworkResult.Success).data)
    }

    /**
     * `@Url` 서비스에 HEADERS 수준을 넘기면 로그를 켰을 때 요청 줄을 남기는지 검증
     *
     * Given: 로그 수집, 로그 켬, 200 응답, HEADERS 수준으로 만든 `@Url` 서비스
     * When: 서버의 /hooks/1 전체 주소로 getItem 호출
     * Then: 전체 주소를 담은 `--> GET` 요청 줄 로그가 있고, 결과는 Success
     */
    @Test
    fun test_24_urlServiceLogsWithHeadersLevel() = runTest {
        /** Given **/
        val logLines = captureDebugLogs("Network")
        Network.setLoggingEnabled(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":2,"name":"hook"}"""))
        val headersLogService = Network.service(UrlService::class.java, NetworkLogLevel.HEADERS)
        val hookUrl = server.url("/hooks/1").toString()

        /** When **/
        val result = headersLogService.getItem(hookUrl)

        /** Then **/
        val logs = logLines.toList()
        assertTrue(logs.any { it.contains("--> GET $hookUrl") })
        assertEquals(Item(2, "hook"), (result as NetworkResult.Success).data)
    }
}
