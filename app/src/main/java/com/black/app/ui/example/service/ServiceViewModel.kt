package com.black.app.ui.example.service

import androidx.lifecycle.MutableLiveData
import com.black.app.base.viewmodel.EventViewModel
import com.black.app.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
class ServiceViewModel : EventViewModel() {
    private var model: ServiceModel? = null
    var foregroundServiceChecked = MutableLiveData(false)

    fun setModel(model: ServiceModel) {
        this.model = model
    }

    fun init() {
        foregroundServiceChecked.value = model?.getForegroundServiceActivated()
    }

    fun onCheckedChangedForegroundService(checked: Boolean) {
        Log.d("checked : $checked")
        if (checked) {
            val result = model?.startForegroundService()
            foregroundServiceChecked.postValue(result)
        } else {
            model?.stopForegroundService()
        }
    }
}