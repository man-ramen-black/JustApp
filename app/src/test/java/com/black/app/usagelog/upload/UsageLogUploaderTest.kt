package com.black.app.usagelog.upload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.model.database.usagelog.UsageLogDatabase
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/** [UsageLogUploader]의 묶음 전송, 전송됨 표시, 재시도 분류 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogUploaderTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = UsageLogSettingsRepository(context)
    private lateinit var database: UsageLogDatabase
    private lateinit var repository: UsageLogRepository
    private val collector = mockk<UsageStatsCollector>(relaxed = true)
    private val sender = FakeSender()
    private val deviceInfo = DeviceInfo("SM-S921N", "com.black.app") { 540 }
    private val now = 1_763_000_000_000L

    private class FakeSender : WebhookSender {
        class Call(val url: String, val content: String, val fileName: String, val attachment: ByteArray)

        val calls = ArrayList<Call>()
        val results = ArrayDeque<WebhookResult>()

        override suspend fun send(webhookUrl: String, content: String, fileName: String, attachment: ByteArray): WebhookResult {
            calls.add(Call(webhookUrl, content, fileName, attachment))
            return results.removeFirstOrNull() ?: WebhookResult.Success
        }
    }

    /**
     * 각 테스트 전 저장값 삭제와 메모리 DB 준비, 전송 설정 저장
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유되기 때문.
     */
    @Before
    fun setupUploader() {
        runTest { UsageLogDataStore(context).clear() }
        database = Room.inMemoryDatabaseBuilder(context, UsageLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UsageLogRepository(database.dao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun createUploader(settingsRepository: UsageLogSettingsRepository = settings) =
        UsageLogUploader(repository, settingsRepository, collector, sender, deviceInfo) { now }

    private suspend fun configure() {
        settings.setWebhookUrl(WEBHOOK_URL)
        settings.ensureEncryptionKey()
    }

    private suspend fun addRecords(count: Int) {
        repository.add(List(count) { ScreenOnRecord(now - 1000 + it) }, now)
    }

    private suspend fun decryptToText(attachment: ByteArray): String {
        val key = checkNotNull(UsageLogCipher.decodeKey(settings.getEncryptionKey()))
        return GZIPInputStream(ByteArrayInputStream(UsageLogCipher.decrypt(attachment, key))).readBytes().toString(Charsets.UTF_8)
    }

    /**
     * 주소나 키가 없으면 보내지 않는지 검증
     *
     * Given: 미전송 기록 1건, 웹후크 주소와 키 없음
     * When: upload 호출
     * Then: NotConfigured, sender 호출 없음
     */
    @Test
    fun test_01_notConfiguredWithoutUrlOrKey() = runTest {
        /** Given **/
        addRecords(1)

        /** When **/
        val result = createUploader().upload(SendReason.MANUAL)

        /** Then **/
        assertEquals(UploadResult.NotConfigured, result)
        assertEquals(0, sender.calls.size)
    }

    /**
     * 미전송 기록을 보내고 전송됨으로 표시하는지 검증
     *
     * Given: 기록 3건, 설정 완료
     * When: upload 호출
     * Then: 머리말 seq 1, 파일 이름 usage-log-1.bin, 복호화 줄 수 4, 미전송 0, 순번 1, 마지막 결과 성공
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
        val call = sender.calls.single()
        assertEquals("""{"kind":"justapp-usage-log","v":1,"seq":1}""", call.content)
        assertEquals("usage-log-1.bin", call.fileName)
        assertEquals(4, decryptToText(call.attachment).trimEnd('\n').lines().size)
        assertEquals(0, repository.getUnsent(10).size)
        assertEquals(1L, settings.getSendSequence())
        assertEquals(LastSendResult(now, true), settings.lastSendResult.first())
    }

    /**
     * 전송된 기록을 다시 담지 않는지 검증
     *
     * Given: 기록 3건을 이미 전송
     * When: upload 다시 호출
     * Then: NothingToSend, sender 호출 1회 그대로
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
        assertEquals(1, sender.calls.size)
    }

    /**
     * 서버 오류면 기록이 미전송으로 남고 재시도 대상인지 검증
     *
     * Given: 기록 3건, sender가 500 실패
     * When: upload 호출
     * Then: Failed(true), 미전송 3, 마지막 결과 실패
     */
    @Test
    fun test_04_serverFailureKeepsRecordsUnsent() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        sender.results.add(WebhookResult.Failure(500, "error"))

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(3, repository.getUnsent(10).size)
        assertEquals(LastSendResult(now, false), settings.lastSendResult.first())
    }

    /**
     * 429이면 안내된 시간만큼 기다려 다시 보내는지 검증
     *
     * Given: 기록 3건, sender가 RateLimited(1000) 다음 Success
     * When: upload 호출
     * Then: Sent(3), sender 호출 2회, 가상 시간 1000ms 경과
     */
    @Test
    fun test_05_rateLimitedRetriesAfterDelay() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        sender.results.add(WebhookResult.RateLimited(1000))

        /** When **/
        val start = currentTime
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Sent(3), result)
        assertEquals(2, sender.calls.size)
        assertEquals(1000L, currentTime - start)
    }

    /**
     * 클라이언트 오류는 재시도 대상이 아닌지 검증
     *
     * Given: 기록 3건, sender가 404 실패
     * When: upload 호출
     * Then: Failed(false)
     */
    @Test
    fun test_06_clientErrorNotRetryable() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        sender.results.add(WebhookResult.Failure(404, "not found"))

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
        val metaLine = decryptToText(sender.calls.single().attachment).lines().first()
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
     * Given: 기록 3건, sender가 계속 RateLimited(1000)
     * When: upload 호출
     * Then: Failed(true), sender 호출 4회, 미전송 3
     */
    @Test
    fun test_10_repeatedRateLimitFailsAfterMaxAttempts() = runTest {
        /** Given **/
        configure()
        addRecords(3)
        repeat(10) { sender.results.add(WebhookResult.RateLimited(1000)) }

        /** When **/
        val result = createUploader().upload(SendReason.PERIODIC)

        /** Then **/
        assertEquals(UploadResult.Failed(true), result)
        assertEquals(4, sender.calls.size)
        assertEquals(3, repository.getUnsent(10).size)
    }

    /**
     * 설치 식별자를 얻지 못하면 보내지 않고 재시도 대상으로 실패하는지 검증
     *
     * Given: 기록 3건, getInstallId가 실패
     * When: upload 호출
     * Then: Failed(true), sender 호출 없음
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
        assertEquals(0, sender.calls.size)
    }

    private companion object {
        const val WEBHOOK_URL = "https://discord.com/api/webhooks/123456/token_ABC-def"
    }
}
