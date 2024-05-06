package com.black.app.model.network.sample

/**
 * Created by jinhyuk.lee on 2022/05/01
 **/
data class NetworkSampleAlbum (
    val id: Int,
    val title: String
    )

data class NetworkSamplePhoto (
    val albumId: Int,
    val id: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String
    )