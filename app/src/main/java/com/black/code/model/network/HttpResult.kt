package com.black.code.model.network

/**
 * Created by jinhyuk.lee on 2022/04/13
 **/
data class HttpResult<T>(val data: T?, val statusCode: Int, private val failedMessage: String = "", val isCanceled: Boolean = false) {
    companion object {
        const val STATUS_NETWORK_ERROR = -999
    }

    val isSuccess
        get() = data != null

    val isNetworkError
        get() = statusCode == STATUS_NETWORK_ERROR

    val errorMessage
        get() = when {
            isNetworkError -> failedMessage

            isSuccess -> "Success"

            else -> "Server failure"
        }
}