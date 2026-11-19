package com.black.feature.usagelog.data.usecase

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.NotificationPostedRecord
import com.black.feature.usagelog.model.NotificationRemovedRecord
import com.black.feature.usagelog.model.RemovalReason
import com.black.feature.usagelog.model.UsageLogRecord
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.time.Duration.Companion.seconds

/**
 * [NotificationRecorder]가 시스템 알림 객체에서 값을 꺼내 게시·제거 기록으로 저장하고, 닫은 뒤 멈추는지 검증
 *
 * 알림 처리가 시간 초과로 실패해도 다음 알림을 기록하는지도 검증.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationRecorderTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * 수집 켜짐인 설정 저장소와 저장한 기록을 [saved]에 모으는 기록 저장소
     *
     * 소비 코루틴이 테스트 스케줄러 밖의 스레드로 넘어가지 않도록 목업함.
     */
    private val settings = mockk<UsageLogSettingsRepository>()
    private val repository = mockk<UsageLogRepository>()
    private val saved = mutableListOf<UsageLogRecord>()

    /** 소비 코루틴의 부모 Job, 테스트가 끝나면 취소 */
    private val recorderJob = Job()

    /** runTest와 같은 테스트 스케줄러에서 도는 기록기 scope */
    private val recorderScope = CoroutineScope(testDispatcherRule.testDispatcher + recorderJob)

    @Before
    fun setupRepositories() {
        coEvery { settings.isCollectionEnabled() } returns true
        coEvery { repository.add(any(), any()) } answers { saved += firstArg<List<UsageLogRecord>>() }
    }

    @After
    fun cancelRecorder() {
        recorderJob.cancel()
    }

    private fun createRecorder() = NotificationRecorder(
        ownPackageName = context.packageName,
        settings = settings,
        repository = repository,
        appNameOf = { packageName -> if (packageName == OTHER_PACKAGE) OTHER_APP_NAME else packageName },
        scope = recorderScope,
    )

    /** 다른 앱이 시각 [postTime]에 게시한 제목·본문·메시지 카테고리 알림 */
    private fun statusBarNotification(postTime: Long = 1000L): StatusBarNotification {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("제목")
            .setContentText("본문")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        return StatusBarNotification(
            OTHER_PACKAGE,
            OTHER_PACKAGE,
            1,
            null,
            OTHER_UID,
            0,
            0,
            notification,
            Process.myUserHandle(),
            postTime,
        )
    }

    /**
     * 다른 앱의 알림 게시 한 건이 게시 기록으로 저장되는지 검증
     *
     * Given: 수집 켜짐, 다른 앱의 제목·본문 알림
     * When: onPosted 호출
     * Then: key, 패키지, 앱 이름, 게시 시각, 제목, 본문, 카테고리를 담은 게시 기록 하나 저장
     */
    @Test
    fun test_01_postedNotificationIsSaved() = runTest {
        /** Given **/
        val recorder = createRecorder()
        val sbn = statusBarNotification(postTime = 1000L)

        /** When **/
        recorder.onPosted(sbn)
        advanceUntilIdle()

        /** Then **/
        val expected = NotificationPostedRecord(
            key = sbn.key,
            packageName = OTHER_PACKAGE,
            appName = OTHER_APP_NAME,
            time = 1000L,
            title = "제목",
            text = "본문",
            category = Notification.CATEGORY_MESSAGE,
        )
        assertEquals(listOf<UsageLogRecord>(expected), saved)
    }

    /**
     * 제거 이유 코드가 기록 사유로 바뀌고, 이유를 모르는 제거는 기타로 저장되는지 검증
     *
     * Given: 수집 켜짐, 다른 앱의 알림
     * When: 이유 없이(null) 제거, 클릭(REASON_CLICK)으로 제거 순서로 onRemoved 호출
     * Then: OTHER, CLICK 사유의 제거 기록 두 건이 도착 순서대로 저장
     */
    @Test
    fun test_02_removalReasonFromSystemCode() = runTest {
        /** Given **/
        val recorder = createRecorder()
        val sbn = statusBarNotification()

        /** When **/
        recorder.onRemoved(sbn, null)
        recorder.onRemoved(sbn, NotificationListenerService.REASON_CLICK)
        advanceUntilIdle()

        /** Then **/
        // 제거 기록의 시각은 콜백 도착 시각이라 비교에서 뺌
        val removed = saved.map { (it as NotificationRemovedRecord).copy(time = 0L) }
        assertEquals(
            listOf(
                NotificationRemovedRecord(sbn.key, OTHER_PACKAGE, 0L, RemovalReason.OTHER),
                NotificationRemovedRecord(sbn.key, OTHER_PACKAGE, 0L, RemovalReason.CLICK),
            ),
            removed,
        )
    }

    /**
     * 닫은 뒤 도착한 콜백은 버리고 소비 코루틴이 끝나는지 검증
     *
     * Given: 수집 켜짐인 기록기
     * When: close 뒤 onPosted 호출
     * Then: 저장한 기록 없음, 남은 소비 코루틴 없음
     */
    @Test
    fun test_03_closeDropsLaterCallbacks() = runTest {
        /** Given **/
        val recorder = createRecorder()

        /** When **/
        recorder.close()
        recorder.onPosted(statusBarNotification())
        advanceUntilIdle()

        /** Then **/
        assertEquals(emptyList<UsageLogRecord>(), saved)
        assertTrue(recorderJob.children.none())
    }

    /**
     * 처리 안의 시간 초과로 한 건이 실패해도 다음 알림을 기록하는지 검증
     *
     * Given: 첫 저장이 withTimeout의 시간 초과로 끝나는 기록 저장소
     * When: 다른 앱 알림의 게시 뒤 클릭 제거 순서로 콜백 호출
     * Then: 게시 기록은 저장되지 않고 실패는 클래스 이름으로 Log.w 기록, 이어진 CLICK 사유의 제거 기록은 저장
     */
    @Test
    fun test_04_timeoutInsideProcessingDoesNotStopLaterEvents() = runTest {
        /** Given **/
        var isFirstSave = true
        coEvery { repository.add(any(), any()) } coAnswers {
            if (isFirstSave) {
                isFirstSave = false
                // 저장 안에서 난 시간 초과라 소비 코루틴의 취소가 아님
                withTimeout(1.seconds) { awaitCancellation() }
            }
            saved += firstArg<List<UsageLogRecord>>()
        }
        val recorder = createRecorder()
        val sbn = statusBarNotification()

        /** When **/
        recorder.onPosted(sbn)
        recorder.onRemoved(sbn, NotificationListenerService.REASON_CLICK)
        advanceUntilIdle()

        /** Then **/
        // 제거 기록의 시각은 콜백 도착 시각이라 비교에서 뺌
        val savedWithoutRemovalTime = saved.map { if (it is NotificationRemovedRecord) it.copy(time = 0L) else it }
        assertEquals(
            listOf<UsageLogRecord>(NotificationRemovedRecord(sbn.key, OTHER_PACKAGE, 0L, RemovalReason.CLICK)),
            savedWithoutRemovalTime,
        )
        verify(exactly = 1) {
            Log.w(any<String>(), match<String> { it.contains("알림 처리 실패: TimeoutCancellationException") })
        }
    }

    companion object {
        private const val OTHER_PACKAGE = "com.example.chat"
        private const val OTHER_APP_NAME = "채팅"
        private const val OTHER_UID = 10_001
        private const val CHANNEL_ID = "chat"
    }
}
