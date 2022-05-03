package com.black.code.model.network.sample

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
interface NetworkSampleService {
    @GET("albums")
    fun getAlbums() : Call<ResponseBody>

    @GET("albums/{albumId}/photos")
    fun getPhotos(@Path("albumId") albumId: Int) : Call<ResponseBody>
}