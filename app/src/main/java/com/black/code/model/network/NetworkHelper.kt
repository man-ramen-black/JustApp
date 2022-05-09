package com.black.code.model.network

import com.black.code.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
object NetworkHelper {
    fun <T> create(baseUrl: String, service: Class<T>) : T {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(service)
    }

    /**
     *  Json String을 JSONArray로 반환
     */
    fun Call<ResponseBody>.callJSONArray(callback: (result: NetworkResult<JSONArray>) -> Unit) : Call<ResponseBody> {
        return callString({
            try {
                JSONArray(it)
            } catch (e: JSONException) {
                e.printStackTrace()
                null
            }
        }, callback)
    }

    /**
     *  Json String을 JSONObject로 반환
     */
    fun Call<ResponseBody>.callJSONObject(callback: (result: NetworkResult<JSONObject>) -> Unit) : Call<ResponseBody> {
        return callString({
            try {
                JSONObject(it)
            } catch (e: JSONException) {
                e.printStackTrace()
                null
            }
        }, callback)
    }

    /**
     *  Json String List<resultCls>로 역직렬화하여 반환
     */
    fun <T> Call<ResponseBody>.callList(resultCls: Class<T>, callback: (result: NetworkResult<List<T?>>) -> Unit) : Call<ResponseBody> {
        return callString({
            val jsonArr = try {
                JSONArray(it)
            } catch (e: JSONException) {
                e.printStackTrace()
                return@callString null
            }

            val gson = Gson()
            val list = ArrayList<T?>()
            for (i in 0 until (jsonArr?.length() ?: 0)) {
                try {
                    list.add(gson.fromJson(jsonArr?.optString(i) ?: "", resultCls))
                } catch (e: JsonParseException) {
                    e.printStackTrace()
                }
            }
            list
        }, callback)
    }

    /**
     *  Json String을 resultCls로 역직렬화하여 반환
     */
    fun <T> Call<ResponseBody>.call(resultCls: Class<T>, callback: (result: NetworkResult<T>) -> Unit) : Call<ResponseBody> {
        return callString({
            try {
                Gson().fromJson(it, resultCls)
            } catch (e: JsonParseException) {
                e.printStackTrace()
                null
            }
        }, callback)
    }

    /**
     * Response String 반환
     */
    fun Call<ResponseBody>.call(callback: (result: NetworkResult<String>) -> Unit) : Call<ResponseBody> {
        return callString({ it }, callback)
    }

    /**
     * Response String을 T로 변환하여 반환
     */
    private fun <T> Call<ResponseBody>.callString(parse: (String) -> T?, callback: (result: NetworkResult<T>) -> Unit) : Call<ResponseBody> {
        return callInternal({ responseBody ->
            responseBody?.string()
                ?.let { it to parse(it) }
                ?: "" to null
        }, callback)
    }

    /**
     * call 공통 로직
     * @param getRawAndData ResponseBody를 raw String과 data로 변환하여 반환
     */
    private fun <T> Call<ResponseBody>.callInternal(getRawAndData: (ResponseBody?) -> Pair<String, T?>, callback: (result: NetworkResult<T>) -> Unit) : Call<ResponseBody> {
        Log.i("HTTP Start : [${request().method()}] ${request().url()}")
        enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                // statusCode 200 ~ 300을 벗어난 경우 body가 null이고, errorBody에 데이터가 있음
                val rawBody = try {
                    response.body()
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        response.errorBody()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }

                if (call.isCanceled) {
                    Log.w("HTTP Canceled : [${request().method()}] ${request().url()}\n[${response.code()}]")
                    callback(NetworkResult(null, response.code(), "Canceled", true))
                    return
                }

                val result = getRawAndData(rawBody)
                val raw = result.first
                val data = result.second

                if (data == null) {
                    Log.w("HTTP Failure : [${request().method()}] ${request().url()}\n[${response.code()}\n$raw]")
                } else {
                    Log.i("HTTP Success : [${request().method()}] ${request().url()}\n[${response.code()}]\n$raw")
                }
                callback(NetworkResult(data, response.code()))
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()

                val errorMessage = t.message ?: "Unknown network error"
                Log.w("HTTP Failure : [${request().method()}] ${request().url()}\n[${NetworkResult.STATUS_NETWORK_ERROR}]\n$errorMessage")

                callback(NetworkResult(null, NetworkResult.STATUS_NETWORK_ERROR, errorMessage, call.isCanceled))
            }
        })
        return this
    }
}