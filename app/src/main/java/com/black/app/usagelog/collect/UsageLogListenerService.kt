package com.black.app.usagelog.collect

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.app.util.InstalledAppResolver
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 알림 접근 권한으로 알림 게시와 제거를 기록하는 서비스
 *
 * 기존 서비스(`UsageTimerAccessibility`)처럼 Hilt 주입 없이 저장소를 직접 만듦(서비스에 `@AndroidEntryPoint`를 쓰는 곳이 없음).
 * 알림 제목·본문은 로그에 남기지 않음.
 */
class UsageLogListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { UsageLogSettingsRepository(applicationContext) }
    private val repository by lazy { UsageLogRepository(applicationContext) }
    private val appResolver by lazy { InstalledAppResolver(applicationContext) }
    private val duplicateFilter = NotificationDuplicateFilter()

    /**
     * Wi-Fi 연결·해제 감시
     *
     * 알림 접근이 처음 연결될 때 만들어, 한 번도 연결되지 않고 끝나는 서비스는 감시기를 만들지 않음.
     * 다시 연결돼도 같은 감시기를 써서 새 세션이 이전 세션의 저장이 끝나기를 기다릴 수 있음.
     * 집 도착·출발 알림은 [HomeTransitionListener]로 받아 [UsageLogScheduler.enqueueSend]로 즉시 전송을 예약함.
     */
    private var wifiMonitor: WifiMonitor? = null

    /** 콜백 도착 순서대로 하나씩 처리하기 위한 알림 이벤트 대기열 */
    private val events = Channel<NotificationEvent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // 단일 소비자라 같은 key의 게시와 제거가 도착 순서대로 판정되고 저장됨
            for (event in events) {
                try {
                    NotificationRecordRules.process(
                        event = event,
                        ownPackageName = applicationContext.packageName,
                        appNameOf = appResolver::resolveLabel,
                        duplicateFilter = duplicateFilter,
                        // 끈 직후 도착한 알림도 끈 상태로 판정하도록 이벤트마다 읽음
                        isCollectionEnabled = { settings.isCollectionEnabled() },
                        save = { repository.add(listOf(it)) },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 한 건의 실패로 루프가 끝나 이후 알림이 모두 멈추지 않도록 다음 이벤트로 넘어감
                    // 알림 내용이 섞일 수 있어 예외 메시지는 남기지 않고 클래스 이름만 기록
                    Log.w("[UsageLogListenerService] 알림 처리 실패: ${failure::class.java.simpleName}")
                }
            }
        }
    }

    /**
     * 집 도착이면 [SendReason.HOME_ARRIVE], 출발이면 [SendReason.HOME_LEAVE]로 전송 예약
     *
     * 예약 예외는 [WifiMonitor]가 잡아 클래스 이름만 기록함.
     */
    private fun enqueueHomeSend(arrived: Boolean) {
        UsageLogScheduler(applicationContext).enqueueSend(if (arrived) SendReason.HOME_ARRIVE else SendReason.HOME_LEAVE)
    }

    /** 알림 접근이 연결되면 Wi-Fi 감시도 시작 */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val monitor = wifiMonitor ?: createWifiMonitor().also { wifiMonitor = it }
        monitor.start()
    }

    override fun onListenerDisconnected() {
        wifiMonitor?.stop()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        wifiMonitor?.stop()
        events.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun createWifiMonitor() = WifiMonitor(
        context = applicationContext,
        settings = settings,
        repository = repository,
        scope = scope,
        listener = HomeTransitionListener { arrived -> enqueueHomeSend(arrived) },
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        enqueue(sbn, null)
    }

    /**
     * API 26 미만에서 시스템이 부르는 제거 콜백
     *
     * 제거 이유를 알 수 없음.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) enqueue(sbn, RemovalReason.OTHER)
    }

    /**
     * API 26 이상에서 시스템이 부르는 제거 콜백
     *
     * 상위 구현은 인자 하나짜리 콜백을 불러 중복 기록되므로 부르지 않음.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        enqueue(sbn, NotificationRecordRules.removalReason(reason))
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
            events.trySend(event)
        } catch (failure: Exception) {
            Log.w("[UsageLogListenerService] 알림 값 읽기 실패: ${failure::class.java.simpleName}")
        }
    }
}
