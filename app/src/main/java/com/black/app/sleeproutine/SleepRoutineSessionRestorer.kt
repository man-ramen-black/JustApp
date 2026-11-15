package com.black.app.sleeproutine

import android.content.Context
import com.black.app.model.SleepRoutineRepository
import com.black.core.util.Log

/**
 * 앱 업데이트·재부팅 뒤 진행 중이던 수면 루틴 세션 재개
 *
 * 두 시점은 프로세스가 함께 종료되고 START_STICKY 재생성도 없어, 그대로 두면 세션과 방해 금지가 켜진 채 남음.
 * 세션이 없으면 서비스를 시작하지 않아 업데이트마다 진행 알림이 잠깐 떴다 사라지지 않음
 */
class SleepRoutineSessionRestorer(context: Context) {
    private val appContext = context.applicationContext
    private val repository = SleepRoutineRepository(appContext)

    /**
     * 저장된 세션이 있으면 [SleepRoutineService]를 재개 모드로 시작
     *
     * 남은 시간은 서비스가 기다리고, 종료 시각이 지났으면 바로 마무리.
     *
     * @return 시작 요청 여부
     */
    suspend fun restore(): Boolean {
        if (repository.getSessionEndTime() == null) return false
        return try {
            appContext.startService(SleepRoutineService.createResumeIntent(appContext))
            true
        } catch (exception: IllegalStateException) {
            // 부팅·업데이트 수신은 백그라운드 시작 허용 대상이지만, 거부돼도 리시버의 다른 시작 처리는 이어 가도록 기록만 함
            Log.w(exception)
            false
        }
    }
}
