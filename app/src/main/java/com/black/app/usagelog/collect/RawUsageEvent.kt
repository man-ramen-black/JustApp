package com.black.app.usagelog.collect

/** 시스템 사용 기록에서 읽은 이벤트 종류 */
enum class RawUsageEventKind { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF, UNLOCK }

/**
 * 시스템 사용 기록 이벤트 한 건
 *
 * @property packageName 앱 이벤트(RESUMED·PAUSED)에서만 값이 있음.
 */
data class RawUsageEvent(val kind: RawUsageEventKind, val packageName: String?, val time: Long)
