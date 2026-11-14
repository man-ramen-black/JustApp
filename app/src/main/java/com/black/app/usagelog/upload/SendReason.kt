package com.black.app.usagelog.upload

/** 기록 전송을 시작한 계기 */
enum class SendReason(val value: String) {
    PERIODIC("periodic"),
    HOME_ARRIVE("home_arrive"),
    HOME_LEAVE("home_leave"),
    MANUAL("manual"),
}
