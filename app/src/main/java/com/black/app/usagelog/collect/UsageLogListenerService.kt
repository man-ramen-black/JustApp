package com.black.app.usagelog.collect

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.upload.SendReason
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 알림 접근 권한으로 알림 게시와 제거를 기록하는 서비스
 *
 * 기존 서비스(`UsageTimerAccessibility`)처럼 Hilt 주입 없이 저장소를 직접 만듦(서비스에 `@AndroidEntryPoint`를 쓰는 곳이 없음).
 * 알림 제목·본문은 로그에 남기지 않음.
 */
class UsageLogListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { UsageLogRepository(applicationContext) }
    private val appResolver by lazy { InstalledAppResolver(applicationContext) }
    private val duplicateFilter = NotificationDuplicateFilter()

    /**
     * Wi-Fi 연결·해제 감시
     *
     * 집 도착·출발 알림은 [HomeTransitionListener]로 받아 [UsageLogScheduler.enqueueSend]로 즉시 전송을 예약함.
     */
    private val wifiMonitor by lazy {
        WifiMonitor(
            context = applicationContext,
            settings = UsageLogSettingsRepository(applicationContext),
            repository = repository,
            scope = scope,
            listener = HomeTransitionListener { arrived -> enqueueHomeSend(arrived) },
        )
    }

    /**
     * 수집 설정 최신 값
     *
     * 첫 값이 오기 전에는 null.
     */
    private val collectionEnabled by lazy {
        UsageLogSettingsRepository(applicationContext).collectionEnabled
            // 읽기 예외로 흐름이 끝나면 대기가 영영 끝나지 않으므로 잠시 뒤 다시 구독
            .retry { cause ->
                Log.w("[UsageLogListenerService] 수집 설정 읽기 실패: ${cause::class.java.simpleName}")
                delay(COLLECTION_RETRY_DELAY_MILLIS)
                true
            }
            .map<Boolean, Boolean?> { it }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    /** 콜백 도착 순서대로 하나씩 처리하기 위한 알림 이벤트 대기열 */
    private val events = Channel<NotificationEvent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // 단일 소비자라 같은 key의 게시와 제거가 도착 순서대로 판정되고 저장됨
            for (event in events) {
                try {
                    handle(event)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 한 건의 실패로 루프가 끝나 이후 알림이 모두 멈추지 않도록 다음 이벤트로 넘어감
                    Log.w("[UsageLogListenerService] 알림 처리 실패: ${failure::class.java.simpleName}")
                }
            }
        }
    }

    /** 집 도착이면 [SendReason.HOME_ARRIVE], 출발이면 [SendReason.HOME_LEAVE]로 전송 예약 */
    private fun enqueueHomeSend(arrived: Boolean) {
        try {
            UsageLogScheduler(applicationContext).enqueueSend(if (arrived) SendReason.HOME_ARRIVE else SendReason.HOME_LEAVE)
        } catch (failure: Exception) {
            // 예외가 감시기로 번져 Wi-Fi 감시가 멈추지 않도록 클래스 이름만 기록
            Log.w("[UsageLogListenerService] 전송 예약 실패: ${failure::class.java.simpleName}")
        }
    }

    /** 알림 접근이 연결되면 Wi-Fi 감시도 시작 */
    override fun onListenerConnected() {
        super.onListenerConnected()
        wifiMonitor.start()
    }

    override fun onListenerDisconnected() {
        wifiMonitor.stop()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        wifiMonitor.stop()
        events.close()
        scope.cancel()
        super.onDestroy()
    }

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
                NotificationEvent(snapshot, null, null, null, removalReason, eventTime)
            } else {
                val extras = notification.extras
                val lastMessageText = NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(notification)
                    ?.messages?.lastOrNull()?.text
                NotificationEvent(
                    snapshot = snapshot,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    text = NotificationRecordRules.resolveText(
                        lastMessageText,
                        extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                        extras.getCharSequence(Notification.EXTRA_TEXT),
                    ),
                    category = notification.category,
                    removalReason = null,
                    eventTime = eventTime,
                )
            }
            events.trySend(event)
        } catch (failure: Exception) {
            Log.w("[UsageLogListenerService] 알림 값 읽기 실패: ${failure::class.java.simpleName}")
        }
    }

    /** 수집 설정의 첫 값을 기다린 뒤 이벤트를 기록 */
    private suspend fun handle(event: NotificationEvent) {
        // 제거된 key는 수집 여부와 상관없이 잊어 같은 내용의 재게시를 놓치지 않음
        if (event.removalReason != null) duplicateFilter.forget(event.snapshot.key)
        if (!collectionEnabled.filterNotNull().first()) return

        val record = NotificationRecordRules.toRecord(
            event = event,
            ownPackageName = applicationContext.packageName,
            appNameOf = appResolver::resolveLabel,
            isDuplicate = duplicateFilter::isDuplicate,
        ) ?: return
        try {
            repository.add(listOf(record))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // 알림 내용이 섞일 수 있어 예외 메시지는 남기지 않고 클래스 이름만 기록
            Log.w("[UsageLogListenerService] 알림 기록 저장 실패: ${failure::class.java.simpleName}")
        }
    }

    private companion object {
        const val COLLECTION_RETRY_DELAY_MILLIS = 5_000L
    }
}
