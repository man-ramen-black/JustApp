package com.black.core.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱의 상시 포그라운드 서비스 실행 설정 저장과 시작·중지 창구
 *
 * 서비스와 실행 설정 저장소는 앱에 있고, 그 서비스에 런타임 리시버를 맡긴 기능 모듈(앱 설치 알림, 스터디 팝업)은
 * 앱 클래스를 참조할 수 없어 이 인터페이스로만 실행 설정과 실행 상태를 다룸. 구현은 앱이 Hilt로 연결.
 * 설정 화면 이동 같은 UI 동작은 하지 않고 [StartResult]만 반환(백그라운드 호출부에서 화면을 띄우지 않도록)
 */
interface ForegroundServiceControl {
    /** 시작 요청 결과 */
    enum class StartResult {
        /** 시작 요청 완료(이미 실행 중이면 무해한 재요청) */
        Started,
        /** 실행 설정 꺼짐 */
        NotActivated,
        /** 배터리 최적화 예외 없음(백그라운드 시작 불가) */
        BatteryOptimizationRequired,
        /** 실행 설정 저장 실패 */
        SaveFailed,
    }

    /** 실행 설정 */
    val activated: Flow<Boolean>

    /** 서비스 실행 여부 */
    val running: StateFlow<Boolean>

    fun isBatteryOptimizationIgnored(): Boolean

    /** 실행 설정 저장 후 서비스 시작 */
    suspend fun activate(): StartResult

    /**
     * 실행 설정 저장 후 서비스 중지
     *
     * 저장에 실패하면 서비스를 멈추지 않고 실패 반환
     */
    suspend fun deactivate(): Result<Unit>

    /** 실행 설정이 켜져 있고 배터리 최적화 예외일 때만 서비스 시작 */
    suspend fun startIfActivated(): StartResult
}
