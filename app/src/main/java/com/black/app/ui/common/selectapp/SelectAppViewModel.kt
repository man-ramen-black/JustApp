package com.black.app.ui.common.selectapp

import android.text.Editable
import androidx.lifecycle.MutableLiveData
import com.black.core.viewmodel.EventViewModel
import com.black.core.viewmodel.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** [SelectAppDialogFragment]의 상태·이벤트 관리 ViewModel */
@HiltViewModel
class SelectAppViewModel @Inject constructor() : EventViewModel() {

    /** 앱 선택 다이얼로그 일회성 이벤트 */
    sealed class SelectAppEvent : ViewModelEvent

    /** 선택 완료(선택한 앱 packageName 목록 전달) */
    data class EventAppSelected(val packageNames: List<String>) : SelectAppEvent()
    /** 다이얼로그 닫기 */
    data object EventClose : SelectAppEvent()

    var itemListOrigin : List<SelectAppItem> = listOf()
    val itemList = MutableLiveData<List<SelectAppItem>>(listOf())
    val isProgress = MutableLiveData(true)

    /** 회전 등으로 뷰가 다시 만들어져도 바꾼 체크를 유지하도록 첫 초기화 여부 기록(Main에서만 읽고 씀) */
    var isInitialized = false
        private set

    /** 첫 호출에서만 목록과 초기 선택을 반영하고 이후 호출은 무시 */
    fun init(list: List<SelectAppItem>, checkedPackageNames: List<String> = emptyList()) {
        launch {
            if (isInitialized) {
                return@launch
            }
            isInitialized = true
            isProgress.value = false
            val checkedSet = checkedPackageNames.toSet()
            list.forEach { it.isChecked.value = checkedSet.contains(it.packageName) }
            // 이미 선택된 앱을 앞에 두고 각 묶음은 앱 이름순. 팝업을 연 시점 기준이라 체크를 바꿔도 재정렬하지 않음
            itemListOrigin = list.sortedWith(
                compareByDescending<SelectAppItem> { checkedSet.contains(it.packageName) }
                    .thenBy { it.appLabel.toString() }
            )
            itemList.value = itemListOrigin
        }
    }

    fun toggleChecked(item: SelectAppItem) {
        item.isChecked.value = !item.isChecked.value!!
    }

    fun close() {
        sendEvent(EventClose)
    }

    fun completeSelection() {
        // 필터로 가려진 선택까지 포함하도록 현재 표시 목록이 아닌 전체 원본에서 선택 항목 수집
        sendEvent(EventAppSelected(itemListOrigin.filter { it.isChecked.value!! }.map { it.packageName }))
        sendEvent(EventClose)
    }

    fun filterApps(text: Editable) {
        if (text.isEmpty()) {
            itemList.value = itemListOrigin
            return
        }
        itemList.value = itemListOrigin.filter { it.appLabel.contains(text, ignoreCase = true) }
    }
}