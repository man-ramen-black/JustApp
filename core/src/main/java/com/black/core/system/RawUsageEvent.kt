package com.black.core.system

/** 시스템 사용 통계에서 읽은 이벤트 종류 */
enum class RawUsageEventKind { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF, UNLOCK }

/**
 * 시스템 사용 통계 이벤트 한 건
 *
 * @property packageName 앱 이벤트(RESUMED·PAUSED)에서만 쓰는 앱 패키지. 화면·잠금 이벤트에는 시스템이 남긴 값이 그대로 담기거나 null.
 */
data class RawUsageEvent(val kind: RawUsageEventKind, val packageName: String?, val time: Long)
