package com.black.code.contents.architecture.mvvm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.black.code.contents.architecture.CounterModel

/**
 * ViewModel은 Model만을 참조함
 * View, Context에 대해 참조하지 않도록 구현 필요
 */
class MVVMViewModel : ViewModel() {

    // 캡슐화를 위해 MutableLiveData, LiveData를 함께 정의
    private val _count by lazy { MutableLiveData(0) }
    val count : LiveData<Int>
        get() = _count

    private var model : CounterModel? = null

    /**
     * 버튼 터치 시 동작
     */
    fun onClickAddCount() {
        val model = model ?: return
        _count.value = model.addCount()
    }

    /**
     * Model을 설정
     */
    fun setModel(counterModel: CounterModel) {
        this.model = counterModel
    }
}