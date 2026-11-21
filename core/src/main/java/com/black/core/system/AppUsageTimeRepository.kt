package com.black.core.system

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration

/**
 * 최근 기간 안 앱별 사용 구간 조회
 *
 * 기간 시작 전에 열려 기간 안으로 이어진 사용을 경계부터 세기 위해 기간 시작보다 [QUERY_LOOKBACK_MILLIS]만큼
 * 앞서 이벤트를 조회함. 이 여유보다 길게 이어진 사용은 앞부분 이벤트가 없어 시간에서 빠짐.
 * 시스템 사용 기록 조회가 블로킹 호출이라 호출 스레드와 무관하게 IO 디스패처에서 실행함.
 *
 * @param includeVisible 작은 창·분할 화면처럼 화면에 보이던 시간도 셀지 여부. API 29 미만은 `ACTIVITY_STOPPED`가 남지 않아
 * 보이는 구간이 화면 꺼짐까지 늘어나므로 포그라운드 구간만 셈.
 * @param clock 현재 시각. 테스트가 후행 람다(`AppUsageTimeRepository(source) { now }`)로 넘기도록 마지막 인자로 둠.
 */
class AppUsageTimeRepository(
    private val source: UsageEventSource,
    private val includeVisible: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(SystemUsageEventSource(context))

    /**
     * 지금부터 [period] 전까지 [packageNames] 앱별 사용 구간 조회
     *
     * @param mergeGapMillis 같은 앱의 짧은 이탈을 이어 붙이는 최대 간격, 0이면 이어 붙이지 않음.
     * @return 조회 시각과 앱별 사용 구간, 사용 정보 접근 권한이 없어 조회하지 못하면 null.
     */
    suspend fun getRecentUsageSessions(
        packageNames: Collection<String>,
        period: Duration,
        mergeGapMillis: Long,
    ): RecentAppUsage? = withContext(Dispatchers.IO) {
        val end = clock()
        val begin = end - period.inWholeMilliseconds
        val events = source.query(begin - QUERY_LOOKBACK_MILLIS, end) ?: return@withContext null
        RecentAppUsage(
            queriedAtMillis = end,
            sessions = AppUsageTimeCalculator.usageSessions(events, begin, end, packageNames, mergeGapMillis, includeVisible),
        )
    }

    companion object {
        const val QUERY_LOOKBACK_MILLIS = 6 * 60 * 60 * 1000L
    }
}
