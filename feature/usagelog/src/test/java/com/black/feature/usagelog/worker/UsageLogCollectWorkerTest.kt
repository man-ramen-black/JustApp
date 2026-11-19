package com.black.feature.usagelog.worker

import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.usecase.SmsCollector
import com.black.feature.usagelog.data.usecase.UsageStatsCollector
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException

/** [UsageLogCollectWorker]의 보관 기간 정리와 수집 순서, 실패 분리 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogCollectWorkerTest : BaseTest() {

    private val repository = mockk<UsageLogRepository>(relaxed = true)
    private val collector = mockk<UsageStatsCollector>(relaxed = true)
    private val smsCollector = mockk<SmsCollector>(relaxed = true)

    /**
     * 정리가 실패해도 수집하는지 검증
     *
     * Given: purgeExpired가 예외
     * When: purgeThenCollect 호출
     * Then: 예외가 밖으로 나오지 않고 purgeExpired(now) 뒤 collect 호출
     */
    @Test
    fun test_01_purgeFailureStillCollects() = runTest {
        /** Given **/
        coEvery { repository.purgeExpired(any()) } throws IOException("purge failed")

        /** When **/
        UsageLogCollectWorker.purgeThenCollect(repository, collector, smsCollector, NOW)

        /** Then **/
        coVerifyOrder {
            repository.purgeExpired(NOW)
            collector.collect()
        }
    }

    /**
     * 수집이 실패해도 예외가 밖으로 나오지 않고 정리는 수집 전에 끝나는지 검증
     *
     * Given: collect가 예외
     * When: purgeThenCollect 호출
     * Then: 예외가 밖으로 나오지 않고 purgeExpired(now) 뒤 collect 호출
     */
    @Test
    fun test_02_collectFailureIsContainedAfterPurge() = runTest {
        /** Given **/
        coEvery { collector.collect() } throws IOException("collect failed")

        /** When **/
        UsageLogCollectWorker.purgeThenCollect(repository, collector, smsCollector, NOW)

        /** Then **/
        coVerifyOrder {
            repository.purgeExpired(NOW)
            collector.collect()
        }
    }

    /**
     * 앱 사용 수집이 실패해도 문자 수집이 도는지 검증
     *
     * Given: 앱 사용 collect가 예외
     * When: purgeThenCollect 호출
     * Then: 예외가 밖으로 나오지 않고 앱 사용 collect 뒤 문자 collect 호출
     */
    @Test
    fun test_03_usageCollectFailureStillCollectsSms() = runTest {
        /** Given **/
        coEvery { collector.collect() } throws IOException("collect failed")

        /** When **/
        UsageLogCollectWorker.purgeThenCollect(repository, collector, smsCollector, NOW)

        /** Then **/
        coVerifyOrder {
            collector.collect()
            smsCollector.collect()
        }
    }

    /**
     * 문자 수집이 실패해도 예외가 밖으로 나오지 않는지 검증
     *
     * Given: 문자 collect가 예외
     * When: purgeThenCollect 호출
     * Then: 예외가 밖으로 나오지 않고 앱 사용 collect 뒤 문자 collect 호출
     */
    @Test
    fun test_04_smsCollectFailureIsContained() = runTest {
        /** Given **/
        coEvery { smsCollector.collect() } throws IOException("sms collect failed")

        /** When **/
        UsageLogCollectWorker.purgeThenCollect(repository, collector, smsCollector, NOW)

        /** Then **/
        coVerifyOrder {
            collector.collect()
            smsCollector.collect()
        }
    }

    private companion object {
        const val NOW = 1_763_000_000_000L
    }
}
