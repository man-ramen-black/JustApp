package com.black.app.broadcast

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.black.app.R
import com.black.app.ui.maintab.main.appinstallnotification.AppInstallNotificationStatus
import com.black.core.util.Log
import com.black.core.util.NotificationUtil

/**
 * [PackageInstallReceiver]가 받은 다른 앱의 설치·업데이트를 알림으로 표시
 *
 * 제목은 앱 이름이며, 알림을 터치하면 그 앱 실행.
 * Android 11 이상은 [PackageInstallShortcut]이 게시한 바로가기를 연결한 대화 알림이라 알림창 왼쪽에 앱 아이콘이 표시되고,
 * 그 미만이거나 바로가기 게시에 실패하면 앱 아이콘을 large icon으로 표시.
 * 설치·업데이트 알림은 즉시 확인할 필요가 없어 소리 없이 낮은 우선순위로 표시.
 * 앱 설치 알림 화면의 테스트 알림도 [AppInstallNotificationStatus]가 JustApp 자신을 대상으로 이 함수를 호출해 표시.
 */
object PackageInstallNotifier {
    private const val NOTIFICATION_CHANNEL_ID = "PackageInstall"

    /**
     * 설치 알림 표시 가능 여부
     *
     * 앱 알림 허용(Android 13 이상 알림 권한 포함)만으로는 이 채널만 끈 경우를 알 수 없어 채널 중요도까지 확인.
     * 채널은 첫 알림 때 만들어지므로 아직 없으면 표시 가능으로 판단
     */
    fun canShow(context: Context): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }
        val channel = notificationManager.getNotificationChannelCompat(NOTIFICATION_CHANNEL_ID)
            ?: return true
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

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

        // 원본 크기 아이콘은 알림·바로가기 전달 데이터가 커지므로 large icon 규격으로 축소
        val resources = context.resources
        val appIcon = packageManager.getApplicationIcon(applicationInfo).toBitmap(
            resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
            resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height),
        )

        // 채널이 이미 있으면 이름 갱신·중요도 하향 외에는 아무 동작 없어 매번 호출 가능
        // 소리·진동·헤드업 없는 LOW, 이전 버전의 DEFAULT 채널도 사용자가 채널 설정을 바꾼 적 없으면 시스템이 LOW로 하향
        NotificationUtil.createNotificationChannel(
            context,
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.package_install_notification_channel_name),
            NotificationManagerCompat.IMPORTANCE_LOW,
        )

        // 패키지별 ID로 같은 앱은 앞선 알림 대체, 다른 앱끼리는 따로 표시
        val notificationId = packageName.hashCode()
        val messageResId = if (isUpdate) {
            R.string.package_install_notification_updated
        } else {
            R.string.package_install_notification_installed
        }
        val label = packageManager.getApplicationLabel(applicationInfo).toString()
        val message = context.getString(messageResId)

        // 대화 알림은 Android 11 이상만 적용, 바로가기 게시 실패 시 large icon 알림으로 대체
        val sender = Person.Builder().setName(label).setKey(packageName).build()
        val isConversation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            PackageInstallShortcut.publish(context, packageName, label, appIcon, launchIntent, sender)

        try {
            NotificationUtil.showNotification(
                context,
                NOTIFICATION_CHANNEL_ID,
                label,
                message,
                notificationId,
                // 상단바 small icon은 설치·업데이트 공통, 순환 화살표 같은 세부 모양은 상단바 크기에서 알아보기 어려움
                R.drawable.ic_app_install,
            ) { builder ->
                if (isConversation) {
                    // MessagingStyle 생성자 인자는 기기 사용자 자리이고 같은 Person의 메시지는 내가 보낸 메시지로 취급돼, 사용자는 JustApp·보낸 사람은 설치된 앱으로 분리
                    val deviceUser = Person.Builder().setName(context.getString(R.string.app_name)).build()
                    // 대화로 인식되지 않으면 1:1 대화 레이아웃이 보낸 사람 아이콘을 표시하므로 앱 아이콘 지정, 없으면 글자 아바타 표시
                    val senderWithIcon = sender.toBuilder().setIcon(IconCompat.createWithBitmap(appIcon)).build()
                    builder.setStyle(
                        NotificationCompat.MessagingStyle(deviceUser)
                            .addMessage(message, System.currentTimeMillis(), senderWithIcon)
                    )
                        // 바로가기 아이콘이 알림창 왼쪽에 표시돼 large icon 미지정
                        .setShortcutId(PackageInstallShortcut.shortcutId(packageName))
                } else {
                    // small icon은 단색 실루엣으로만 그려져 컬러 앱 아이콘은 large icon으로 표시
                    builder.setLargeIcon(appIcon)
                }
                // 액티비티 직접 실행이라 Android 12 알림 트램폴린 제한 대상 아님
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        notificationId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                    .setAutoCancel(true)
                    // 채널 없는 Android 8 미만용 우선순위, NotificationUtil 기본값 PRIORITY_MAX 대체
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            }
        } finally {
            // notify 호출 안에서 시스템이 바로가기를 확인·캐시하므로 정리는 반드시 알림 게시 뒤
            // 알림 게시가 예외로 끝나도 런처 길게 누르기 메뉴에 바로가기가 남지 않게 정리
            if (isConversation) {
                PackageInstallShortcut.removeAfterNotify(context, packageName)
            }
        }
    }
}
