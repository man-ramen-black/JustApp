package com.black.feature.dday.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.black.core.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * D-Day 위젯 수신기
 *
 * 위젯이 놓이거나 갱신될 때 자정 알람과 캘린더 변경 감시를 걸고, 마지막 위젯이 지워지면 둘 다 취소함.
 * 재부팅·앱 업데이트 뒤에도 시스템이 `onUpdate`를 부르므로 따로 부팅 수신기를 두지 않음.
 *
 * Glance 1.2.0의 `onUpdate`는 이미 `goAsync`를 써서 그 뒤의 `goAsync()`는 null이 되므로, `onUpdate`·`onEnabled`·`onDisabled`의
 * 예약과 취소는 suspend가 아닌 호출이라 `goAsync` 없이 동기로 처리함(`onEnabled`·`onDisabled`는 Glance가 `goAsync`를 쓰지 않음).
 * 자정·시간 변경 액션은 놓인 위젯이 없으면 알람과 감시를 취소하고 끝내 위젯이 없는 기기에서 매일 자정 앱이 뜨지 않게 하며,
 * 갱신이 실패해도 다음 자정 예약이 남도록 예약을 갱신보다 먼저 함.
 * 자정·시간 변경 액션은 `super.onReceive`를 거치지 않아 `goAsync`가 처음 불리므로 non-null이며, suspend인 갱신을 IO에서 처리함.
 */
class DdayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = DdayWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleRefresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DdayWidgetRefresher(context).cancelAll()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> launchAsync {
                val refresher = DdayWidgetRefresher(context)
                if (!refresher.hasWidgets()) {
                    refresher.cancelAll()
                    return@launchAsync
                }
                refresher.scheduleNextMidnight()
                refresher.refresh()
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun scheduleRefresh(context: Context) {
        val refresher = DdayWidgetRefresher(context)
        refresher.scheduleNextMidnight()
        refresher.watchCalendarChanges()
    }

    /**
     * [block]을 IO에서 실행하고 끝나면 `goAsync` 결과를 마침
     *
     * `super.onReceive`를 거치지 않는 경로에서만 부르므로 `goAsync`는 non-null이지만, 단위 테스트처럼 직접 호출할 때는 null이라 안전 호출로 마침.
     */
    private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runSuspendCatching { block() }
            pendingResult?.finish()
        }
    }

    companion object {
        const val ACTION_MIDNIGHT_REFRESH = "com.black.feature.dday.action.MIDNIGHT_REFRESH"
    }
}
