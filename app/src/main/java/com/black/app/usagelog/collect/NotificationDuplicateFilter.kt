package com.black.app.usagelog.collect

/**
 * 같은 key로 같은 내용이 다시 게시되는 알림(진행률 갱신 등)을 걸러냄
 *
 * 최근 [maxEntries]개 key의 마지막 내용만 기억하고 넘치면 가장 오래 전에 넣은 key부터 잊음.
 * 알림 콜백이 여러 스레드에서 올 수 있어 호출을 직렬화함.
 */
class NotificationDuplicateFilter(private val maxEntries: Int = 500) {
    private val lastContents = object : LinkedHashMap<String, Pair<String?, String?>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String?, String?>>) =
            size > maxEntries
    }

    /** 직전에 같은 key로 같은 제목·본문이 들어왔으면 true, 아니면 내용을 기억하고 false */
    @Synchronized
    fun isDuplicate(key: String, title: String?, text: String?): Boolean {
        val content = title to text
        if (lastContents[key] == content) return true
        lastContents[key] = content
        return false
    }

    /** 알림이 제거되면 그 key의 기억을 지워 같은 내용의 재게시를 중복으로 보지 않음 */
    @Synchronized
    fun forget(key: String) {
        lastContents.remove(key)
    }
}
