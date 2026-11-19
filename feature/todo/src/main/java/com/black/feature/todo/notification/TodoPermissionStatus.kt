package com.black.feature.todo.notification

import android.content.Context
import com.black.core.util.NotificationUtil
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Todo 기능에 필요한 권한 허용 여부 확인
 *
 * ViewModel이 Context를 직접 갖지 않고 유닛 테스트에서 바꿀 수 있도록 확인만 따로 둠.
 */
class TodoPermissionStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Todo 알림 표시 가능 여부
     *
     * 앱 알림이 허용돼도 집 도착·시각 알림 채널 중 하나를 끄면 그 알림이 뜨지 않으므로 두 채널을 모두 확인함.
     */
    fun canShowTodoNotifications(): Boolean =
        NotificationUtil.canShow(context, TodoNotifier.HOME_ARRIVAL_CHANNEL_ID) &&
            NotificationUtil.canShow(context, TodoNotifier.TIME_CHANNEL_ID)

    /** 정확한 알람 허용 여부 */
    fun canScheduleExactAlarms(): Boolean = PermissionHelper.canScheduleExactAlarms(context)
}
