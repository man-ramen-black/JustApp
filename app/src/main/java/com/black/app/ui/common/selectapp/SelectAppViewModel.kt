package com.black.app.ui.common.selectapp

import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.black.core.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [SelectAppDialogFragment]
 */
class SelectAppViewModel : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_APP_SELECTED = "AppSelected" // Data : List<packageName : String>
        const val EVENT_CLOSE = "Close"
    }

    var itemListOrigin : List<SelectAppItem> = listOf()
    val itemList = MutableLiveData<List<SelectAppItem>>(listOf())
    val isProgress = MutableLiveData(true)

    fun init(list: List<SelectAppItem>) {
        viewModelScope.launch(Dispatchers.Main) {
            isProgress.value = false
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
        sendEvent(EVENT_APP_SELECTED, itemList.value!!.filter { it.isChecked.value!! }.map { it.packageName })
        sendEvent(EVENT_CLOSE)
    }

    fun onTextChangedFilter(text: Editable) {
        if (text.isEmpty()) {
            itemList.value = itemListOrigin
            return
        }
        itemList.value = itemListOrigin.filter { it.appLabel.contains(text) }
    }
}