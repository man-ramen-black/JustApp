package com.black.code.model.network.sample

import android.content.Context

/**
 * Created by jinhyuk.lee on 2022/04/14
 **/
class NetworkSampleModel(private val context: Context) {
    fun loadAlbums(callback: (statusCode: Int, errorMessage: String, albums: List<NetworkSampleAlbum?>?) -> Unit) {
        NetworkSampleApi.getAlbums {
            callback(it.statusCode, it.errorMessage, it.data)
        }
    }

    fun loadAlbumsWrongResponse(callback: (statusCode: Int, errorMessage: String, albums: List<NetworkSampleAlbum?>?) -> Unit) {
        NetworkSampleApi.getAlbumsWrongResponse {
            callback(it.statusCode, it.errorMessage, it.data)
        }
    }

    fun loadPhotos(albumId: Int, callback: (statusCode: Int, errorMessage: String, albums: List<NetworkSamplePhoto?>?) -> Unit) {
        NetworkSampleApi.getPhotos(albumId) {
            callback(it.statusCode, it.errorMessage, it.data)
        }
    }
}