package com.black.app.model.network

/**
 * Created by jinhyuk.lee on 2022/04/13
 **/
class NetworkResult<T> constructor(val code: Int,
                                   val message: String,
                                   val data: T? = null,
                                   val statusCode: Int = 0) {
    companion object {
        // 자주 사용되는 Code는 아래에 추가하여 사용
        // 특정 API에서만 사용되는 Code는 100 이상으로 해당 클래스에 별도로 정의하여 사용
        // 100 이하로 정의
        const val CODE_SUCCESS             = 0
        const val CODE_CANCELED            = -1
        const val CODE_TIMEOUT             = -2
        const val CODE_NETWORK_ERROR       = -3
        const val CODE_INVALID_RESPONSE    = -4

        const val MESSAGE_SUCCESS = "Success"
        const val MESSAGE_CANCELED = "Canceled"
    }

    constructor(result: NetworkResult<T>, code: Int, message: String)
            : this(code, message, result.data, result.statusCode)

    val isSuccess = code >= 0

    override fun toString(): String {
        return "Result{\n" +
                "Code=$code, \n" +
                "Message=$message, \n" +
                "StatusCode=$statusCode\n" +
                "Data=$data" +
                "}"
    }
}