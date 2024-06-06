package com.black.app.ui.maintab.main.architecture.mvvm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.black.app.ui.maintab.main.architecture.CounterModel

/**
 * ViewModel은 Model만을 참조함
 * View, Context에 대해 참조하지 않도록 구현 필요
 */
class MVVMViewModel : ViewModel() {

    val count by lazy { MutableLiveData(0) }

    private var model : CounterModel? = null

    /**
     * 버튼 터치 시 동작
     */
    fun onClickAddCount() {
        val model = model ?: return
        count.value = model.addCount()
    }

    /**
     * Model을 설정
     */
    fun setModel(counterModel: CounterModel) {
        this.model = counterModel
    }
}