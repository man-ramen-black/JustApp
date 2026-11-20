package com.black.feature.dday.data.usecase

import android.content.Context
import com.black.core.system.CalendarSource
import com.black.core.system.SystemCalendarSource
import com.black.feature.dday.data.DdayRepository
import com.black.feature.dday.model.DDAY_MAX_ITEMS
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.ddayLabel
import com.black.feature.dday.model.eventStartDate
import com.black.feature.dday.model.formatEventDate
import com.black.feature.dday.model.queryRange
import com.black.feature.dday.model.selectUpcoming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** [LoadDdayItemsUseCase.load]의 결과 */
sealed interface DdayLoadResult {
    /** 캘린더 읽기 권한 없음 */
    data object NoPermission : DdayLoadResult

    /** 캘린더 조회 실패 */
    data object Failed : DdayLoadResult

    /** 조회 성공, [items]는 시작일 순이며 비어 있을 수 있음 */
    data class Loaded(val items: List<DdayItem>) : DdayLoadResult
}

/**
 * 앞에서 [count]개 항목만 남긴 결과, [DdayLoadResult.Loaded]가 아니면 그대로
 *
 * 불러오기는 [DDAY_MAX_ITEMS]개까지 하고 보이는 개수는 그리는 쪽에서 줄임.
 * 설정 화면이 개수를 바꿀 때 다시 조회하지 않고 미리보기에 바로 반영하기 위함.
 */
fun DdayLoadResult.limitTo(count: Int): DdayLoadResult {
    return if (this is DdayLoadResult.Loaded) DdayLoadResult.Loaded(items.take(count)) else this
}

/**
 * 기기 캘린더에서 위젯에 올릴 D-Day 항목을 불러옴
 *
 * 제목이 비었거나 공백이면 [DdayItem.title]은 null로 두고, 표시 단계에서 `dday_untitled`로 바꿈.
 * ContentResolver 조회는 블로킹 호출이라 IO 디스패처에서 실행.
 */
class LoadDdayItemsUseCase(
    private val source: CalendarSource,
    private val repository: DdayRepository,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) {
    @Inject
    constructor(repository: DdayRepository, @ApplicationContext context: Context) : this(
        SystemCalendarSource(context),
        repository,
        Instant::now,
        ZoneId::systemDefault,
    )

    /** Hilt 없이 직접 만드는 위젯·작업용 생성자 */
    constructor(context: Context) : this(DdayRepository(context.applicationContext), context.applicationContext)

    suspend fun load(): DdayLoadResult {
        val zoneId = zone()
        val today = now().atZone(zoneId).toLocalDate()
        val excludedCalendarIds = repository.getExcludedCalendarIds()
        return withContext(Dispatchers.IO) {
            if (!source.canRead()) {
                return@withContext DdayLoadResult.NoPermission
            }
            val range = queryRange(today, zoneId)
            val instances = source.queryInstances(range.first, range.last)
                ?: return@withContext DdayLoadResult.Failed
            val items = selectUpcoming(instances, excludedCalendarIds, today, zoneId).map {
                DdayItem(
                    eventId = it.eventId,
                    title = it.title?.takeIf { title -> title.isNotBlank() },
                    label = ddayLabel(eventStartDate(it.begin, it.allDay, zoneId), today),
                    dateText = formatEventDate(it.begin, it.allDay, zoneId),
                    begin = it.begin,
                    end = it.end,
                )
            }
            DdayLoadResult.Loaded(items)
        }
    }
}
