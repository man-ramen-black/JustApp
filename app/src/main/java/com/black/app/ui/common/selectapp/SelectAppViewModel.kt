package com.black.app.ui.common.selectapp

import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [SelectAppDialogFragment] */
@HiltViewModel
class SelectAppViewModel @Inject constructor() : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_APP_SELECTED = "AppSelected" // Data : List<packageName : String>
        const val EVENT_CLOSE = "Close"
    }

    var itemListOrigin : List<SelectAppItem> = listOf()
    val itemList = MutableLiveData<List<SelectAppItem>>(listOf())
    val isProgress = MutableLiveData(true)

    /** 회전 등으로 뷰가 다시 만들어져도 바꾼 체크를 유지하도록 첫 초기화 여부 기록(Main에서만 읽고 씀) */
    var isInitialized = false
        private set

    /** 첫 호출에서만 목록과 초기 선택을 반영하고 이후 호출은 무시 */
    fun init(list: List<SelectAppItem>, checkedPackageNames: List<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.Main) {
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

    fun onItemClick(item: SelectAppItem) {
        item.isChecked.value = !item.isChecked.value!!
    }

    fun onClickClose() {
        sendEvent(EVENT_CLOSE)
    }

    fun onClickComplete() {
        // 필터로 가려진 선택까지 포함하도록 현재 표시 목록이 아닌 전체 원본에서 선택 항목 수집
        sendEvent(EVENT_APP_SELECTED, itemListOrigin.filter { it.isChecked.value!! }.map { it.packageName })
        sendEvent(EVENT_CLOSE)
    }

    fun onTextChangedFilter(text: Editable) {
        if (text.isEmpty()) {
            itemList.value = itemListOrigin
            return
        }
        itemList.value = itemListOrigin.filter { it.appLabel.contains(text, ignoreCase = true) }
    }
}