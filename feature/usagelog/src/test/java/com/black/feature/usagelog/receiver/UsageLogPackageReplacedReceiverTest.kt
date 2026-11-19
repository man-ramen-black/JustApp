package com.black.feature.usagelog.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.black.feature.usagelog.data.usecase.UsageLogCollectionController
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/** [UsageLogPackageReplacedReceiver]의 action 확인과 앱 업데이트 수신 뒤 주기 수집 예약 갱신, 실패 로그 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogPackageReplacedReceiverTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalCreateController = UsageLogPackageReplacedReceiver.createController
    private val controller = mockk<UsageLogCollectionController>()
    private val createdCount = AtomicInteger(0)
    private val scheduled = CompletableDeferred<Unit>()

    /** 실제 저장소를 만들지 않도록 컨트롤러 생성을 호출 기록과 목업으로 대체 */
    @Before
    fun replaceController() {
        coEvery { controller.ensureScheduled() } coAnswers {
            scheduled.complete(Unit)
        }
        UsageLogPackageReplacedReceiver.createController = {
            createdCount.incrementAndGet()
            controller
        }
    }

    @After
    fun restoreController() {
        UsageLogPackageReplacedReceiver.createController = originalCreateController
    }

    /** 매니페스트에 선언된 리시버를 지정한 앱 업데이트 브로드캐스트 전송 */
    private fun sendPackageReplaced() {
        context.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(context, UsageLogPackageReplacedReceiver::class.java))
        idleMainLooper()
    }

    /**
     * 앱 업데이트가 아닌 action은 컨트롤러를 만들지 않는지 검증
     *
     * Given: 컨트롤러 생성 호출 기록
     * When: action 확인을 거치도록 다른 action 인텐트로 onReceive 직접 호출
     * Then: 컨트롤러 생성 0회, ensureScheduled 호출 없음
     */
    @Test
    fun test_01_otherActionDoesNotCreateController() = runTest {
        /** Given **/
        val receiver = UsageLogPackageReplacedReceiver()

        /** When **/
        receiver.onReceive(context, Intent("com.other.ACTION"))

        /** Then **/
        assertEquals(0, createdCount.get())
        coVerify(exactly = 0) { controller.ensureScheduled() }
    }

    /**
     * 앱 업데이트 action을 받으면 주기 수집 예약을 갱신하는지 검증
     *
     * Given: 컨트롤러 생성 호출 기록, 불리면 완료를 알리는 ensureScheduled
     * When: MY_PACKAGE_REPLACED action으로 리시버를 지정한 브로드캐스트 전송
     * Then: 컨트롤러 생성 1회, ensureScheduled 1회 호출
     */
    @Test
    fun test_02_packageReplacedEnsuresSchedule() = runTest {
        /** Given **/
        // replaceController에서 준비

        /** When **/
        sendPackageReplaced()

        /** Then **/
        // 예약 갱신은 goAsync로 이어 가는 Dispatchers.Default 범위에서 불리므로 실제 시간으로 대기
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { scheduled.await() } }
        assertEquals(1, createdCount.get())
        coVerify(exactly = 1) { controller.ensureScheduled() }
    }

    /**
     * 예약 갱신이 실패하면 로그에 예외 클래스 이름만 남기는지 검증
     *
     * Given: ensureScheduled가 메시지를 담은 IOException을 던짐
     * When: MY_PACKAGE_REPLACED action으로 리시버를 지정한 브로드캐스트 전송
     * Then: 경고 로그에 IOException은 있고 예외 메시지는 없음
     */
    @Test
    fun test_03_scheduleFailureLogsClassNameOnly() = runTest {
        /** Given **/
        coEvery { controller.ensureScheduled() } throws IOException("schedule-detail")

        /** When **/
        sendPackageReplaced()

        /** Then **/
        // 로그는 goAsync로 이어 가는 Dispatchers.Default 범위에서 남으므로 실제 시간으로 대기
        verify(timeout = 5_000) { Log.w(any<String>(), match<String> { it.contains("IOException") }) }
        verify(exactly = 0) { Log.w(any<String>(), match<String> { it.contains("schedule-detail") }) }
    }
}
