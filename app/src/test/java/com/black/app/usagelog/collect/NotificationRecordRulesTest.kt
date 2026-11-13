package com.black.app.usagelog.collect

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.record.UsageLogRecord
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [NotificationRecordRules]의 기록 대상 판정, 제거 이유 변환, 본문 선택, 메시지 스타일 읽기, 이벤트 처리 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationRecordRulesTest : BaseTest() {

    private fun snapshot(
        packageName: String = "com.other",
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
    ) = NotificationSnapshot("key", packageName, 1_000L, isOngoing, isGroupSummary)

    /**
     * 진행 중 알림은 기록하지 않는지 검증
     *
     * Given: isOngoing=true 알림
     * When: shouldRecord 호출
     * Then: false
     */
    @Test
    fun test_01_ongoingSkipped() = runTest {
        /** Given / When **/
        val result = NotificationRecordRules.shouldRecord(snapshot(isOngoing = true), "com.black.app")

        /** Then **/
        assertFalse(result)
    }

    /**
     * 그룹 요약 알림은 기록하지 않는지 검증
     *
     * Given: isGroupSummary=true 알림
     * When: shouldRecord 호출
     * Then: false
     */
    @Test
    fun test_02_groupSummarySkipped() = runTest {
        /** Given / When **/
        val result = NotificationRecordRules.shouldRecord(snapshot(isGroupSummary = true), "com.black.app")

        /** Then **/
        assertFalse(result)
    }

    /**
     * 이 앱 자신의 알림은 기록하지 않는지 검증
     *
     * Given: 패키지가 ownPackageName과 같은 알림
     * When: shouldRecord 호출
     * Then: false
     */
    @Test
    fun test_03_ownPackageSkipped() = runTest {
        /** Given / When **/
        val result = NotificationRecordRules.shouldRecord(snapshot(packageName = "com.black.app"), "com.black.app")

        /** Then **/
        assertFalse(result)
    }

    /**
     * 일반 알림은 기록하는지 검증
     *
     * Given: 진행 중도 요약도 아닌 다른 앱 알림
     * When: shouldRecord 호출
     * Then: true
     */
    @Test
    fun test_04_normalRecorded() = runTest {
        /** Given / When **/
        val result = NotificationRecordRules.shouldRecord(snapshot(), "com.black.app")

        /** Then **/
        assertTrue(result)
    }

    /**
     * 제거 이유 코드가 기록 사유로 변환되는지 검증
     *
     * Given: 1, 2, 3, 8, 9, 10
     * When: removalReason 호출
     * Then: CLICK, DISMISS, DISMISS, APP_CANCEL, APP_CANCEL, OTHER
     */
    @Test
    fun test_05_removalReasonMapping() = runTest {
        /** Given / When **/
        val reasons = listOf(1, 2, 3, 8, 9, 10).map(NotificationRecordRules::removalReason)

        /** Then **/
        assertEquals(
            listOf(
                RemovalReason.CLICK,
                RemovalReason.DISMISS,
                RemovalReason.DISMISS,
                RemovalReason.APP_CANCEL,
                RemovalReason.APP_CANCEL,
                RemovalReason.OTHER,
            ),
            reasons,
        )
    }

    /**
     * 본문이 마지막 메시지, 긴 본문, 본문 순으로 비어 있지 않은 첫 값이 되는지 검증
     *
     * Given: 세 값의 조합 네 가지
     * When: resolveText 호출
     * Then: 마지막, 긴 본문, 본문, null
     */
    @Test
    fun test_06_resolveTextPriority() = runTest {
        /** Given / When / Then **/
        assertEquals("마지막", NotificationRecordRules.resolveText("마지막", "긴 본문", "본문"))
        assertEquals("긴 본문", NotificationRecordRules.resolveText(null, "긴 본문", "본문"))
        assertEquals("본문", NotificationRecordRules.resolveText(null, null, "본문"))
        assertNull(NotificationRecordRules.resolveText(null, "", null))
    }

    private fun event(
        removalReason: RemovalReason? = null,
        snapshot: NotificationSnapshot = snapshot(),
    ) = NotificationEvent(snapshot, "제목", "본문", "msg", null, removalReason, eventTime = 9_000L)

    /**
     * 게시 이벤트가 알림 postTime의 게시 기록으로 변환되는지 검증
     *
     * Given: 게시 이벤트, 중복 아님
     * When: toRecord 호출
     * Then: postTime, 앱 이름, 제목, 본문, 카테고리를 담은 NotificationPostedRecord
     */
    @Test
    fun test_07_postedEventToRecord() = runTest {
        /** Given / When **/
        val record = NotificationRecordRules.toRecord(event(), "com.black.app", { "${it}앱" }, { _, _, _, _ -> false })

        /** Then **/
        assertEquals(NotificationPostedRecord("key", "com.other", "com.other앱", 1_000L, "제목", "본문", "msg"), record)
    }

    /**
     * 제거 이벤트의 기록 시각이 콜백 시각인지 검증
     *
     * Given: eventTime 9000, postTime 1000인 CLICK 제거 이벤트
     * When: toRecord 호출
     * Then: time=9000인 NotificationRemovedRecord이고 중복 판정은 호출되지 않음
     */
    @Test
    fun test_08_removedEventUsesEventTime() = runTest {
        /** Given / When **/
        val record = NotificationRecordRules.toRecord(
            event(RemovalReason.CLICK), "com.black.app", { it }, { _, _, _, _ -> error("제거는 중복 판정하지 않음") },
        )

        /** Then **/
        assertEquals(NotificationRemovedRecord("key", "com.other", 9_000L, RemovalReason.CLICK), record)
    }

    /**
     * 기록 대상이 아니거나 중복인 게시 이벤트는 null인지 검증
     *
     * Given: 진행 중 알림, 중복으로 판정되는 알림
     * When: toRecord 호출
     * Then: 둘 다 null
     */
    @Test
    fun test_09_skippedOrDuplicateEventIsNull() = runTest {
        /** Given / When **/
        val ongoing = NotificationRecordRules.toRecord(
            event(snapshot = snapshot(isOngoing = true)), "com.black.app", { it }, { _, _, _, _ -> false },
        )
        val duplicate = NotificationRecordRules.toRecord(event(), "com.black.app", { it }, { _, _, _, _ -> true })

        /** Then **/
        assertNull(ongoing)
        assertNull(duplicate)
    }

    /**
     * 메시지 스타일 알림에서 마지막 메시지의 문구와 시각을 읽는지 검증
     *
     * Given: 메시지 두 개인 메시지 스타일 알림, 일반 알림
     * When: lastMessageOf 호출
     * Then: 마지막 메시지 문구와 시각 2000, 일반 알림은 null
     */
    @Test
    fun test_10_lastMessageOfMessagingStyle() = runTest {
        /** Given **/
        val sender = Person.Builder().setName("보낸 사람").build()
        val messaging = NotificationCompat.Builder(context, CHANNEL_ID)
            .setStyle(
                NotificationCompat.MessagingStyle(Person.Builder().setName("나").build())
                    .addMessage("첫 메시지", 1_000L, sender)
                    .addMessage("마지막 메시지", 2_000L, sender),
            )
            .build()
        val plain = NotificationCompat.Builder(context, CHANNEL_ID).setContentText("본문").build()

        /** When **/
        val lastMessage = NotificationRecordRules.lastMessageOf(messaging)
        val none = NotificationRecordRules.lastMessageOf(plain)

        /** Then **/
        assertEquals("마지막 메시지", lastMessage?.text?.toString())
        assertEquals(2_000L, lastMessage?.timestamp)
        assertNull(none)
    }

    /**
     * 메시지 스타일 읽기가 예외를 던지면 null로 진행하는지 검증
     *
     * Given: 메시지 스타일 추출이 예외를 던지도록 모킹
     * When: lastMessageOf 호출
     * Then: 예외 없이 null
     */
    @Test
    fun test_11_lastMessageOfFailureIsNull() = runTest {
        /** Given **/
        mockkStatic(NotificationCompat.MessagingStyle::class)
        every { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(any()) } throws
            IllegalStateException("형식 오류")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).setContentText("본문").build()

        /** When **/
        val lastMessage = NotificationRecordRules.lastMessageOf(notification)

        /** Then **/
        assertNull(lastMessage)
    }

    /**
     * 수집 켜짐을 이벤트마다 읽고, 꺼진 동안의 제거도 중복 판정 기억을 지우는지 검증
     *
     * Given: 켜짐에서 게시 하나를 저장한 뒤 수집을 끔
     * When: 같은 key 제거와 다른 내용 게시 처리, 다시 켠 뒤 처음과 같은 내용 게시 처리
     * Then: 꺼진 동안은 저장하지 않고, 다시 켠 뒤 같은 내용 게시는 중복으로 거르지 않고 저장
     */
    @Test
    fun test_12_processReadsCollectionEnabledPerEvent() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        val saved = mutableListOf<UsageLogRecord>()
        var enabled = true
        process(event(), filter, { enabled }) { saved += it }
        enabled = false

        /** When **/
        process(event(RemovalReason.DISMISS), filter, { enabled }) { saved += it }
        process(event().copy(text = "꺼진 동안 본문"), filter, { enabled }) { saved += it }
        enabled = true
        process(event(), filter, { enabled }) { saved += it }

        /** Then **/
        assertEquals(listOf(postedRecord, postedRecord), saved)
    }

    /**
     * 저장하지 못한 게시는 기억을 지워 같은 내용의 재게시를 저장하는지 검증
     *
     * Given: 첫 호출만 예외를 던지는 저장 함수
     * When: 같은 게시를 두 번 처리
     * Then: 첫 처리는 저장 예외를 다시 던지고, 둘째 처리는 중복으로 거르지 않고 저장
     */
    @Test
    fun test_13_failedSaveForgetsContent() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        val saved = mutableListOf<UsageLogRecord>()
        var saveCount = 0
        val save: (UsageLogRecord) -> Unit = { record ->
            saveCount++
            if (saveCount == 1) throw IllegalStateException("저장 실패")
            saved += record
        }

        /** When **/
        val failure = runCatching { process(event(), filter, save = save) }.exceptionOrNull()
        process(event(), filter, save = save)

        /** Then **/
        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(postedRecord), saved)
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val postedRecord = NotificationPostedRecord("key", "com.other", "com.other앱", 1_000L, "제목", "본문", "msg")

    /** 실제 중복 거르기로 [NotificationRecordRules.process] 호출 */
    private suspend fun process(
        event: NotificationEvent,
        filter: NotificationDuplicateFilter,
        enabled: () -> Boolean = { true },
        save: (UsageLogRecord) -> Unit,
    ) = NotificationRecordRules.process(event, "com.black.app", { "${it}앱" }, filter, { enabled() }, { save(it) })

    private companion object {
        const val CHANNEL_ID = "usage_log_test"
    }
}
