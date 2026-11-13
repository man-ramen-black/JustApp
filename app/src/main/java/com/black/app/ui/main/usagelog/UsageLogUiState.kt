package com.black.app.ui.main.usagelog

import com.black.app.usagelog.record.UsageLogRecord
import com.black.app.usagelog.upload.LastSendResult

/** 기기 사용 기록 화면 상태 */
data class UsageLogUiState(
    /** 기록 수집 켜짐 여부 */
    val collectionEnabled: Boolean = false,
    /** 사용 정보 접근 허용 여부 */
    val usageAccessGranted: Boolean = false,
    /** 알림 접근 허용 여부 */
    val notificationAccessGranted: Boolean = false,
    /** 정확한 위치 권한 허용 여부 */
    val fineLocationGranted: Boolean = false,
    /**
     * 백그라운드 위치 권한 허용 여부
     *
     * API 29 미만은 항상 true.
     */
    val backgroundLocationGranted: Boolean = false,
    /** 배터리 최적화 예외 여부 */
    val batteryOptimizationIgnored: Boolean = false,
    /**
     * 등록된 집 Wi-Fi 이름
     *
     * 이름순.
     */
    val homeSsids: List<String> = emptyList(),
    /** 지금 연결된 Wi-Fi 이름. 연결돼 있지 않거나 읽을 수 없으면 null */
    val currentSsid: String? = null,
    /** 저장된 디스코드 웹후크 주소 */
    val webhookUrl: String = "",
    /** 저장된 웹후크 주소가 올바른 형식인지 여부 */
    val webhookUrlValid: Boolean = false,
    /**
     * 전송 암호화 키(Base64)
     *
     * 아직 만들어지지 않았으면 빈 문자열.
     */
    val encryptionKey: String = "",
    /** 마지막 전송 결과. 보낸 적 없으면 null */
    val lastSendResult: LastSendResult? = null,
    /** 아직 보내지 않은 기록 건수 */
    val pendingCount: Int = 0,
    /** 키 새로 만들기 확인 대화상자 표시 여부 */
    val showRegenerateKeyConfirm: Boolean = false,
    /**
     * 최근 기록
     *
     * 최신순.
     */
    val recentRecords: List<UsageLogRecordItem> = emptyList(),
)

/**
 * 최근 기록 한 줄의 화면용 항목
 *
 * @property id 기록 행 id(목록 key)
 * @property appLabel 알림 제거 기록이 가리키는 앱 이름. 다른 기록은 null
 */
data class UsageLogRecordItem(
    val id: Long,
    val record: UsageLogRecord,
    val appLabel: String? = null,
)
