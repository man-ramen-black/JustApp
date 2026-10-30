package com.black.app.service

import android.content.Context
import android.content.Intent
import com.black.app.model.datastore.ForegroundServiceDataStore
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [ForegroundService] 실행 설정 저장과 시작·중지의 단일 창구
 *
 * 앱 시작·부팅·앱 업데이트·서비스 재시작·설정 화면이 모두 이 클래스를 거쳐 실행 설정과 실행 상태를 맞춤.
 * 상태는 DataStore와 [ForegroundService.running]이 가지므로 Hilt 주입이 없는 리시버·서비스는 직접 생성해 사용.
 * 설정 화면 이동 같은 UI 동작은 하지 않고 [StartResult]만 반환(백그라운드 호출부에서 화면을 띄우지 않도록)
 */
class ForegroundServiceController @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** 시작 요청 결과 */
    enum class StartResult {
        /** 시작 요청 완료(이미 실행 중이면 무해한 재요청) */
        Started,
        /** 실행 설정 꺼짐 */
        NotActivated,
        /** 배터리 최적화 예외 없음(백그라운드 시작 불가) */
        BatteryOptimizationRequired,
    }

    private val appContext = context.applicationContext
    private val dataStore = ForegroundServiceDataStore(appContext)

    /** 실행 설정 */
    val activated: Flow<Boolean> = dataStore.getActivatedFlow()

    /** 서비스 실행 여부 */
    val running: StateFlow<Boolean> = ForegroundService.running

    fun isBatteryOptimizationIgnored(): Boolean = PermissionHelper.isIgnoringBatteryOptimizations(appContext)

    /** 실행 설정 저장 후 서비스 시작 */
    suspend fun activate(): StartResult {
        dataStore.updateActivated(true)
        return startIfActivated()
    }

    /** 실행 설정 저장 후 서비스 중지 */
    suspend fun deactivate() {
        // onDestroy의 재시작 확인이 꺼진 설정을 읽도록 중지 전에 저장 완료
        dataStore.updateActivated(false)
        // stopService는 멈춘 서비스를 생성하지 않고 대기 중인 시작 요청도 취소
        appContext.stopService(Intent(appContext, ForegroundService::class.java))
    }

    /** 실행 설정이 켜져 있고 배터리 최적화 예외일 때만 서비스 시작 */
    suspend fun startIfActivated(): StartResult {
        if (!dataStore.getActivated()) {
            Log.d("ForegroundService not activated")
            return StartResult.NotActivated
        }
        if (!isBatteryOptimizationIgnored()) {
            Log.d("BatteryOptimizations not ignored")
            return StartResult.BatteryOptimizationRequired
        }
        appContext.startService(Intent(appContext, ForegroundService::class.java))
        return StartResult.Started
    }
}
