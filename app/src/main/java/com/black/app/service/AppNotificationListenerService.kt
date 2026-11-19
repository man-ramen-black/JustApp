package com.black.app.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.black.core.model.HomeTransition
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import com.black.core.util.SerialEventQueue
import com.black.core.wifi.WifiConnectionMonitor
import com.black.feature.todo.data.usecase.TodoHomeArrivalUseCase
import com.black.feature.usagelog.data.usecase.HomeTransitionListener
import com.black.feature.usagelog.data.usecase.NotificationRecorder
import com.black.feature.usagelog.data.usecase.WifiRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * JustApp의 유일한 알림 리스너(앱 공용 알림 리스너)
 *
 * 세 기능이 함께 쓰므로 기능 모듈에 두지 않고 앱에 하나로 둠.
 * - 기기 사용 기록: 알림 게시·제거를 [NotificationRecorder]에, Wi-Fi 연결·해제 감시([WifiConnectionMonitor])의 보고를 [WifiRecorder]에 넘김
 * - Todo 집 도착 알림: [WifiRecorder]가 알린 집 전환을 [homeTransitions]로 받은 순서대로 [TodoHomeArrivalUseCase]에 넘김
 * - 수면 루틴: 활성 미디어 세션 조회에 알림 접근이 허용된 리스너 컴포넌트로 씀(이 서비스가 따로 하는 일은 없음)
 *
 * 기능 모듈은 이 클래스를 참조하지 않고 [PermissionHelper.findNotificationListenerService]로 매니페스트 선언에서 찾으므로, 매니페스트에 다른 알림 리스너를 추가하지 않음.
 * 클래스 경로(매니페스트의 `android:name`)가 바뀌면 시스템에 저장된 알림 접근 허용과 맞지 않아 허용이 풀리고, 업데이트 뒤 다시 허용해야 함.
 * 기존 서비스(`AppAccessibilityService`)처럼 Hilt 주입 없이 기록기와 처리기를 직접 만듦(서비스에 `@AndroidEntryPoint`를 쓰는 곳이 없음).
 * 알림 제목·본문은 로그에 남기지 않음.
 */
class AppNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val homeArrivalUseCase by lazy { TodoHomeArrivalUseCase(applicationContext) }

    /**
     * 알림 게시·제거 기록기
     *
     * 필드 초기화 시점에는 `applicationContext`가 아직 없어 [onCreate]에서 만듦.
     */
    private lateinit var notificationRecorder: NotificationRecorder

    /**
     * Wi-Fi 연결·해제 감시
     *
     * 알림 접근이 처음 연결될 때 만들어, 한 번도 연결되지 않고 끝나는 서비스는 감시기를 만들지 않음.
     * 감시 세션마다 [wifiRecorder]의 기록 세션에 연결·해제 보고를 넘김.
     */
    private var wifiMonitor: WifiConnectionMonitor? = null

    /**
     * [wifiMonitor]가 넘긴 Wi-Fi 연결·해제 보고의 기록기
     *
     * 감시기와 같은 시점에 만듦.
     * 다시 연결돼도 같은 기록기를 써서 새 세션이 이전 세션의 저장이 끝나기를 기다릴 수 있음.
     * 집 도착·출발 알림은 [HomeTransitionListener]로 받아 [homeTransitions]에 넣어 Todo 집 도착 알림 처리와 마지막 집 출발 시각 저장에 씀.
     * 기기 사용 기록의 즉시 전송 예약은 기록기가 리스너를 부른 뒤 따로 함.
     */
    private var wifiRecorder: WifiRecorder? = null

    /**
     * Todo 집 도착 처리에 받은 순서대로 하나씩 넘기는 집 전환 대기열
     *
     * 재부팅 직후에는 추정 출발과 실제 도착이 거의 동시에 와, 전환마다 코루틴을 따로 띄우면 도착 판정이 추정 출발 저장보다 먼저 돌아 알림을 막지 못할 수 있어 하나씩 처리함.
     * 기기 사용 기록 전송과 따로 실패하도록 전송 예약보다 먼저 넣어 별도 코루틴에서 처리함([WifiRecorder]가 리스너를 전송 예약보다 먼저 부름).
     * 소비 코루틴은 [onCreate]에서 띄우고, 한 전환의 처리 실패는 클래스 이름만 남기고 다음 전환으로 넘어감.
     */
    private val homeTransitions = SerialEventQueue<HomeTransition>()

    override fun onCreate() {
        super.onCreate()
        notificationRecorder = NotificationRecorder(applicationContext, scope)
        // 단일 소비자라 앞 전환의 출발 시각 저장이 끝난 뒤 다음 도착을 판정함
        scope.launch {
            homeTransitions.consume(
                onFailure = { Log.w("[AppNotificationListenerService] 집 전환 처리 실패: ${it::class.java.simpleName}") },
            ) { transition -> homeArrivalUseCase.onHomeTransition(transition) }
        }
    }

    /** 알림 접근이 연결되면 Wi-Fi 감시도 시작 */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val monitor = wifiMonitor ?: WifiConnectionMonitor(applicationContext).also { wifiMonitor = it }
        val recorder = wifiRecorder ?: createWifiRecorder().also { wifiRecorder = it }
        monitor.start(recorder::openSession)
    }

    override fun onListenerDisconnected() {
        wifiMonitor?.stop()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        wifiMonitor?.stop()
        notificationRecorder.close()
        homeTransitions.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun createWifiRecorder() = WifiRecorder(
        context = applicationContext,
        scope = scope,
        listener = HomeTransitionListener { transition -> homeTransitions.offer(transition) },
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        notificationRecorder.onPosted(sbn)
    }

    /**
     * API 26 미만에서 시스템이 부르는 제거 콜백
     *
     * 제거 이유를 알 수 없음.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationRecorder.onRemoved(sbn, null)
        }
    }

    /**
     * API 26 이상에서 시스템이 부르는 제거 콜백
     *
     * 상위 구현은 인자 하나짜리 콜백을 불러 중복 기록되므로 부르지 않음.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        notificationRecorder.onRemoved(sbn, reason)
    }
}
