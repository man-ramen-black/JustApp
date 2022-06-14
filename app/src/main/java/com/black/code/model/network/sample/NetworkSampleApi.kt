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
    const val CODE_EMPTY_LIST = -10
    private const val HOST = "https://jsonplaceholder.typicode.com/"

    fun getAlbums(callback: (NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<List<NetworkSampleAlbum?>?> {
        return NetworkHelper.create(HOST, NetworkSampleService::class.java)
            .getAlbums()
            .callList(callback)
    }

    fun getAlbumsWrongResponse(callback: (NetworkResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<List<NetworkSampleAlbum?>?> {
        return NetworkHelper.create("https://www.netmarble.net/", NetworkSampleService::class.java)
            .getAlbums()
            .callList(callback)
    }

    fun getPhotos(albumId: Int, callback: (NetworkResult<List<NetworkSamplePhoto?>>) -> Unit) : Call<List<NetworkSamplePhoto?>?> {
        return NetworkHelper.create(HOST, NetworkSampleService::class.java)
            .getPhotos(albumId)
            .callList {
                it.data?.let { data ->
                    if (it.isSuccess && data.isEmpty()) {
                        callback(NetworkResult(it, CODE_EMPTY_LIST, "List is empty"))
                        return@callList
                    }
                }
                callback(it)
            }
    }
}