package com.black.app.assistanttask

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.black.app.R
import com.black.app.deeplink.Deeplink
import com.black.app.ui.MainActivity
import com.black.core.util.DataUtil
import com.black.core.util.NotificationUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/** 비서 Todo 알림 표시와 제거 */
class AssistantTaskNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * 집 도착 알림 표시
     *
     * 제목은 개수, 본문은 이름 목록이며 펼치면 앞 [MAX_LINES]개를 줄마다 보임.
     * 누르면 Todo 화면을 열고, 같은 알림 ID라 다시 띄우면 앞 알림을 대체함.
     *
     * @return 알림을 띄웠으면 true, 대상이 없거나 알림 권한이 없거나 집 도착 채널이 꺼져 있으면 false
     */
    @SuppressLint("MissingPermission") // 알림 허용 확인 뒤 호출
    fun showHomeArrival(tasks: List<AssistantTask>): Boolean {
        // 대상 없음, 앱 알림 꺼짐, 이 채널만 꺼짐이면 띄우지 않음
        if (tasks.isEmpty() || !NotificationUtil.canShow(context, HOME_ARRIVAL_CHANNEL_ID)) return false

        // 채널 생성(이미 있으면 아무 동작 없음)
        NotificationUtil.createNotificationChannel(
            context,
            HOME_ARRIVAL_CHANNEL_ID,
            context.getString(R.string.assistant_task_home_arrival_channel),
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
        // 이름이 빈 항목은 화면처럼 제목 없음으로 대체
        val titles = tasks.map(::displayTitle)
        // 제목은 개수, 본문은 이름 목록
        NotificationUtil.showNotification(
            context,
            HOME_ARRIVAL_CHANNEL_ID,
            context.getString(R.string.assistant_task_home_arrival_title, tasks.size),
            titles.joinToString(", "),
            HOME_ARRIVAL_NOTIFICATION_ID,
        ) { builder ->
            // 펼친 줄은 앞 MAX_LINES개만
            val inbox = NotificationCompat.InboxStyle()
            titles.take(MAX_LINES).forEach { inbox.addLine(it) }
            // 누르면 Todo 화면 열기, 누르면 알림 제거
            builder.setStyle(inbox)
                .setContentIntent(openTodoIntent(HOME_ARRIVAL_NOTIFICATION_ID))
                .setAutoCancel(true)
                // 채널 없는 Android 8 미만용 우선순위, NotificationUtil 기본값 PRIORITY_MAX 대체
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }
        return true
    }

    /**
     * 시각 알림 표시
     *
     * 제목은 Todo 이름, 본문은 기기 시간대 기준 기한 시각(`HH:mm`).
     * 알림 ID는 page ID의 `hashCode()`라 Todo마다 따로 쌓이고 같은 Todo는 앞 알림을 대체함. 누르면 Todo 화면을 엶.
     *
     * @return 알림을 띄웠으면 true, 기한에 시각이 없거나 알림 권한이 없거나 시각 알림 채널이 꺼져 있으면 false
     */
    @SuppressLint("MissingPermission") // 알림 허용 확인 뒤 호출
    fun showTimeReminder(task: AssistantTask): Boolean {
        // 시각 없는 기한, 앱 알림 꺼짐, 이 채널만 꺼짐이면 띄우지 않음
        val due = task.due as? AssistantTaskDue.DateTime ?: return false
        if (!NotificationUtil.canShow(context, TIME_CHANNEL_ID)) return false

        // 채널 생성(이미 있으면 아무 동작 없음)
        NotificationUtil.createNotificationChannel(
            context,
            TIME_CHANNEL_ID,
            context.getString(R.string.assistant_task_time_channel),
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
        // 제목은 이름, 본문은 기한 시각
        val notificationId = timeReminderNotificationId(task.pageId)
        NotificationUtil.showNotification(
            context,
            TIME_CHANNEL_ID,
            displayTitle(task),
            DataUtil.formatDateTime(TIME_PATTERN, Locale.US, due.epochMillis),
            notificationId,
        ) { builder ->
            // 누르면 Todo 화면 열기, 누르면 알림 제거
            builder.setContentIntent(openTodoIntent(notificationId))
                .setAutoCancel(true)
                // 채널 없는 Android 8 미만용 우선순위, 채널 중요도 HIGH에 맞춤
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }
        return true
    }

    /**
     * [pageId] Todo의 시각 알림 제거
     *
     * 집 도착 알림은 여러 Todo를 묶은 알림이라 건드리지 않음.
     * 임시 ID(`local-`)인 동안 뜬 시각 알림은 동기화로 실제 page ID로 바뀐 뒤 부르면 알림 ID가 달라 지워지지 않음.
     */
    fun cancelTimeReminder(pageId: String) {
        NotificationUtil.cancelNotification(context, timeReminderNotificationId(pageId))
    }

    /** [showTimeReminder]와 [cancelTimeReminder]가 함께 쓰는 Todo별 시각 알림 ID */
    private fun timeReminderNotificationId(pageId: String): Int = pageId.hashCode()

    /** 이름이 빈 Todo는 화면처럼 제목 없음으로 대체한 이름 */
    private fun displayTitle(task: AssistantTask): String =
        task.title.ifBlank { context.getString(R.string.assistant_task_untitled) }

    /** 누르면 [openTodoActivityIntent]로 Todo 화면을 여는 PendingIntent */
    private fun openTodoIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        openTodoActivityIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val HOME_ARRIVAL_CHANNEL_ID = "AssistantTaskHomeArrival"
        const val HOME_ARRIVAL_NOTIFICATION_ID = 7
        const val TIME_CHANNEL_ID = "AssistantTaskTime"
        private const val MAX_LINES = 5
        private const val TIME_PATTERN = "HH:mm"

        /**
         * 알림을 누르면 여는 Todo 화면 인텐트
         *
         * 인텐트 필터 해석은 black 스킴 필터에 DEFAULT 카테고리가 없어 걸러지므로 [MainActivity]를 지정한 명시적 인텐트로 엶.
         * 계측 테스트가 같은 인텐트로 [MainActivity]를 띄워 Todo 화면이 열리는지 확인함.
         */
        internal fun openTodoActivityIntent(context: Context): Intent =
            Intent(Intent.ACTION_VIEW, Deeplink.navigateUri(Deeplink.PathNavigate.TODO), context, MainActivity::class.java)
    }
}
