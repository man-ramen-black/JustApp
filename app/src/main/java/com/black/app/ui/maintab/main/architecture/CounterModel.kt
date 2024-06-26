package com.black.app.ui.maintab.main.architecture

/**
 * Model은 데이터, 상태, 비즈니스 로직을 관리
 */
class CounterModel {
    // Count 데이터
    var count = 0
        private set

    /**
     * Count를 증가시킨 후 반환
     */
    fun addCount(addCount: Int = 1) : Int {
        count += addCount
        return count
    }
}