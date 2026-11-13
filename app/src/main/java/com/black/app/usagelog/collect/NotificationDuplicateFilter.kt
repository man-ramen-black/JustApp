package com.black.app.usagelog.collect

/**
 * 같은 key로 같은 내용이 다시 게시되는 알림을 걸러냄
 *
 * 진행률 갱신처럼 내용이 그대로인 재게시를 거르며, 메시지 스타일 알림은 마지막 메시지 시각까지 비교해 같은 문구로 새로 온 메시지는 거르지 않음.
 * 최근 [maxEntries]개 key의 마지막 내용만 기억하고 넘치면 가장 오래 전에 넣은 key부터 잊음.
 * 알림 접근 서비스의 단일 소비 코루틴이 차례로 불러 동기화하지 않음.
 */
class NotificationDuplicateFilter(private val maxEntries: Int = 500) {
    private val lastContents = object : LinkedHashMap<String, Content>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Content>) = size > maxEntries
    }

    /**
     * 직전에 같은 key로 같은 내용이 들어왔으면 true, 아니면 내용을 기억하고 false
     *
     * [messageTime]은 메시지 스타일 알림의 마지막 메시지 시각이고, 그 밖의 알림은 null.
     */
    fun isDuplicate(key: String, title: String?, text: String?, messageTime: Long?): Boolean {
        val content = Content(title, text, messageTime)
        if (lastContents[key] == content) return true
        lastContents[key] = content
        return false
    }

    /** 알림이 제거되거나 기록을 저장하지 못하면 그 key의 기억을 지워 같은 내용의 재게시를 중복으로 보지 않음 */
    fun forget(key: String) {
        lastContents.remove(key)
    }

    private data class Content(val title: String?, val text: String?, val messageTime: Long?)
}
