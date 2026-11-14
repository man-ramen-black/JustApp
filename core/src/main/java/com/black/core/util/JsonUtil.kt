package com.black.core.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.Type

/**
 * Json 처리 유틸
 **/
object JsonUtil {
    /**
     * Kotlin Serialization 객체
     * https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#json-configuration
     */
    @OptIn(ExperimentalSerializationApi::class)
    val kotlinSerialization : Json by lazy {
        Json {
            // ""이 빠진 값도 String으로 인식하는 등 구문 분석을 너프하게 하도록 설정
            isLenient = true
            // 클래스와 매치되지 않는 key 무시
            ignoreUnknownKeys = true
            // key 값이 없는 경우 변수에 null로 입력되도록 설정
            explicitNulls = false
            // json string 파싱 시 디폴트 값도 반영
            encodeDefaults = true
            // 데이터로 파싱 시 non-null 변수에 null을 설정하려고 하는 경우 default value로 대체
            // 참고 : json value가 타입에 맞지 않는 경우 등엔 동작하지 않음(Exception 발생)
            coerceInputValues = true
            prettyPrint = true
        }
    }

    /**
     * 한 줄 출력용 Kotlin Serialization 객체
     *
     * [kotlinSerialization]에서 줄바꿈 없는 출력([Json.prettyPrint] false)과
     * null 필드를 `null`로 기록([Json.explicitNulls] true)만 바꿈. JSON Lines 한 줄 기록에 사용.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val kotlinSerializationCompact : Json by lazy {
        Json(from = kotlinSerialization) {
            prettyPrint = false
            explicitNulls = true
        }
    }

    /** Gson 객체 */
    val gson : Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    /** get data from Json */
    fun <T> from(json: String, cls: Class<T>, isKotlinSerialize: Boolean = false): T? {
        return if (isKotlinSerialize) {
            tryForKS {
                fromNotNull(json, cls, true)
            }.getOrNull()
        } else {
            tryForGson {
                fromNotNull(json, cls, false)
            }.getOrNull()
        }
    }

    fun <T> fromNotNull(json: String, cls: Class<T>, isKotlinSerialize: Boolean = false): T {
        return if (isKotlinSerialize) {
            @Suppress("UNCHECKED_CAST")
            val serializer = (kotlinSerialization.serializersModule.serializer(cls) as KSerializer<T>)
            kotlinSerialization.decodeFromString(serializer, json)
        } else {
                gson.fromJson(json, cls)
        }
    }

    fun <T> from(json: String, type: Type, isKotlinSerialize: Boolean = false): T? {
        return if (isKotlinSerialize) {
            tryForKS<T> {
                fromNotNull(json, type, true)
            }.getOrNull()
        } else {
            tryForGson<T> {
                fromNotNull(json, type, false)
            }.getOrNull()
        }
    }

    fun <T> fromNotNull(json: String, type: Type, isKotlinSerialize: Boolean = false): T {
        return if (isKotlinSerialize) {
            @Suppress("UNCHECKED_CAST")
            val serializer = (kotlinSerialization.serializersModule.serializer(type) as KSerializer<T>)
            kotlinSerialization.decodeFromString(serializer, json)
        } else {
            gson.fromJson(json, type)
        }
    }

    /** get data from Json */
    inline fun <reified T> from(
        json: String,
        isKotlinSerialize: Boolean = false,
        kotlinJson: Json = kotlinSerialization,
        logInput: Boolean = true,
    ): T? {
        return if (isKotlinSerialize) {
            tryForKS<T>(logInput) {
                kotlinJson.decodeFromString(json)
            }.getOrNull()
        } else {
            tryForGson<T>(logInput) {
                gson.fromJson(json, object: TypeToken<T>(){}.type)
            }.getOrNull()
        }
    }

    /** parse data to JsonString */
    fun <T> to(data: T, type: Type, isKotlinSerialize: Boolean = false): String? {
        return if (isKotlinSerialize) {
            @Suppress("UNCHECKED_CAST")
            tryForKS {
                val serializer = (kotlinSerialization.serializersModule.serializer(type) as? KSerializer<T>)
                    ?: return@tryForKS null
                kotlinSerialization.encodeToString(serializer, data)
            }.getOrNull()
        } else {
            tryForGson {
                gson.toJson(data, type)
            }.getOrNull()
        }
    }

    /** parse data to JsonString */
    inline fun <reified T> to(
        data: T,
        isKotlinSerialize: Boolean = false,
        kotlinJson: Json = kotlinSerialization,
    ): String? {
        return if (isKotlinSerialize) {
            tryForKS {
                kotlinJson.encodeToString(data)
            }.getOrNull()
        } else {
            tryForGson {
                gson.toJson(data)
            }.getOrNull()
        }
    }

    /**
     * Gson 파싱 실패를 [Result]로 변환
     *
     * [logInput]이 false면 예외 메시지에 입력 JSON이 들어 있을 수 있어 스택 트레이스를 출력하지 않음.
     */
    fun <T> tryForGson(logInput: Boolean = true, block: () -> T?): Result<T?> {
        return try {
            return Result.success(block())
        } catch (e: JsonParseException) {
            reportFailure(e, logInput)
            e
        } catch (e: IllegalArgumentException) {
            reportFailure(e, logInput)
            e
        } catch (e: ClassCastException) {
            reportFailure(e, logInput)
            e
        }.let { Result.failure(JsonUtilParseException(it)) }
    }

    /**
     * Kotlin Serialization 파싱 실패를 [Result]로 변환
     *
     * [logInput]이 false면 예외 메시지에 입력 JSON이 들어 있을 수 있어 스택 트레이스를 출력하지 않음.
     */
    fun <T> tryForKS(logInput: Boolean = true, block: () -> T?): Result<T?> {
        return try {
            return Result.success(block())
        } catch (e: SerializationException) {
            reportFailure(e, logInput)
            e
        } catch (e: IllegalArgumentException) {
            reportFailure(e, logInput)
            e
        } catch (e: ClassCastException) {
            reportFailure(e, logInput)
            e
        }.let { Result.failure(JsonUtilParseException(it)) }
    }

    /**
     * 파싱 실패 출력
     *
     * [logInput]이 false면 입력이 노출되지 않도록 예외 클래스 이름만 core [Log]로 남김.
     */
    private fun reportFailure(e: Throwable, logInput: Boolean) {
        if (logInput) Log.w(e) else Log.w("[JsonUtil] 파싱 실패: ${e.javaClass.simpleName}")
    }
}

class JsonUtilParseException(cause: Throwable): Throwable(cause)