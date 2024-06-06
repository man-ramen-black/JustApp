package com.black.app.ui.maintab.main.recyclerview

/**
 * Created by jinhyuk.lee on 2022/05/10
 **/
sealed class RecyclerViewData {
    data class Toast(val toast: String) : RecyclerViewData()
    data class Dialog(val message: String) : RecyclerViewData()
}