package com.black.code.model.network

/**
 * Created by jinhyuk.lee on 2022/04/13
 **/
class NetworkResult<T>(val data: T?, val statusCode: Int, private val errorMessage: String = "", isCanceled: Boolean = false) {
    companion object {
        const val STATUS_NETWORK_ERROR = -999

        const val CODE_SUCCESS = 0
        const val CODE_CANCELED = -1
        const val CODE_NETWORK_ERROR = -2
        const val CODE_SERVER_FAILED = -3
    }

    val code = when {
        data != null -> {
            CODE_SUCCESS
        }
        isCanceled -> {
            CODE_CANCELED
        }
        statusCode == STATUS_NETWORK_ERROR -> {
            CODE_NETWORK_ERROR
        }
        else -> {
            CODE_SERVER_FAILED
        }
    }

    val isSuccess
        get() = code == CODE_SUCCESS

    val message
        get() = when {
            errorMessage.isNotEmpty() -> errorMessage

            isSuccess -> "Success"

            else -> "Server failure"
        }

    override fun toString(): String {
        return "NetworkResult{\n" +
                "Code=$code, \n" +
                "Message=$message, \n" +
                "StatusCode=$statusCode, \n" +
                "Data=$data\n" +
                "}"
    }
}