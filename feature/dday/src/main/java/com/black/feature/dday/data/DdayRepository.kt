package com.black.feature.dday.data

import android.content.Context
import com.black.feature.dday.data.datastore.DdayDataStore
import com.black.feature.dday.model.DdayStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** D-Day 위젯의 스타일과 제외 캘린더 저장·조회 */
class DdayRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = DdayDataStore(context)

    val style: Flow<DdayStyle> = dataStore.styleFlow()

    suspend fun getStyle(): DdayStyle = dataStore.getStyle()

    suspend fun saveStyle(style: DdayStyle): Result<Unit> = dataStore.updateStyle(style)

    val excludedCalendarIds: Flow<Set<Long>> = dataStore.excludedCalendarIdsFlow()

    suspend fun getExcludedCalendarIds(): Set<Long> = dataStore.getExcludedCalendarIds()

    /** [calendarId]를 제외 목록에 넣거나 빼고, 성공하면 바뀐 제외 목록을 돌려줌 */
    suspend fun setCalendarExcluded(calendarId: Long, excluded: Boolean): Result<Set<Long>> {
        return dataStore.updateCalendarExcluded(calendarId, excluded)
    }
}
