package com.black.core.model

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.black.core.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.properties.ReadOnlyProperty

/**
 * [BaseDataStore] 하위 클래스가 쓰는 Preferences DataStore delegate
 *
 * [preferencesDataStore]를 직접 쓰면 corruptionHandler가 빠져, 파일이 손상되면 읽기는 [BaseDataStore]의 IOException 처리로
 * 기본값만 돌려주고 저장(edit)은 손상 파일을 먼저 읽다 CorruptionException으로 계속 실패.
 * 손상 파일을 빈 값으로 바꾸는 [ReplaceFileCorruptionHandler]를 항상 지정
 *
 * 사용: `private val Context.fooDataStore by blackDataStore("foo")`
 */
fun blackDataStore(
    name: String,
    produceMigrations: (Context) -> List<DataMigration<Preferences>> = { listOf() },
): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(
        name = name,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceMigrations = produceMigrations,
    )

/** https://developer.android.com/topic/libraries/architecture/datastore?hl=ko */
abstract class BaseDataStore(private val context: Context) {
    protected abstract fun getDataStore(context: Context): DataStore<Preferences>

    /** https://github.com/android/codelab-android-datastore/blob/preferences_datastore/app/src/main/java/com/codelab/android/datastore/data/UserPreferencesRepository.kt#L138 */
    protected suspend fun <T> get(key: Preferences.Key<T>, def: T): T {
        val data = getDataStore(context).data
            .catch {
                when (it) {
                    is IOException -> emit(emptyPreferences())
                    /** [DataStore.data] 예외가 발생하면 데이터 읽기를 다시 시도합니다. */
                    else -> throw it
                }
            }
            /** [DataStore.data] use data.first() to access a single snapshot. */
            .first()
            .toPreferences()[key] ?: def
        Log.v("[${key.name}] $data")
        return data
    }

    protected fun <T> flow(key: Preferences.Key<T>): Flow<T?> {
        return getDataStore(context).data
            .catch {
                when (it) {
                    is IOException -> emit(emptyPreferences())
                    /** [DataStore.data] 예외가 발생하면 데이터 읽기를 다시 시도합니다. */
                    else -> throw it
                }
            }
            .map { it[key] }
    }

    protected suspend fun <T> update(key: Preferences.Key<T>, data: T): Throwable? {
        Log.v("[${key.name}] $data")
        return try {
            getDataStore(context).edit { it[key] = data }
            null
        } catch (e: IOException) {
            e
        } catch (e: Exception) {
            e
        }
    }

    protected suspend fun <T> remove(key: Preferences.Key<T>): Throwable? {
        Log.v("[${key.name}]")
        return try {
            getDataStore(context).edit { it.remove(key) }
            null
        } catch (e: IOException) {
            e
        } catch (e: Exception) {
            e
        }
    }
}