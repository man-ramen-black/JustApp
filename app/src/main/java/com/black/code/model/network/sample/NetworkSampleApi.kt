package com.black.code.model.network.sample

import com.black.code.model.network.HttpResult
import com.black.code.model.network.RetrofitHelper
import com.black.code.model.network.RetrofitHelper.callList
import okhttp3.ResponseBody
import retrofit2.Call

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
object NetworkSampleApi {
    private const val HOST = "https://jsonplaceholder.typicode.com/"

    fun getAlbums(callback: (HttpResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<ResponseBody> {
        return RetrofitHelper.create(HOST, NetworkSampleService::class.java)
            .getAlbums()
            .callList(NetworkSampleAlbum::class.java, callback)
    }

    fun getAlbumsWrongResponse(callback: (HttpResult<List<NetworkSampleAlbum?>>) -> Unit) : Call<ResponseBody> {
        return RetrofitHelper.create("https://www.netmarble.net/", NetworkSampleService::class.java)
            .getAlbums()
            .callList(NetworkSampleAlbum::class.java, callback)
    }

    fun getPhotos(albumId: Int, callback: (HttpResult<List<NetworkSamplePhoto?>>) -> Unit) : Call<ResponseBody> {
        return RetrofitHelper.create(HOST, NetworkSampleService::class.java)
            .getPhotos(albumId)
            .callList(NetworkSamplePhoto::class.java, callback)
    }
}