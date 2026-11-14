package com.black.app.model

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.black.app.model.datastore.UsageLogDataStore
import com.black.app.usagelog.record.OpenUsageSession
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.upload.UsageLogCipher
import com.black.test.BaseTest
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageLogSettingsRepository]의 수집 설정, 집 SSID, 진행 상태 저장·조회 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogSettingsRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = UsageLogSettingsRepository(context)

    /**
     * 각 테스트 전 저장값 전체 삭제
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 간 공유되기 때문.
     */
    @Before
    fun clearSettings() {
        runTest { UsageLogDataStore(context).clear() }
    }

    /**
     * 저장된 값이 없을 때 기본값으로 조회되는지 검증
     *
     * Given: 저장값 없음
     * When: 모든 설정 조회
     * Then: 꺼짐, 0, 빈 집합, 0, null, WifiState(false, null)
     */
    @Test
    fun test_01_defaults() = runTest {
        /** Given / When / Then **/
        assertEquals(false, repository.isCollectionEnabled())
        assertEquals(false, repository.collectionEnabled.first())
        assertEquals(0L, repository.getCollectionOffSince())
        assertEquals(emptySet<String>(), repository.getHomeSsids())
        assertEquals(emptySet<String>(), repository.homeSsids.first())
        assertEquals(0L, repository.getUsageCursor())
        assertNull(repository.getOpenSession())
        assertEquals(WifiState(false, null), repository.getLastWifiState())
    }

    /**
     * 집 SSID 추가와 삭제가 목록에 반영되는지 검증
     *
     * Given: 저장값 없음
     * When: 두 SSID 추가 후 하나 삭제
     * Then: 남은 SSID 하나
     */
    @Test
    fun test_02_homeSsidAddRemove() = runTest {
        /** Given / When **/
        repository.addHomeSsid("MyHome_5G")
        repository.addHomeSsid("MyHome_2G")
        repository.removeHomeSsid("MyHome_2G")

        /** Then **/
        assertEquals(setOf("MyHome_5G"), repository.getHomeSsids())
    }

    /**
     * 설치 식별자가 한 번 만들어진 뒤 유지되는지 검증
     *
     * Given: 저장값 없음
     * When: 설치 식별자 두 번 조회
     * Then: 같은 값이며 비어 있지 않음
     */
    @Test
    fun test_03_installIdStable() = runTest {
        /** Given / When **/
        val first = repository.getInstallId().getOrThrow()
        val second = repository.getInstallId().getOrThrow()

        /** Then **/
        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
    }

    /**
     * 열린 구간이 저장·삭제되는지 검증
     *
     * Given: 저장값 없음
     * When: 구간 저장 후 조회, null 저장 후 조회
     * Then: 저장한 구간 그대로, 이후 null
     */
    @Test
    fun test_04_openSessionRoundTrip() = runTest {
        /** Given / When **/
        val session = OpenUsageSession("com.a", 1000, 2000)
        repository.setOpenSession(session)
        val saved = repository.getOpenSession()
        repository.setOpenSession(null)

        /** Then **/
        assertEquals(session, saved)
        assertNull(repository.getOpenSession())
    }

    /**
     * 마지막 Wi-Fi 상태가 SSID 유무와 관계없이 그대로 돌아오는지 검증
     *
     * Given: 저장값 없음
     * When: SSID가 있는 상태와 없는 상태를 차례로 저장·조회
     * Then: 저장한 상태 그대로
     */
    @Test
    fun test_05_lastWifiStateRoundTrip() = runTest {
        /** Given / When / Then **/
        repository.setLastWifiState(WifiState(true, "MyHome_5G"))
        assertEquals(WifiState(true, "MyHome_5G"), repository.getLastWifiState())

        repository.setLastWifiState(WifiState(true, null))
        assertEquals(WifiState(true, null), repository.getLastWifiState())
    }

    /**
     * 설치 식별자를 동시에 읽어도 한 개만 만들어지는지 검증
     *
     * Given: 저장값 없음
     * When: 설치 식별자를 동시에 여러 번 조회
     * Then: 모두 같은 값
     */
    @Test
    fun test_06_installIdCreatedOnceOnConcurrentReads() = runTest {
        /** Given / When **/
        val ids = (1..5).map { async { repository.getInstallId().getOrThrow() } }.awaitAll()

        /** Then **/
        assertEquals(1, ids.toSet().size)
    }

    /**
     * 집 SSID를 동시에 추가해도 모두 남는지 검증
     *
     * Given: 저장값 없음
     * When: SSID 여러 개를 동시에 추가
     * Then: 모두 포함
     */
    @Test
    fun test_07_homeSsidConcurrentAddKeepsAll() = runTest {
        /** Given / When **/
        val names = (1..5).map { "ssid_$it" }
        names.map { async { repository.addHomeSsid(it) } }.awaitAll()

        /** Then **/
        assertEquals(names.toSet(), repository.getHomeSsids())
    }

    /**
     * 개인 데이터 저장·조회가 로그에 값을 남기지 않는지 검증
     *
     * Given: 저장값 없음
     * When: 집 SSID, 마지막 Wi-Fi 상태, 열린 구간 저장 후 조회, 설치 식별자 생성 후 재조회
     * Then: Log.v 메시지에 SSID 문자열과 설치 식별자가 없음
     */
    @Test
    fun test_08_personalValuesAreNotLogged() = runTest {
        /** Given / When **/
        repository.addHomeSsid("MyHome_5G")
        repository.getHomeSsids()
        repository.setLastWifiState(WifiState(true, "MyHome_5G"))
        repository.getLastWifiState()
        repository.setOpenSession(OpenUsageSession("com.a", 1000, null))
        repository.getOpenSession()
        val installId = repository.getInstallId().getOrThrow()
        repository.getInstallId()

        /** Then **/
        verify(exactly = 0) { Log.v(any(), match { it.contains("MyHome_5G") || it.contains(installId) }) }
    }

    /**
     * 웹후크 주소와 암호화 키가 로그에 남지 않는지 검증
     *
     * Given: 저장값 없음
     * When: 웹후크 주소 저장·조회, 암호화 키 생성·조회
     * Then: Log.v 메시지에 주소 토큰과 키 문자열이 없음
     */
    @Test
    fun test_11_webhookUrlAndKeyAreNotLogged() = runTest {
        /** Given / When **/
        repository.setWebhookUrl("https://discord.com/api/webhooks/1/secret-token")
        repository.getWebhookUrl()
        val key = repository.ensureEncryptionKey().getOrThrow()
        repository.getEncryptionKey()

        /** Then **/
        verify(exactly = 0) { Log.v(any(), match { it.contains("secret-token") || it.contains(key) }) }
    }

    /**
     * 손상된 열린 구간 JSON이 null로 처리되는지 검증
     *
     * Given: 열린 구간 키에 깨진 JSON 저장
     * When: 열린 구간 조회
     * Then: null
     */
    @Test
    fun test_09_corruptOpenSessionReturnsNull() = runTest {
        /** Given **/
        UsageLogDataStore(context).updateRawOpenSession("{broken")

        /** When / Then **/
        assertNull(repository.getOpenSession())
    }

    /**
     * 암호화 키가 한 번만 만들어지는지 검증
     *
     * Given: 저장값 없음
     * When: ensureEncryptionKey를 동시에 여러 번 호출
     * Then: 모두 같은 값이며 복원하면 32바이트
     */
    @Test
    fun test_10_ensureEncryptionKeyCreatesOnce() = runTest {
        /** Given / When **/
        val keys = (1..5).map { async { repository.ensureEncryptionKey().getOrThrow() } }.awaitAll()

        /** Then **/
        assertEquals(1, keys.toSet().size)
        assertEquals(keys.first(), repository.getEncryptionKey())
        assertEquals(32, UsageLogCipher.decodeKey(keys.first())?.size)
    }

    /**
     * 열린 구간과 커서가 함께 저장되고 null 구간은 지워지는지 검증
     *
     * Given: 저장값 없음
     * When: 구간과 커서 5000 저장 후 조회, null 구간과 커서 6000 저장 후 조회
     * Then: 첫 조회는 저장한 구간과 5000, 둘째 조회는 null과 6000
     */
    @Test
    fun test_12_usageProgressSavedTogether() = runTest {
        /** Given **/
        val session = OpenUsageSession("com.a", 1000, 2000)

        /** When **/
        val first = repository.setUsageProgress(session, 5000L)
        val firstSession = repository.getOpenSession()
        val firstCursor = repository.getUsageCursor()
        val second = repository.setUsageProgress(null, 6000L)

        /** Then **/
        assertTrue(first.isSuccess)
        assertEquals(session, firstSession)
        assertEquals(5000L, firstCursor)
        assertTrue(second.isSuccess)
        assertNull(repository.getOpenSession())
        assertEquals(6000L, repository.getUsageCursor())
    }
}
