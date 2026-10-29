package com.black.app.broadcast

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.black.core.util.Log

/**
 * [PackageInstallNotifier]의 대화 알림이 참조하는 앱별 long-lived 동적 바로가기 게시·정리
 *
 * 대화 알림은 setShortcutId로 연결한 바로가기 아이콘을 알림창 왼쪽에 표시하므로 설치된 앱마다 바로가기 필요.
 * JustApp의 다른 동적 바로가기를 지우지 않도록 전체 삭제 대신 자기 ID만 정리.
 *
 * 런처 노출 제외 플래그 setExcludedFromSurfaces는 사용하지 않음. Android 13 이상 ShortcutService는 이 플래그가 붙은
 * 동적 바로가기를 AppSearch 연동이 켜진 기기에서만 따로 보관하고 Android 16은 보관하지 않아, 알림이 바로가기를 찾지 못하고 일반 알림으로 표시.
 * 대신 알림 표시 직후 [removeAfterNotify]로 삭제해 런처 길게 누르기 메뉴에 남지 않게 정리
 */
object PackageInstallShortcut {
    private const val SHORTCUT_ID_PREFIX = "PackageInstall:"

    /** 다른 기능의 바로가기 ID와 겹치지 않도록 접두어를 붙인 패키지별 바로가기 ID */
    fun shortcutId(packageName: String): String = "$SHORTCUT_ID_PREFIX$packageName"

    /**
     * 대화 알림용 바로가기 게시
     *
     * 바로가기 서비스 예외는 로그만 남기고 false를 반환해 호출부가 large icon 알림으로 대체
     *
     * @return 게시 성공 여부
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun publish(context: Context, packageName: String, label: String, icon: Bitmap, launchIntent: Intent, sender: Person): Boolean {
        return try {
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(packageName))
                // 대화 제목 자리에 표시
                .setShortLabel(label)
                // 알림창 왼쪽 대화 아이콘
                .setIcon(IconCompat.createWithBitmap(icon))
                // 대화 설정·대화 위젯 등 시스템 화면에서 눌러도 해당 앱 실행
                .setIntent(launchIntent)
                .setPerson(sender)
                // false면 임시 바로가기로 취급돼 대화 아이콘 미노출
                .setLongLived(true)
                .build()
            // 동적 바로가기 개수 상한 도달 시 시스템이 순위가 가장 낮은 기존 동적 바로가기를 제거
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (exception: IllegalStateException) {
            // 사용자 잠금 상태 등 바로가기 서비스 거부
            Log.w("shortcut publish failed : $packageName, $exception")
            false
        } catch (exception: IllegalArgumentException) {
            // 빈 라벨·변경 불가 바로가기와 ID 충돌 등
            Log.w("shortcut publish failed : $packageName, $exception")
            false
        }
    }

    /**
     * 대화 알림 표시 후 런처 길게 누르기 메뉴에 남는 자기 바로가기 삭제
     *
     * NotificationManagerService가 notify 호출 안에서 long-lived 바로가기를 알림용으로 캐시하므로,
     * 동적 바로가기에서 지워도 알림의 대화 표시는 유지
     */
    fun removeAfterNotify(context: Context, packageName: String) {
        // 알림은 이미 표시돼 정리 실패로 프로세스가 종료되지 않도록 로그만 기록
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(packageName)))
        } catch (exception: IllegalStateException) {
            Log.w("shortcut remove failed : $packageName, $exception")
        }
    }
}
