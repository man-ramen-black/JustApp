package com.black.feature.usagelog.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.black.core.util.ComponentExtensions.launchAsync
import com.black.core.util.Log
import com.black.core.util.runSuspendCatching
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.data.usecase.SmsCollector
import com.black.feature.usagelog.data.usecase.UsageLogCollectionController
import com.black.feature.usagelog.worker.UsageLogCollectWorker
import com.black.feature.usagelog.worker.UsageLogScheduler

/**
 * 앱 업데이트(MY_PACKAGE_REPLACED) 직후 기기 사용 기록 화면을 열지 않아도 주기 수집 예약을 새 Worker 클래스로 갱신하는 매니페스트 리시버
 *
 * WorkManager DB의 주기 수집 작업은 업데이트 전 Worker 클래스 이름으로 저장돼 있어, 그 클래스의 경로가 바뀌면 실행 때 작업자를 만들지 못하고 FAILED로 끝남.
 * [UsageLogCollectionController.ensureScheduled]로 대기 중인 옛 작업은 같은 id로 바꾸고 끝난 작업은 새로 예약함([UsageLogScheduler.schedulePeriodicCollect]).
 * 예약은 진행 중인 수집(앱 사용 기록, 문자)이나 수집 켜기·끄기가 끝날 때까지 수집 잠금을 기다린 뒤 고치므로 [launchAsync]가 정한 짧은 작업보다 길어질 수 있음.
 * MY_PACKAGE_REPLACED는 `FLAG_RECEIVER_FOREGROUND` 없이 보내는 백그라운드 브로드캐스트라 처리 제한 시간이 60초이고, 수집은 수 초 안에 끝나 기다려도 그 안에 끝남.
 * 예외는 클래스 이름만 로그로 남기고, 다음에 기기 사용 기록 화면에 들어올 때의 예약 복구에 맡김.
 * Hilt 주입 없이 컨트롤러를 직접 만듦([UsageLogCollectWorker]가 저장소를 만드는 방식과 같음).
 */
class UsageLogPackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // exported라 다른 앱의 명시적 브로드캐스트도 받으므로 action 확인
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.w("[UsageLogPackageReplacedReceiver] unexpected action : ${intent.action}")
            return
        }
        val applicationContext = context.applicationContext
        launchAsync {
            // 컨트롤러 생성과 예약 갱신, 실패는 클래스 이름만 로그
            runSuspendCatching { createController(applicationContext).ensureScheduled() }.onFailure {
                Log.w("[UsageLogPackageReplacedReceiver] 주기 수집 예약 갱신 실패: ${it::class.java.simpleName}")
            }
        }
    }

    companion object {
        /** 컨트롤러 생성 함수, 테스트에서 바꿔 넣음 */
        @VisibleForTesting
        internal var createController: (Context) -> UsageLogCollectionController = { context ->
            val settings = UsageLogSettingsRepository(context)
            val repository = UsageLogRepository(context)
            UsageLogCollectionController(
                context,
                settings,
                repository,
                UsageLogScheduler(context),
                SmsCollector(context, settings, repository),
            )
        }
    }
}
