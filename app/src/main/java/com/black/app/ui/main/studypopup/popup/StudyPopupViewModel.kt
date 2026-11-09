package com.black.app.ui.main.studypopup.popup

import androidx.databinding.ObservableField
import com.black.app.databinding.ViewStudyPopupBinding
import com.black.app.model.StudyPopupRepository
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import kotlin.random.Random

/**
 * [StudyPopupView]의 상태·이벤트 관리 ViewModel
 *
 * 레이아웃은 [ViewStudyPopupBinding]
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupViewModel(
    private val repository: StudyPopupRepository,
) : EventViewModel() {

    /** 스터디 팝업 일회성 이벤트 */
    sealed class StudyPopupEvent : ViewModelEvent

    /** 팝업 닫기 */
    data object EventClose : StudyPopupEvent()
    /** 섞은 내용 목록 표시 */
    data class EventShowContents(val contents: List<StudyPopupData.Contents>) : StudyPopupEvent()

    val isProgress = ObservableField(true)
    val currentItem =  ObservableField(0)
    val currentItemSmooth =  ObservableField(0)

    /** 팝업에 보여 줄 내용을 섞어서 표시 */
    fun loadContents() {
        Log.d()
        launch {
            isProgress.set(true)
            val contents = repository.loadPopupContents()
                .shuffled(Random(System.currentTimeMillis()))
            isProgress.set(false)
            sendEvent(EventShowContents(contents))
        }
    }

    fun close() {
        sendEvent(EventClose)
    }

    fun showPrevious() {
        Log.d("${currentItemSmooth.get()}")
        val value = currentItemSmooth.get() ?: return
        currentItemSmooth.set(value - 1)
    }

    fun showNext() {
        Log.d("${currentItemSmooth.get()}")
        val value = currentItemSmooth.get() ?: return
        currentItemSmooth.set(value + 1)
    }
}
