package com.black.app.usagelog.collect

import android.content.Context
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.ui.common.selectapp.InstalledAppResolver
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.UsageLogRecord
import com.black.core.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 시스템 사용 기록을 읽어 앱 사용 구간과 화면 기록을 저장
 *
 * 최근 [SETTLE_DELAY_MILLIS]는 시스템이 이벤트를 확정하기 전이라 읽지 않고 다음 수집으로 미룸.
 */
class UsageStatsCollector(
    private val source: UsageEventSource,
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    appNameOf: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: UsageLogSettingsRepository,
        repository: UsageLogRepository,
        installedAppResolver: InstalledAppResolver,
    ) : this(SystemUsageEventSource(context), settings, repository, installedAppResolver::resolveLabel)

    // 라벨 캐시는 이 인스턴스 수명에 한정(앱 이름 변경은 인스턴스를 새로 만들어야 반영)
    private val appNameCache = HashMap<String, String>()
    private val cachedAppNameOf: (String) -> String = { packageName ->
        appNameCache.getOrPut(packageName) { appNameOf(packageName) }
    }

    /**
     * 커서 이후 확정된 이벤트를 기록으로 저장하고 커서를 옮김
     *
     * 권한이 없어 조회하지 못하면 커서와 열린 구간을 그대로 둠.
     * 기록 추가와 진행 상태(열린 구간·커서) 저장은 취소돼도 끝까지 실행해, 취소로 기록만 남고 커서가 그대로인 일은 없음.
     * 두 저장은 저장소가 달라 원자적이지 않으므로 진행 상태 저장이 실패하면 다음 수집에서 같은 기록이 중복될 수 있음.
     * 시스템 사용 기록 조회와 앱 라벨 조회가 블로킹 호출이라 호출 스레드와 무관하게 IO 디스패처에서 실행함.
     */
    suspend fun collect() = mutex.withLock {
        withContext(Dispatchers.IO) { collectLocked() }
    }

    private suspend fun collectLocked() {
        if (!settings.isCollectionEnabled()) return
        val end = clock() - SETTLE_DELAY_MILLIS
        val cursor = settings.getUsageCursor()
        if (end <= cursor) return

        val events = source.query(cursor, end) ?: return
        val records = ArrayList<UsageLogRecord>()
        var openSession = settings.getOpenSession()

        // 이벤트 보관 기간이 지나 앞부분이 사라졌는지 확인
        val firstEventTime = events.firstOrNull()?.time ?: end
        if (end - cursor > EXPIRY_CHECK_AGE_MILLIS && firstEventTime > cursor + EXPIRY_GAP_MIN_MILLIS) {
            records.add(GapRecord(cursor, firstEventTime, GapCause.USAGE_EVENTS_EXPIRED))
            openSession = null
        }

        // 이벤트를 기록으로 변환
        val result = UsageSessionBuilder.build(events, openSession, cachedAppNameOf)
        records.addAll(result.records)

        // 기록을 먼저 저장하고 그 뒤에 열린 구간과 커서를 한 번에 저장, 취소돼도 둘 다 끝까지 실행
        val progressSaved = withContext(NonCancellable) {
            repository.add(records)
            settings.setUsageProgress(result.openSession, end)
        }
        progressSaved.onFailure {
            Log.w("[UsageStatsCollector] 열린 구간과 커서 저장 실패, 이전 진행 상태 유지: ${it.javaClass.simpleName}")
            return
        }
        Log.i("[UsageStatsCollector] 이벤트 ${events.size}건에서 기록 ${records.size}건 저장")
    }

    companion object {
        const val SETTLE_DELAY_MILLIS = 10 * 60 * 1000L
        const val EXPIRY_CHECK_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val EXPIRY_GAP_MIN_MILLIS = 60 * 60 * 1000L

        // 수집기 인스턴스가 작업·화면마다 따로 만들어져 잠금은 프로세스에 하나
        private val mutex = Mutex()

        /**
         * 수집과 겹치지 않게 [block] 실행
         *
         * 커서·열린 구간을 바꾸는 쪽(수집 켜기·끄기)이 진행 중인 수집의 저장에 덮이지 않도록 수집과 같은 잠금을 씀.
         * 잠금은 재진입되지 않으므로 [block] 안에서 [collect]를 부르면 교착됨.
         */
        suspend fun <T> withCollectionLock(block: suspend () -> T): T = mutex.withLock { block() }
    }
}
