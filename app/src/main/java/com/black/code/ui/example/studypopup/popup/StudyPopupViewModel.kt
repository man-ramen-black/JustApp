package com.black.code.ui.example.studypopup.popup

import androidx.databinding.ObservableField
import com.black.code.base.view.LoopingPagerAdapter
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.ui.example.studypopup.StudyPopupData
import com.black.code.util.Log

/**
 * [StudyPopupView]
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupViewModel : EventViewModel() {
    companion object {
        const val EVENT_CLOSE = "Close"
        const val EVENT_SUBMIT_LIST = "SubmitList"
    }

    val currentItem =  ObservableField(LoopingPagerAdapter.INITIAL_POSITION)
    private val list = ArrayList<StudyPopupData.Contents>()

    fun initList() {
        list.add(StudyPopupData.Contents("Test1"))
        list.add(StudyPopupData.Contents("Test2"))
        submitList()
    }

    fun onClickClose() {
        sendEvent(EVENT_CLOSE)
    }

    fun onClickPrev() {
        Log.d("${currentItem.get()}")
        val value = currentItem.get() ?: return
        currentItem.set(value - 1)
    }

    fun onClickNext() {
        Log.d("${currentItem.get()}")
        val value = currentItem.get() ?: return
        currentItem.set(value + 1)
    }

    private fun submitList() {
        sendEvent(EVENT_SUBMIT_LIST, list)
    }
}