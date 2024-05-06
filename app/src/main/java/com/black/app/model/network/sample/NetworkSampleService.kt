package com.black.app.model.network.sample

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
interface NetworkSampleService {
    @GET("albums")
    fun getAlbums() : Call<List<NetworkSampleAlbum?>?>

    @GET("albums/{albumId}/photos")
    fun getPhotos(@Path("albumId") albumId: Int) : Call<List<NetworkSamplePhoto?>?>
}