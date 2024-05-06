package com.black.app.model.network.sample

import android.content.Context
import com.black.app.model.network.NetworkResult

/**
 * Created by jinhyuk.lee on 2022/04/14
 **/
class NetworkSampleModel(private val context: Context) {
    fun loadAlbums(callback: (result: NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) {
        NetworkSampleApi.getAlbums(callback)
    }

    fun loadAlbumsWrongResponse(callback: (result: NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) {
        NetworkSampleApi.getAlbumsWrongResponse(callback)
    }

    fun loadPhotos(albumId: Int, callback: (result: NetworkResult<List<NetworkSamplePhoto?>>) -> Unit) {
        NetworkSampleApi.getPhotos(albumId, callback)
    }
}