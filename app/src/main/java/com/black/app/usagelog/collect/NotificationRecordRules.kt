package com.black.app.usagelog.collect

import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.record.UsageLogRecord

/** 알림 기록 여부 판정에 필요한 알림 정보 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
)

/**
 * 알림 콜백 한 건을 콜백 안에서 꺼낸 불변 값으로 옮긴 것
 *
 * 처리가 늦어져도 `StatusBarNotification`에 접근하지 않도록 기록에 필요한 값만 담음.
 *
 * @property removalReason null이면 게시, 아니면 제거(제거는 [title]·[text]·[category]를 읽지 않음)
 * @property eventTime 콜백이 도착한 시각(epoch ms)
 */
data class NotificationEvent(
    val snapshot: NotificationSnapshot,
    val title: String?,
    val text: String?,
    val category: String?,
    val removalReason: RemovalReason?,
    val eventTime: Long,
)

/** 알림 기록 대상 판정, 제거 이유 변환, 본문 선택 규칙 */
object NotificationRecordRules {
    // NotificationListenerService.REASON_* 상수값(JVM 유닛 테스트에서 안드로이드 상수에 기대지 않으려고 복사)
    private const val REASON_CLICK = 1
    private const val REASON_CANCEL = 2
    private const val REASON_CANCEL_ALL = 3
    private const val REASON_APP_CANCEL = 8
    private const val REASON_APP_CANCEL_ALL = 9

    /** 진행 중 알림, 그룹 요약, 이 앱 자신의 알림은 기록하지 않음 */
    fun shouldRecord(snapshot: NotificationSnapshot, ownPackageName: String): Boolean =
        !snapshot.isOngoing && !snapshot.isGroupSummary && snapshot.packageName != ownPackageName

    /** 시스템의 제거 이유 코드를 기록 사유로 변환 */
    fun removalReason(reasonCode: Int): RemovalReason = when (reasonCode) {
        REASON_CLICK -> RemovalReason.CLICK
        REASON_CANCEL, REASON_CANCEL_ALL -> RemovalReason.DISMISS
        REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> RemovalReason.APP_CANCEL
        else -> RemovalReason.OTHER
    }

    /** 마지막 메시지, 긴 본문, 본문 순으로 비어 있지 않은 첫 값 */
    fun resolveText(lastMessageText: CharSequence?, bigText: CharSequence?, text: CharSequence?): String? =
        listOf(lastMessageText, bigText, text).firstOrNull { !it.isNullOrEmpty() }?.toString()

    /**
     * 이벤트를 저장할 기록으로 변환
     *
     * 기록 대상이 아니거나, 게시이고 [isDuplicate]가 true이면 null.
     * 제거 기록의 시각은 [NotificationEvent.eventTime], 게시 기록의 시각은 알림의 postTime.
     */
    fun toRecord(
        event: NotificationEvent,
        ownPackageName: String,
        appNameOf: (String) -> String,
        isDuplicate: (key: String, title: String?, text: String?) -> Boolean,
    ): UsageLogRecord? {
        val snapshot = event.snapshot
        if (!shouldRecord(snapshot, ownPackageName)) return null
        val removalReason = event.removalReason
        if (removalReason != null) {
            return NotificationRemovedRecord(snapshot.key, snapshot.packageName, event.eventTime, removalReason)
        }
        if (isDuplicate(snapshot.key, event.title, event.text)) return null
        return NotificationPostedRecord(
            key = snapshot.key,
            packageName = snapshot.packageName,
            appName = appNameOf(snapshot.packageName),
            time = snapshot.postTime,
            title = event.title,
            text = event.text,
            category = event.category,
        )
    }
}
