package com.black.app.ui.main.appinstallnotification

import android.content.Context
import com.black.app.broadcast.PackageInstallNotifier
import com.black.app.service.ForegroundServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [AppInstallNotificationScreen]이 보여 주는 설치 알림 표시 가능 여부 조회와 테스트 알림 표시
 *
 * 서비스 실행 설정·시작·중지는 [ForegroundServiceController]가 맡고, 이 클래스는 알림 쪽 시스템 상태만 다룸
 */
class AppInstallNotificationStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 미리보기 예시 앱 패키지(항상 설치돼 있고 실행 인텐트가 있는 JustApp 자신) */
    val previewPackageName: String = context.packageName

    /** 설치 알림을 게시하는 앱 패키지(JustApp 자신, 대화 알림 아이콘 배지에 표시) */
    val notifierPackageName: String = context.packageName

    /** 앱 알림 허용과 설치 알림 채널 차단 여부를 함께 반영한 설치 알림 표시 가능 여부 */
    fun canShowNotification(): Boolean = PackageInstallNotifier.canShow(context)

    /** 예시 앱으로 실제 설치·업데이트 알림 표시 */
    fun showPreviewNotification(isUpdate: Boolean) {
        PackageInstallNotifier.show(context, previewPackageName, isUpdate)
    }
}
