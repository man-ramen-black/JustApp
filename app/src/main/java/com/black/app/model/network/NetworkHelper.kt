package com.black.app.model.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Created by jinhyuk.lee on 2022/04/12
 **/
object NetworkHelper {
    fun <T> create(baseUrl: String, service: Class<T>) : T {
        // 로그 출력되도록 설정
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(interceptor)
            .build()

        val gson = Gson().newBuilder()
            .setPrettyPrinting()
            .create()

        return Retrofit.Builder()
            // baseUrl 마지막에 "/"가 누락되는 경우 Exception이 발생하므로 추가해줌
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(service)
    }

    fun <T> Call<T?>.call(callback: (result: NetworkResult<T>) -> Unit) : Call<T?> {
        return callInternal({
            val data : T? = try {
                Gson().fromJson(it ?: return@callInternal null, object : TypeToken<T?>(){}.type)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            data
        }, callback)
    }

    fun <T> Call<List<T?>?>.callList(callback: (result: NetworkResult<List<T?>>) -> Unit) : Call<List<T?>?> {
        return callInternal({
            val list = ArrayList<T?>()
            val arr = try {
                JSONArray(it ?: return@callInternal null)
            } catch (e: JSONException) {
                e.printStackTrace()
                return@callInternal null
            }

            for (i in 0 until arr.length()) {
                val data : T? = try {
                    Gson().fromJson(arr.optString(i, ""), object : TypeToken<T?>(){}.type)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                list.add(data)
            }
            list
        }, callback)
    }

    /**
     * call 공통 로직
     */
    private fun <T> Call<T?>.callInternal(parse:(body: String?) -> T?, callback: (result: NetworkResult<T>) -> Unit) : Call<T?> {
        enqueue(object : Callback<T?> {
            override fun onResponse(call: Call<T?>, response: Response<T?>) {
                // statusCode 200 ~ 300을 벗어난 경우 body가 null이고, errorBody에 데이터가 있음
                val body : T? = response.body() ?: parse(response.errorBody()?.string())

                if (call.isCanceled) {
                    callback(NetworkResult(NetworkResult.CODE_CANCELED, NetworkResult.MESSAGE_CANCELED, null, response.code()))
                    return
                }

                if (body == null) {
                    callback(NetworkResult(NetworkResult.CODE_INVALID_RESPONSE, "Response is null", body, response.code()))
                } else {
                    callback(NetworkResult(NetworkResult.CODE_SUCCESS, NetworkResult.MESSAGE_SUCCESS, body, response.code()))
                }
            }

            override fun onFailure(call: Call<T?>, t: Throwable) {
                t.printStackTrace()

                if (call.isCanceled) {
                    callback(NetworkResult(NetworkResult.CODE_CANCELED, NetworkResult.MESSAGE_CANCELED, null))
                    return
                }

                val errorMessage = t.message ?: "Unknown network error"

                if (t is SocketTimeoutException) {
                    callback(NetworkResult(NetworkResult.CODE_TIMEOUT, errorMessage, null))
                } else {
                    callback(NetworkResult(NetworkResult.CODE_NETWORK_ERROR, errorMessage, null))
                }
            }
        })
        return this
    }
}