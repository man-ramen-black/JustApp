package com.black.code.ui.example.studypopup.popup

import androidx.databinding.ObservableField
import androidx.lifecycle.viewModelScope
import com.black.code.base.view.LoopingPagerAdapter
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.databinding.ViewStudyPopupBinding
import com.black.code.model.StudyPopupModel
import com.black.code.model.database.studypopup.StudyPopupData
import com.black.code.util.Log
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * [StudyPopupView]
 * [ViewStudyPopupBinding]
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupViewModel : EventViewModel() {
    companion object {
        const val EVENT_CLOSE = "Close"
        const val EVENT_SUBMIT_LIST = "SubmitList"
        const val EVENT_SET_CURRENT_ITEM = "SetCurrentItem"
        const val EVENT_TOAST = "Toast"
    }

    val isProgress = ObservableField(true)
    val currentItem =  ObservableField(0)
    val currentItemSmooth =  ObservableField(0)
    private val list = ArrayList<StudyPopupData.Contents>()
    private var model: StudyPopupModel? = null

    fun setModel(model: StudyPopupModel) {
        this.model = model
    }

    fun initList() {
        Log.d()
        viewModelScope.launch {
            isProgress.set(true)
            val contentList = model?.load() ?: run {
                Log.w("model is null")
                return@launch
            }
            list.addAll(contentList.shuffled(Random(System.currentTimeMillis())))
            isProgress.set(false)
            sendEvent(EVENT_SUBMIT_LIST, list)
            // submitList 시점과 맞추기 위해 sendEvent로 currentItem 설정
            sendEvent(EVENT_SET_CURRENT_ITEM, LoopingPagerAdapter.INITIAL_POSITION)
        }
    }

    fun onClickClose() {
        sendEvent(EVENT_CLOSE)
    }

    fun onClickPrev() {
        Log.d("${currentItemSmooth.get()}")
        val value = currentItemSmooth.get() ?: return
        currentItemSmooth.set(value - 1)
    }

    fun onClickNext() {
        Log.d("${currentItemSmooth.get()}")
        val value = currentItemSmooth.get() ?: return
        currentItemSmooth.set(value + 1)
    }

    fun onClickOutside() {
        onClickClose()
    }
}