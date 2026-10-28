package com.black.app.broadcast

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.black.app.R
import com.black.core.util.Log
import com.black.core.util.NotificationUtil

/**
 * [PackageInstallReceiver]가 받은 다른 앱의 설치·업데이트를 알림으로 표시
 *
 * 제목은 앱 이름, large icon은 앱 아이콘이며, 알림을 터치하면 그 앱 실행
 */
object PackageInstallNotifier {
    private const val NOTIFICATION_CHANNEL_ID = "PackageInstall"

    /** 알림 권한·앱 정보·실행 인텐트 중 하나라도 없으면 알림 없이 종료 */
    fun show(context: Context, packageName: String, isUpdate: Boolean) {
        // Android 13 이상 알림 권한 미허용 시 표시 불가, 13 미만은 권한 자체가 없어 검사 제외
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("notification permission not granted")
            return
        }

        // 수신 직후 삭제 등으로 앱 정보 조회 실패 시 종료
        val packageManager = context.packageManager
        val applicationInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (notFound: PackageManager.NameNotFoundException) {
            Log.w("application not found : $packageName")
            return
        }

        // 런처 액티비티 없는 앱은 터치로 실행할 수 없어 알림 제외, 반환 인텐트에 FLAG_ACTIVITY_NEW_TASK 포함
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: run {
                Log.d("launch intent not found : $packageName")
                return
            }

        // 원본 크기 아이콘은 알림 전달 데이터가 커지므로 large icon 규격으로 축소
        val resources = context.resources
        val largeIcon = packageManager.getApplicationIcon(applicationInfo).toBitmap(
            resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
            resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height),
        )

        // 채널이 이미 있으면 아무 동작 없어 매번 호출 가능
        NotificationUtil.createNotificationChannel(
            context,
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.package_install_notification_channel_name),
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )

        // 패키지별 ID로 같은 앱은 앞선 알림 대체, 다른 앱끼리는 따로 표시
        val notificationId = packageName.hashCode()
        // 상단바 small icon은 설치·업데이트를 구분하는 전용 아이콘
        val (messageResId, smallIconResId) = if (isUpdate) {
            R.string.package_install_notification_updated to R.drawable.ic_app_update
        } else {
            R.string.package_install_notification_installed to R.drawable.ic_app_install
        }
        NotificationUtil.showNotification(
            context,
            NOTIFICATION_CHANNEL_ID,
            packageManager.getApplicationLabel(applicationInfo).toString(),
            context.getString(messageResId),
            notificationId,
            smallIconResId,
        ) { builder ->
            // small icon은 단색 실루엣으로만 그려져 컬러 앱 아이콘은 large icon으로 표시
            builder.setLargeIcon(largeIcon)
                // 액티비티 직접 실행이라 Android 12 알림 트램폴린 제한 대상 아님
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        notificationId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setAutoCancel(true)
        }
    }
}
