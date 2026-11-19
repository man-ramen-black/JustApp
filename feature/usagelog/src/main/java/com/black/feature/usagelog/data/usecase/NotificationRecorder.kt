package com.black.feature.usagelog.data.usecase

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.black.core.util.InstalledAppResolver
import com.black.core.util.Log
import com.black.core.util.SerialEventQueue
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.RemovalReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 알림 게시와 제거를 기기 사용 기록으로 저장하는 기록기
 *
 * 앱 공용 알림 리스너(앱의 `AppNotificationListenerService`)가 알림 콜백마다 [onPosted]·[onRemoved]로 넘김.
 * 콜백 안에서는 기록에 필요한 값만 꺼내 대기열에 넣고, 판정과 저장은 만들 때 [scope]에서 띄운 단일 소비 코루틴이 도착 순서대로 처리함.
 * 알림 제목·본문은 로그에 남기지 않음.
 *
 * @param ownPackageName 기록에서 뺄 이 앱의 패키지 이름
 * @param appNameOf 게시 기록에 남길 앱 이름 조회
 */
class NotificationRecorder(
    private val ownPackageName: String,
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val appNameOf: (String) -> String,
    scope: CoroutineScope,
) {

    /**
     * Hilt 주입 없이 저장소를 직접 만드는 서비스용 생성자
     *
     * 기존 서비스(`AppAccessibilityService`)처럼 서비스에 `@AndroidEntryPoint`를 쓰는 곳이 없음.
     */
    constructor(context: Context, scope: CoroutineScope) : this(
        ownPackageName = context.packageName,
        settings = UsageLogSettingsRepository(context.applicationContext),
        repository = UsageLogRepository(context.applicationContext),
        appNameOf = InstalledAppResolver(context.applicationContext)::resolveLabel,
        scope = scope,
    )

    private val duplicateFilter = NotificationDuplicateFilter()

    /** 콜백 도착 순서대로 하나씩 처리하기 위한 알림 이벤트 대기열 */
    private val events = SerialEventQueue<NotificationEvent>()

    init {
        scope.launch {
            // 단일 소비자라 같은 key의 게시와 제거가 도착 순서대로 판정되고 저장됨
            events.consume(
                // 한 건의 실패로 이후 알림이 모두 멈추지 않도록 실패는 로그만 남기고 다음 이벤트로 넘어감
                // 알림 내용이 섞일 수 있어 예외 메시지는 남기지 않고 클래스 이름만 기록
                onFailure = { Log.w("[NotificationRecorder] 알림 처리 실패: ${it::class.java.simpleName}") },
            ) { event ->
                NotificationRecordRules.process(
                    event = event,
                    ownPackageName = ownPackageName,
                    appNameOf = appNameOf,
                    duplicateFilter = duplicateFilter,
                    // 끈 직후 도착한 알림도 끈 상태로 판정하도록 이벤트마다 읽음
                    isCollectionEnabled = { settings.isCollectionEnabled() },
                    save = { repository.add(listOf(it)) },
                )
            }
        }
    }

    /** 알림 게시 콜백 */
    fun onPosted(sbn: StatusBarNotification) {
        enqueue(sbn, null)
    }

    /**
     * 알림 제거 콜백
     *
     * [systemReason]은 시스템의 제거 이유 코드(`NotificationListenerService.REASON_*`)이며,
     * null이면 이유를 알 수 없는 제거(API 26 미만의 인자 하나짜리 제거 콜백)라 [RemovalReason.OTHER]로 기록함.
     */
    fun onRemoved(sbn: StatusBarNotification, systemReason: Int?) {
        val removalReason = if (systemReason != null) {
            NotificationRecordRules.removalReason(systemReason)
        } else {
            RemovalReason.OTHER
        }
        enqueue(sbn, removalReason)
    }

    /**
     * 대기열 닫기
     *
     * 뒤에 도착한 콜백은 버리고, 소비 코루틴은 이미 받은 이벤트까지 처리한 뒤 끝남(그 전에 scope가 취소되면 남은 이벤트는 처리하지 않음).
     */
    fun close() {
        events.close()
    }

    /**
     * 콜백 안에서 시각과 기록에 필요한 값을 꺼내 대기열에 넣음
     *
     * 시스템 객체는 넘기지 않음.
     */
    private fun enqueue(sbn: StatusBarNotification, removalReason: RemovalReason?) {
        val eventTime = System.currentTimeMillis()
        try {
            val notification = sbn.notification
            val snapshot = NotificationSnapshot(
                key = sbn.key,
                packageName = sbn.packageName,
                postTime = sbn.postTime,
                isOngoing = sbn.isOngoing,
                isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
            // 제거 기록에는 제목·본문·카테고리가 쓰이지 않아 읽지 않음
            val event = if (removalReason != null) {
                NotificationEvent(snapshot, null, null, null, null, removalReason, eventTime)
            } else {
                val extras = notification.extras
                val lastMessage = NotificationRecordRules.lastMessageOf(notification)
                NotificationEvent(
                    snapshot = snapshot,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    text = NotificationRecordRules.resolveText(
                        lastMessage?.text,
                        extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                        extras.getCharSequence(Notification.EXTRA_TEXT),
                    ),
                    category = notification.category,
                    lastMessageTime = lastMessage?.timestamp,
                    removalReason = null,
                    eventTime = eventTime,
                )
            }
            events.offer(event)
        } catch (failure: Exception) {
            Log.w("[NotificationRecorder] 알림 값 읽기 실패: ${failure::class.java.simpleName}")
        }
    }
}
