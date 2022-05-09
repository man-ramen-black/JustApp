package com.black.code.model.network.sample

import com.black.code.model.network.NetworkResult
import com.black.code.model.network.NetworkHelper
import com.black.code.model.network.NetworkHelper.callList
import okhttp3.ResponseBody
import retrofit2.Call

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
object NetworkSampleApi {
    private const val HOST = "https://jsonplaceholder.typicode.com/"

    fun getAlbums(callback: (NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<ResponseBody> {
        return NetworkHelper.create(HOST, NetworkSampleService::class.java)
            .getAlbums()
            .callList(NetworkSampleAlbum::class.java, callback)
    }

    fun getAlbumsWrongResponse(callback: (NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<ResponseBody> {
        return NetworkHelper.create("https://www.netmarble.net/", NetworkSampleService::class.java)
            .getAlbums()
            .callList(NetworkSampleAlbum::class.java, callback)
    }

    fun getPhotos(albumId: Int, callback: (NetworkResult<List<NetworkSamplePhoto?>>) -> Unit) : Call<ResponseBody> {
        return NetworkHelper.create(HOST, NetworkSampleService::class.java)
            .getPhotos(albumId)
            .callList(NetworkSamplePhoto::class.java, callback)
    }
}