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
    private val _count by lazy { MutableLiveData(0) }
    val count : LiveData<Int>
        get() = _count

    private var model : CounterModel? = null

    /**
     * Model을 설정
     */
    fun setModel(counterModel: CounterModel) {
        this.model = counterModel
    }

    /**
     * Model을 통해서 초기 상태(count = 0) 적용
     */
    fun init() {
        model?.let {
            _count.value = it.count
        }
    }

    /**
     * 버튼 터치 시 동작
     */
    fun onClickAddCount() {
        model?.let {
            _count.value = it.addCount()
        }
    }
}