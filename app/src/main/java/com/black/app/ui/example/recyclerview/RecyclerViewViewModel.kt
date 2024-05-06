package com.black.app.ui.example.recyclerview

import com.black.core.util.Util
import kotlin.random.Random

/**
 * Created by jinhyuk.lee on 2022/05/10
 * android recyclerview livedata
 * https://gwynn.tistory.com/59
 **/
class RecyclerViewViewModel : com.black.core.viewmodel.EventViewModel() {
    companion object {
        const val EVENT_SHOW_DIALOG = "ShowDialog"
        const val EVENT_SHOW_TOAST = "ShowToast"
        const val EVENT_UPDATE_RECYCLER_VIEW = "UpdateRecyclerView"
    }

    private val list = ArrayList<RecyclerViewData>()

    init {
        initList()
    }

    private fun initList() {
        list.add(RecyclerViewData.Dialog("dialogogogoog"))
        list.add(RecyclerViewData.Toast("totoaooaososos"))
        list.add(RecyclerViewData.Toast("yapyapyap"))
        list.add(RecyclerViewData.Dialog("gogogogo"))
        updateRecyclerView()
    }

    fun onClickAdd() {
        val randomString = Util.generateRandomString(10)
        val data = if (Random(System.currentTimeMillis()).nextInt() % 2 == 0) {
            RecyclerViewData.Dialog(randomString)
        } else {
            RecyclerViewData.Toast(randomString)
        }
        list.add(data)
        updateRecyclerView()
    }

    fun onItemClickDialog(data: RecyclerViewData.Dialog) {
        sendEvent(EVENT_SHOW_DIALOG, data.message)
    }

    fun onItemClickToast(data: RecyclerViewData.Toast) {
        sendEvent(EVENT_SHOW_TOAST, data.toast)
    }

    fun onItemClickChange(index: Int) {
        val newItem = when (val item = list[index]) {
            is RecyclerViewData.Dialog -> {
                RecyclerViewData.Toast(item.message)
            }
            is RecyclerViewData.Toast -> {
                RecyclerViewData.Dialog(item.toast)
            }
        }

        list.removeAt(index)
        list.add(index, newItem)
        updateRecyclerView()
    }

    fun onItemClickDelete(index: Int) {
        list.removeAt(index)
        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        sendEvent(EVENT_UPDATE_RECYCLER_VIEW, list)
    }
}