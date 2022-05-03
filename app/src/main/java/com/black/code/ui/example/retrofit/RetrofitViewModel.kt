package com.black.code.ui.example.retrofit

import androidx.lifecycle.MutableLiveData
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.model.network.sample.NetworkSampleModel

/**
 * Created by jinhyuk.lee on 2022/05/02
 **/
class RetrofitViewModel : EventViewModel() {
    val statusCode = MutableLiveData(0)
    val errorMessage = MutableLiveData("")
    val data = MutableLiveData("")
    val albumId = MutableLiveData(0)

    private var model: NetworkSampleModel? = null

    fun setModel(model: NetworkSampleModel) {
        this.model = model
    }

    private fun <T : List<*>> setResponse(statusCode: Int, errorMessage: String, data: T?) {
        this@RetrofitViewModel.statusCode.value = statusCode
        this@RetrofitViewModel.errorMessage.value = errorMessage
        this@RetrofitViewModel.data.value = data?.joinToString(",\n") ?: "null"
    }

    fun onClickGetAlbums() {
        model?.loadAlbums(this::setResponse)
    }

    fun onClickGetAlbumsWrongResponse() {
        model?.loadAlbumsWrongResponse(this::setResponse)
    }

    fun onClickGetPhoto() {
        model?.loadPhotos(albumId.value ?: 0, this::setResponse)
    }
}