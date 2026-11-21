package com.black.feature.dday.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.black.core.data.BaseDataStore
import com.black.core.data.PreferenceUpdate
import com.black.core.data.blackDataStore
import com.black.feature.dday.model.DdayStyle
import com.black.feature.dday.model.DdayStyleField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 같은 모듈의 테스트가 범위 밖 값을 직접 저장할 수 있도록 internal로 선언한 DataStore delegate
 */
internal val Context.ddayDataStore: DataStore<Preferences> by blackDataStore("dday")

/**
 * D-Day 위젯 로컬 데이터 저장소
 *
 * 스타일 값은 [DdayStyleField]마다 키 하나(필드 이름 소문자), 제외 캘린더는 id 문자열 집합 하나로 저장.
 */
class DdayDataStore(context: Context) : BaseDataStore(context) {

    override fun getDataStore(context: Context): DataStore<Preferences> = context.ddayDataStore

    private fun styleKey(field: DdayStyleField) = intPreferencesKey(field.name.lowercase())

    /** 저장된 스타일 Flow, 저장값이 없으면 필드 기본값이고 범위 밖 값은 [DdayStyle.with]로 맞춤 */
    fun styleFlow(): Flow<DdayStyle> {
        val flows = DdayStyleField.entries.map { field -> flow(styleKey(field), field.default) }
        return combine(flows) { values ->
            DdayStyleField.entries.foldIndexed(DdayStyle()) { index, style, field ->
                style.with(field, values[index])
            }
        }
    }

    suspend fun getStyle(): DdayStyle {
        return DdayStyleField.entries.fold(DdayStyle()) { style, field ->
            style.with(field, get(styleKey(field), field.default))
        }
    }

    /** [DdayStyleField] 값을 모두 한 번에 저장 */
    suspend fun updateStyle(style: DdayStyle): Result<Unit> {
        val updates = DdayStyleField.entries.map { PreferenceUpdate(styleKey(it), style[it]) }
        return updateAll(*updates.toTypedArray())
    }

    fun excludedCalendarIdsFlow(): Flow<Set<Long>> = flow(KEY_EXCLUDED_CALENDAR_IDS, emptySet()).map { it.toIds() }

    suspend fun getExcludedCalendarIds(): Set<Long> = get(KEY_EXCLUDED_CALENDAR_IDS, emptySet()).toIds()

    /** [calendarId]를 제외 목록에 넣거나([excluded] true) 뺀 뒤의 집합 */
    suspend fun updateCalendarExcluded(calendarId: Long, excluded: Boolean): Result<Set<Long>> {
        return updateWith(KEY_EXCLUDED_CALENDAR_IDS, emptySet()) { stored ->
            if (excluded) {
                stored + calendarId.toString()
            } else {
                stored - calendarId.toString()
            }
        }.map { it.toIds() }
    }

    private fun Set<String>.toIds(): Set<Long> = mapNotNull { it.toLongOrNull() }.toSet()

    private companion object {
        val KEY_EXCLUDED_CALENDAR_IDS = stringSetPreferencesKey("excluded_calendar_ids")
    }
}
