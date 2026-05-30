package com.black.app.ui.common.selectapp

import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.black.core.viewmodel.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [SelectAppDialogFragment]
 */
@HiltViewModel
class SelectAppViewModel @Inject constructor() : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_APP_SELECTED = "AppSelected" // Data : List<packageName : String>
        const val EVENT_CLOSE = "Close"
    }

    var itemListOrigin : List<SelectAppItem> = listOf()
    val itemList = MutableLiveData<List<SelectAppItem>>(listOf())
    val isProgress = MutableLiveData(true)

    fun init(list: List<SelectAppItem>, checkedPackageNames: List<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.Main) {
            isProgress.value = false
            val checkedSet = checkedPackageNames.toSet()
            list.forEach { it.isChecked.value = checkedSet.contains(it.packageName) }
            itemListOrigin = list.sortedBy { it.appLabel.toString() }
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