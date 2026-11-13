package com.black.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.black.core.broadcast.ReceiverRegistration
import com.black.core.util.Log

/**
 * 다른 앱의 설치·업데이트 수신 후 [PackageInstallNotifier]로 알림 표시
 *
 * targetSdk 26 이상은 ACTION_PACKAGE_ADDED를 매니페스트 리시버로 받을 수 없어 ForegroundService 실행 중에만 런타임 등록
 */
class PackageInstallReceiver : BroadcastReceiver() {
    /** ForegroundService 수명에 맞춘 리시버 등록·해제 */
    companion object {
        /** 해제 없이 재호출돼도 같은 설치에 알림 처리가 중복되지 않도록 등록 여부를 보관하는 홀더 */
        private val registration = ReceiverRegistration(
            // 패키지 브로드캐스트의 data가 package:<패키지명> 형식이라 scheme 지정 필수
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
            PackageInstallReceiver(),
        )

        /** 설치·업데이트 수신 등록 */
        fun register(context: Context) {
            registration.register(context)
        }

        fun unregister(context: Context) {
            registration.unregister(context)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("context = $context, intent = $intent")
        context ?: return
        intent ?: return

        // 설치·업데이트 외 액션 무시
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) {
            return
        }

        // data(package:<패키지명>) 없는 인텐트 무시
        val packageName = intent.data?.schemeSpecificPart
            ?: run {
                Log.w("package name is null")
                return
            }

        // 자기 앱 설치·업데이트는 알림 대상 제외
        if (packageName == context.packageName) {
            return
        }

        // 업데이트는 PACKAGE_REMOVED·ADDED·REPLACED가 차례로 오므로 ADDED의 EXTRA_REPLACING으로만 구분해 알림 중복 방지
        val isUpdate = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        Log.d("packageName = $packageName, isUpdate = $isUpdate")
        PackageInstallNotifier.show(context, packageName, isUpdate)
    }
}
