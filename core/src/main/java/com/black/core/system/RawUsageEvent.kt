package com.black.core.system

/** 시스템 사용 통계에서 읽은 이벤트 종류 */
enum class RawUsageEventKind { RESUMED, PAUSED, STOPPED, SCREEN_ON, SCREEN_OFF, UNLOCK }

/**
 * 시스템 사용 통계 이벤트 한 건
 *
 * @property packageName 앱 이벤트(RESUMED·PAUSED·STOPPED)에서만 쓰는 앱 패키지. 화면·잠금 이벤트에는 시스템이 남긴 값이 그대로 담기거나 null.
 * @property className 앱 이벤트의 액티비티 클래스 이름. 액티비티 인스턴스 ID가 공개 API가 아니라 같은 앱의 액티비티를 이 값으로 구분하며,
 * 화면·잠금 이벤트에는 null.
 */
data class RawUsageEvent(
    val kind: RawUsageEventKind,
    val packageName: String?,
    val time: Long,
    val className: String? = null,
)
