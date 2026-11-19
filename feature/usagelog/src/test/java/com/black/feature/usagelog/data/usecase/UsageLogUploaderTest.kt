package com.black.feature.usagelog.data.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.core.crypto.AesGcmCipher
import com.black.core.network.Network
import com.black.core.network.discord.DiscordWebhookPayload
import com.black.core.network.discord.DiscordWebhookService
import com.black.core.system.SmsMessage
import com.black.core.util.JsonUtil
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.data.database.UsageLogDatabase
import com.black.feature.usagelog.data.database.UsageLogEntity
import com.black.feature.usagelog.data.datastore.UsageLogDataStore
import com.black.feature.usagelog.model.LastSendResult
import com.black.feature.usagelog.model.ScreenOnRecord
import com.black.feature.usagelog.model.SendReason
import com.black.feature.usagelog.model.SmsProgress
import com.black.feature.usagelog.model.UploadResult
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [UsageLogUploader]의 묶음 전송, 전송됨 표시, 재시도 분류를 MockWebServer 웹후크 서버로 검증 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogUploaderTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = UsageLogSettingsRepository(context)
    private lateinit var database: UsageLogDatabase
    private lateinit var repository: UsageLogRepository
    private val collector = mockk<UsageStatsCollector>(relaxed = true)
    private val smsCollector = mockk<SmsCollector>(relaxed = true)
    private val deviceInfo = DeviceInfo("SM-S921N", "com.black.app") { 540 }
    private val now = 1_763_000_000_000L
    private lateinit var server: MockWebServer
    private lateinit var webhookService: DiscordWebhookService

    /** 서버가 받은 웹후크 요청 하나의 경로, 머리말(payload의 content), 첨부 파일 이름, 첨부 바이트 */
    private class ReceivedWebhook(val path: String, val content: String, val fileName: String, val attachment: ByteArray)

    /**
     * 각 테스트 전 저장값 삭제와 메모리 DB 준비, 웹후크 서버 준비
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유되기 때문.
     * 서버는 넣어 둔 응답이 없으면 디스코드 웹후크 성공 응답인 204로 답함.
     */
    @Before
    fun setupUploader() {
        runTest { UsageLogDataStore(context).clear() }
        database = Room.inMemoryDatabaseBuilder(context, UsageLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UsageLogRepository(database.dao())
        server = MockWebServer()
        server.dispatcher = QueueDispatcher().apply { setFailFast(MockResponse().setResponseCode(204)) }
        server.start()
        webhookService = createWebhookService(server.url("/"))
    }

    @After
    fun closeDatabase() {
        database.close()
        server.shutdown()
    }

    /** 디스코드 웹후크 주소로 가는 요청의 scheme, host, port를 [target]으로 바꿔 보내는 웹후크 서비스 */
    private fun createWebhookService(target: HttpUrl): DiscordWebhookService =
        Network.service(DiscordWebhookService::class.java) { builder ->
            builder.addInterceptor(Interceptor { chain ->
                val url = chain.request().url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            })
        }

    private fun createUploader(
        settingsRepository: UsageLogSettingsRepository = settings,
        smsCollectorToUse: SmsCollector = smsCollector,
        service: DiscordWebhookService = webhookService,
    ) = UsageLogUploader(repository, settingsRepository, collector, smsCollectorToUse, service, deviceInfo) { now }

    private suspend fun configure() {
        settings.setWebhookUrl(WEBHOOK_URL)
        settings.ensureEncryptionKey()
    }

    private suspend fun addRecords(count: Int) {
        repository.add(List(count) { ScreenOnRecord(now - 1000 + it) }, now)
    }

    private suspend fun decryptToText(attachment: ByteArray): String =
        decryptToText(attachment, checkNotNull(AesGcmCipher.decodeKey(settings.getEncryptionKey())))

    private fun decryptToText(attachment: ByteArray, key: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(AesGcmCipher.decrypt(attachment, key))).readBytes().toString(Charsets.UTF_8)

    /** 상태 코드 [statusCode]인 응답 */
    private fun response(statusCode: Int): MockResponse = MockResponse().setResponseCode(statusCode)

    /** `Retry-After`가 [wait]를 초 단위 소수(밀리초 세 자리)로 적은 값인 429 응답이며, null이면 헤더 없음 */
    private fun tooManyRequests(wait: Duration?): MockResponse {
        val rateLimited = response(429)
        if (wait == null) {
            return rateLimited
        }
        val millis = wait.inWholeMilliseconds
        return rateLimited.setHeader("Retry-After", "${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')}")
    }

    /**
     * 서버가 지금까지 받은 웹후크 요청을 받은 순서대로 모두 꺼냄
     *
     * 꺼낸 요청은 서버 대기열에서 빠지므로 테스트마다 한 번만 부름.
     */
    private fun takeReceivedWebhooks(): List<ReceivedWebhook> = List(server.requestCount) { readWebhook(server.takeRequest()) }

    /** multipart 요청에서 payload_json 파트의 content와 files[0] 파트의 파일 이름, 바이트를 꺼냄 */
    private fun readWebhook(request: RecordedRequest): ReceivedWebhook {
        val boundary = checkNotNull(checkNotNull(request.getHeader("Content-Type")).toMediaType().parameter("boundary"))
        return MultipartReader(request.body, boundary).use { reader ->
            val payloadJson = checkNotNull(reader.nextPart()).body.readUtf8()
            val payload = JsonUtil.kotlinSerialization.decodeFromString(DiscordWebhookPayload.serializer(), payloadJson)
            val filePart = checkNotNull(reader.nextPart())
            val fileName = checkNotNull(filePart.headers["Content-Disposition"]).substringAfter("filename=\"").substringBefore('"')
            ReceivedWebhook(checkNotNull(request.path), payload.content, fileName, filePart.body.readByteArray())
        }
    }

    /**
     * 주소나 키가 없으면 보내지 않는지 검증
     *
     * Given: 미전송 기록 1건, 웹후크 주소와 키 없음
     * When: upload 호출
     * Then: NotConfigured, 서버가 받은 요청 없음
     */
    @Test
    fun test_01_notConfiguredWithoutUrlOrKey() = runTest {
        /** Given **/
        addRecords(1)

        /** When **/
        val result = createUploader().upload(SendReason.MANUAL)

        /** Then **/
        assertEquals(UploadResult.NotConfigured, result)
        assertEquals(0, server.requestCount)
    }

    /**
     * 미전송 기록을 보내고 전송됨으로 표시하는지 검증
     *
     * Given: 기록 3건, 설정 완료
     * When: upload 호출
     * Then: 요청 경로는 웹후크 경로, 머리말 seq 1, 파일 이름 usage-log-1.bin, 복호화 줄 수 4, 미전송 0, 순번 1, 마지막 결과 성공
     */
    @Test
    fun test_02_sendsUnsentAndMarksSent() = runTest {
        /** Given **/
        configure()
        addRecords(3)

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        val webhook = takeReceivedWebhooks().single()
        assertEquals("/api/webhooks/123456/token_ABC-def", webhook.path)
        assertEquals("""{"kind":"justapp-usage-log","v":1,"seq":1}""", webhook.content)
        assertEquals("usage-log-1.bin", webhook.fileName)
        assertEquals(4, decryptToText(webhook.attachment).trimEnd('\n').lines().size)
        assertEquals(0, repository.getUnsent(10).size)
        assertEquals(1L, settings.getSendSequence())
        assertEquals(LastSendResult(now, true), settings.lastSendResult.first())
    }

    /**
     * 전송된 기록을 다시 담지 않는지 검증
     *
     * Given: 기록 3건을 이미 전송
     * When: upload 다시 호출
     * Then: NothingToSend, 서버가 받은 요청 1회 그대로
     */
    @Test
    fun test_03_alreadySentRecordsAreNotResent() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        createUploader().upload(SendReason.PERIODIC)

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.NothingToSend, result)
        assertEquals(1, server.requestCount)
    }

    /**
     * 서버 오류면 기록이 미전송으로 남고 재시도 대상인지 검증
     *
     * Given: 기록 3건, 서버가 500 응답
     * When: upload 호출
     * Then: Failed(true), 미전송 3, 마지막 결과 실패
     */
    @Test
    fun test_04_serverFailureKeepsRecordsUnsent() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(response(500))

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(3, repository.getUnsent(10).size)
        assertEquals(LastSendResult(now, false), settings.lastSendResult.first())
    }

    /**
     * 429이면 Retry-After 헤더의 시간만큼 기다려 다시 보내는지 검증
     *
     * Given: 기록 3건, 서버가 429(Retry-After 1초) 다음 204 응답
     * When: upload 호출
     * Then: Sent(3), 서버가 받은 요청 2회, 가상 시간 1000ms 경과
     */
    @Test
    fun test_05_rateLimitedRetriesAfterDelay() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(tooManyRequests(1.seconds))

        /** When **/
        val start = currentTime
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        assertEquals(2, server.requestCount)
        assertEquals(1000L, currentTime - start)
    }

    /**
     * 클라이언트 오류는 재시도 대상이 아닌지 검증
     *
     * Given: 기록 3건, 서버가 404 응답
     * When: upload 호출
     * Then: Failed(false)
     */
    @Test
    fun test_06_clientErrorNotRetryable() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(response(404))

        /** When / Then **/
        assertEquals(UploadResult.Failed(false), createUploader().upload(SendReason.PERIODIC))
    }

    /**
     * 수집이 켜져 있으면 보내기 전에 수집하는지 검증
     *
     * Given: 수집 켜짐, 설정 완료
     * When: upload 호출
     * Then: collector.collect 호출
     */
    @Test
    fun test_07_collectsBeforeSendingWhenEnabled() = runTest {
        /** Given **/
        configure()
        settings.setCollectionEnabled(true)

        /** When **/
        createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        coVerify { collector.collect() }
    }

    /**
     * 메타 줄의 reason이 인자와 같은지 검증
     *
     * Given: 기록 1건, 설정 완료
     * When: HOME_LEAVE로 upload 호출
     * Then: 복호화한 meta 줄에 "reason":"home_leave"
     */
    @Test
    fun test_08_metaReasonMatchesArgument() = runTest {
        /** Given **/
        configure()
        addRecords(1)

        /** When **/
        createUploader().upload(SendReason.HOME_LEAVE)

        /** Then **/
        val metaLine = decryptToText(takeReceivedWebhooks().single().attachment).lines().first()
        assertTrue(metaLine.contains("\"reason\":\"home_leave\""))
    }

    /**
     * 수집이 실패해도 저장된 기록을 보내는지 검증
     *
     * Given: 수집 켜짐, collector가 예외, 기록 3건
     * When: upload 호출
     * Then: Sent(3)
     */
    @Test
    fun test_09_collectFailureStillSends() = runTest {
        /** Given **/
        configure()
        settings.setCollectionEnabled(true)
        coEvery { collector.collect() } throws IOException("collect failed")
        addRecords(3)

        /** When / Then **/
        assertEquals(UploadResult.Sent(3), createUploader().upload(SendReason.PERIODIC))
    }

    /**
     * 429가 반복되면 처음 포함 네 번까지만 보내고 재시도 대상으로 실패하는지 검증
     *
     * Given: 기록 3건, 서버가 계속 429(Retry-After 1초) 응답
     * When: upload 호출
     * Then: Failed(true), 서버가 받은 요청 4회, 미전송 3
     */
    @Test
    fun test_10_repeatedRateLimitFailsAfterMaxAttempts() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        repeat(10) { server.enqueue(tooManyRequests(1.seconds)) }

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(4, server.requestCount)
        assertEquals(3, repository.getUnsent(10).size)
    }

    /**
     * 설치 식별자를 얻지 못하면 보내지 않고 재시도 대상으로 실패하는지 검증
     *
     * Given: 기록 3건, getInstallId가 실패
     * When: upload 호출
     * Then: Failed(true), 서버가 받은 요청 없음
     */
    @Test
    fun test_11_installIdFailureFailsWithoutSending() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        val failingSettings = spyk(settings)
        coEvery { failingSettings.getInstallId() } returns Result.failure(IOException("install id"))

        /** When **/
        val result = createUploader(failingSettings).upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(0, server.requestCount)
    }

    /**
     * 보관 기간 정리가 실패해도 저장된 기록을 보내는지 검증
     *
     * Given: 기록 3건, 설정 완료, purgeExpired가 예외
     * When: upload 호출
     * Then: Sent(3), 서버가 받은 요청 1회
     */
    @Test
    fun test_12_purgeFailureStillSends() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        val failingRepository = spyk(repository)
        coEvery { failingRepository.purgeExpired(any()) } throws IOException("purge failed")

        /** When **/
        val result = UsageLogUploader(failingRepository, settings, collector, smsCollector, webhookService, deviceInfo) { now }
            .upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        assertEquals(1, server.requestCount)
    }

    /**
     * 429의 대기 시간이 상한을 넘으면 기다리지 않고 재시도 대상으로 실패하는지 검증
     *
     * Given: 기록 3건, 서버가 429(Retry-After 상한 + 1ms) 응답
     * When: upload 호출
     * Then: Failed(true), 서버가 받은 요청 1회, 가상 시간 경과 없음, 미전송 3
     */
    @Test
    fun test_13_rateLimitOverMaxWaitFailsWithoutWaiting() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(tooManyRequests(UsageLogUploader.MAX_RATE_LIMIT_WAIT + 1.milliseconds))

        /** When **/
        val start = currentTime
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(1, server.requestCount)
        assertEquals(0L, currentTime - start)
        assertEquals(3, repository.getUnsent(10).size)
    }

    /**
     * 429의 대기 시간이 상한과 같으면 기다려 다시 보내는지 검증
     *
     * Given: 기록 3건, 서버가 429(Retry-After 상한) 다음 204 응답
     * When: upload 호출
     * Then: Sent(3), 서버가 받은 요청 2회, 가상 시간이 상한만큼 경과
     */
    @Test
    fun test_14_rateLimitAtMaxWaitStillWaits() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(tooManyRequests(UsageLogUploader.MAX_RATE_LIMIT_WAIT))

        /** When **/
        val start = currentTime
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        assertEquals(2, server.requestCount)
        assertEquals(UsageLogUploader.MAX_RATE_LIMIT_WAIT.inWholeMilliseconds, currentTime - start)
    }

    /**
     * 전송 도중 키를 새로 만들면 다음 라운드부터 새 키로 암호화하는지 검증
     *
     * Given: 한 라운드 분량보다 1건 많은 기록, 서버가 첫 요청에 응답하기 전 키를 새로 만들어 저장
     * When: upload 호출
     * Then: Sent(전체 건수), 첫 첨부는 이전 키로 풀려 기록 줄이 한 라운드 분량, 둘째 첨부는 새 키로 풀려 기록 줄이 1개
     */
    @Test
    fun test_15_regeneratedKeyIsUsedFromNextRound() = runTest {
        /** Given **/
        configure()
        val roundSize = UsageLogUploader.MAX_RECORDS_PER_ROUND
        addRecords(roundSize + 1)
        val oldKey = checkNotNull(AesGcmCipher.decodeKey(settings.getEncryptionKey()))
        val newKey = AesGcmCipher.generateKey()
        val keyChanged = AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // 서버 스레드에서 첫 요청에 응답하기 전 한 번만 키 교체
                if (keyChanged.compareAndSet(false, true)) {
                    runBlocking { settings.setEncryptionKey(AesGcmCipher.encodeKey(newKey)) }
                }
                return response(204)
            }
        }

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(roundSize + 1), result)
        val webhooks = takeReceivedWebhooks()
        assertEquals(2, webhooks.size)
        // 줄 수는 meta 줄 포함
        assertEquals(roundSize + 1, decryptToText(webhooks[0].attachment, oldKey).trimEnd('\n').lines().size)
        assertEquals(2, decryptToText(webhooks[1].attachment, newKey).trimEnd('\n').lines().size)
    }

    /**
     * 한 라운드 분량이 모두 손상 행이어도 멈추지 않고 뒤의 기록과 손상 범위의 공백 기록을 보내는지 검증
     *
     * Given: 설정 완료, json이 깨진 미전송 행이 한 라운드 분량(time 0부터), 그 뒤 정상 기록 1건
     * When: upload 호출
     * Then: Sent(2), 첨부에 정상 기록과 gap(0, 마지막 손상 행 time, unsent_deleted) 줄, 미전송 0
     */
    @Test
    fun test_16_allCorruptRoundContinuesWithGap() = runTest {
        /** Given **/
        configure()
        val corruptCount = UsageLogUploader.MAX_RECORDS_PER_ROUND
        database.dao().insertAll(List(corruptCount) { UsageLogEntity(json = "{broken", time = it.toLong(), createdAt = now) })
        addRecords(1)

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(2), result)
        val lines = decryptToText(takeReceivedWebhooks().single().attachment).trimEnd('\n').lines()
        assertTrue(lines[1].contains("\"type\":\"screen_on\""))
        assertTrue(lines[2].contains("\"type\":\"gap\",\"start\":0,\"end\":${corruptCount - 1},\"cause\":\"unsent_deleted\""))
        assertEquals(0, repository.getUnsent(10).size)
    }

    /**
     * 수집이 켜져 있으면 전송 전에 문자를 수집해 같은 묶음에 담는지 검증
     *
     * Given: 수집 켜짐, 설정 완료, 문자 진행 상태, 구간 안 문자 1건을 돌려주는 문자 수집기
     * When: upload 호출
     * Then: Sent(1), 복호화한 묶음에 sms 기록
     */
    @Test
    fun test_17_smsCollectedBeforeSendingIsInSameBatch() = runTest {
        /** Given **/
        configure()
        settings.setCollectionEnabled(true)
        val hour = 60L * 60 * 1000
        settings.setSmsProgress(SmsProgress(now - 24 * hour, now - hour))
        val message = SmsMessage(1, false, now - hour / 2, SmsMessage.Direction.RECEIVED, emptyList(), text = null)
        val realSmsCollector = SmsCollector({ _, _ -> listOf(message) }, settings, repository) { now }

        /** When **/
        val result = createUploader(smsCollectorToUse = realSmsCollector).upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(1), result)
        assertTrue(decryptToText(takeReceivedWebhooks().single().attachment).contains("\"type\":\"sms\""))
    }

    /**
     * 문자 수집이 실패해도 저장된 기록을 보내는지 검증
     *
     * Given: 수집 켜짐, 문자 collect가 예외, 기록 3건
     * When: upload 호출
     * Then: Sent(3)
     */
    @Test
    fun test_18_smsCollectFailureStillSends() = runTest {
        /** Given **/
        configure()
        settings.setCollectionEnabled(true)
        coEvery { smsCollector.collect() } throws IOException("sms collect failed")
        addRecords(3)

        /** When / Then **/
        assertEquals(UploadResult.Sent(3), createUploader().upload(SendReason.PERIODIC))
    }

    /**
     * 앱 사용 수집이 실패해도 문자 수집은 도는지 검증
     *
     * Given: 수집 켜짐, 앱 사용 collect가 예외
     * When: upload 호출
     * Then: smsCollector.collect 호출
     */
    @Test
    fun test_19_smsCollectedEvenWhenUsageCollectFails() = runTest {
        /** Given **/
        configure()
        settings.setCollectionEnabled(true)
        coEvery { collector.collect() } throws IOException("collect failed")

        /** When **/
        createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        coVerify { smsCollector.collect() }
    }

    /**
     * 수집이 꺼져 있으면 문자 수집을 하지 않는지 검증
     *
     * Given: 수집 꺼짐, 설정 완료
     * When: upload 호출
     * Then: smsCollector.collect 호출 없음
     */
    @Test
    fun test_20_smsNotCollectedWhenDisabled() = runTest {
        /** Given **/
        configure()

        /** When **/
        createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        coVerify(exactly = 0) { smsCollector.collect() }
    }

    /**
     * Retry-After 헤더가 없는 429면 기본 대기 시간만큼 기다려 다시 보내는지 검증
     *
     * Given: 기록 3건, 서버가 헤더 없는 429 다음 204 응답
     * When: upload 호출
     * Then: Sent(3), 서버가 받은 요청 2회, 가상 시간이 기본 대기 시간(5초)만큼 경과
     */
    @Test
    fun test_21_rateLimitWithoutRetryAfterWaitsDefault() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        server.enqueue(tooManyRequests(null))

        /** When **/
        val start = currentTime
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        assertEquals(2, server.requestCount)
        assertEquals(5_000L, currentTime - start)
    }

    /**
     * 응답을 받지 못하면 기록이 미전송으로 남고 재시도 대상인지 검증
     *
     * Given: 기록 3건, 시작한 뒤 바로 닫은 서버로 보내는 웹후크 서비스
     * When: upload 호출
     * Then: Failed(true), 미전송 3, 마지막 결과 실패
     */
    @Test
    fun test_22_connectionFailureIsRetryable() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        val closedServer = MockWebServer()
        closedServer.start()
        val closedUrl = closedServer.url("/")
        closedServer.shutdown()

        /** When **/
        val result = createUploader(service = createWebhookService(closedUrl)).upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(3, repository.getUnsent(10).size)
        assertEquals(LastSendResult(now, false), settings.lastSendResult.first())
    }

    private companion object {
        const val WEBHOOK_URL = "https://discord.com/api/webhooks/123456/token_ABC-def"
    }
}
