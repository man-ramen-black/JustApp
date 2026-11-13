package com.black.app.usagelog.record

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 기기 사용 기록 한 건
 *
 * DB 저장과 PC 전송의 공통 모델.
 * 하위 클래스의 [SerialName]이 전송 JSON의 type 값이고, 프로퍼티의 [SerialName]이 JSON 키.
 */
@Serializable
sealed interface UsageLogRecord {
    /**
     * 기록 시각(epoch ms)
     *
     * 구간 기록은 시작 시각이며 JSON 키는 t.
     */
    val time: Long
}

/**
 * 앱 사용 구간 기록
 *
 * @property time [start]와 같아야 하며 `copy(start = …)`를 쓸 때 함께 바꿀 것.
 */
@Serializable
@SerialName("app_usage")
data class AppUsageRecord(
    @SerialName("package") val packageName: String,
    val appName: String,
    val start: Long,
    val end: Long,
    @SerialName("t") override val time: Long = start,
) : UsageLogRecord

/** 화면 켜짐 기록 */
@Serializable
@SerialName("screen_on")
data class ScreenOnRecord(@SerialName("t") override val time: Long) : UsageLogRecord

/** 화면 꺼짐 기록 */
@Serializable
@SerialName("screen_off")
data class ScreenOffRecord(@SerialName("t") override val time: Long) : UsageLogRecord

/** 잠금 해제 기록 */
@Serializable
@SerialName("unlock")
data class UnlockRecord(@SerialName("t") override val time: Long) : UsageLogRecord

/** 알림 게시 기록 */
@Serializable
@SerialName("notification")
data class NotificationPostedRecord(
    val key: String,
    @SerialName("package") val packageName: String,
    val appName: String,
    @SerialName("t") override val time: Long,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
) : UsageLogRecord

/** 알림 제거 사유 */
@Serializable
enum class RemovalReason {
    @SerialName("click") CLICK,
    @SerialName("dismiss") DISMISS,
    @SerialName("app_cancel") APP_CANCEL,
    @SerialName("other") OTHER,
}

/** 알림 제거 기록 */
@Serializable
@SerialName("notification_removed")
data class NotificationRemovedRecord(
    val key: String,
    @SerialName("package") val packageName: String,
    @SerialName("t") override val time: Long,
    val reason: RemovalReason,
) : UsageLogRecord

/** Wi-Fi 연결·해제 기록의 공통 필드 */
@Serializable
sealed interface WifiRecord : UsageLogRecord {
    val ssid: String?
    val home: Boolean?
    val inferred: Boolean
}

/** Wi-Fi 연결 기록 */
@Serializable
@SerialName("wifi_connected")
data class WifiConnectedRecord(
    @SerialName("t") override val time: Long,
    override val ssid: String? = null,
    override val home: Boolean? = null,
    override val inferred: Boolean,
) : WifiRecord

/** Wi-Fi 해제 기록 */
@Serializable
@SerialName("wifi_disconnected")
data class WifiDisconnectedRecord(
    @SerialName("t") override val time: Long,
    override val ssid: String? = null,
    override val home: Boolean? = null,
    override val inferred: Boolean,
) : WifiRecord

/** 기록 공백 원인 */
@Serializable
enum class GapCause {
    @SerialName("collection_off") COLLECTION_OFF,
    @SerialName("usage_events_expired") USAGE_EVENTS_EXPIRED,
    @SerialName("unsent_deleted") UNSENT_DELETED,
}

/**
 * 기록이 비어 있는 구간
 *
 * @property time [start]와 같아야 하며 `copy(start = …)`를 쓸 때 함께 바꿀 것.
 */
@Serializable
@SerialName("gap")
data class GapRecord(
    val start: Long,
    val end: Long,
    val cause: GapCause,
    @SerialName("t") override val time: Long = start,
) : UsageLogRecord
