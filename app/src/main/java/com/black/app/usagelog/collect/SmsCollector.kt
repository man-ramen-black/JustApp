package com.black.app.usagelog.collect

import android.content.Context
import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.usagelog.record.SmsProgress
import com.black.core.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 폰 문자 저장소의 받은·보낸 문자를 기록으로 저장
 *
 * 문자 행은 늦게 확정되므로(보내는 중에서 보냄, MMS 내려받기) 커서 앞 [LOOKBACK_MILLIS]를 다시 읽고 이미 기록한 문자 키로 거름.
 * 시계가 되돌아가도 커서는 줄이지 않고 이전 문자 키를 유지함.
 * 최근 [SETTLE_DELAY_MILLIS]는 MMS 파트가 아직 저장되지 않았을 수 있어 다음 수집으로 미룸.
 * 수집 켜기·끄기와 겹치지 않도록 앱 사용 수집과 같은 잠금을 쓰며, 잠금이 재진입되지 않으므로 앱 사용 수집 안에서 부르지 않음.
 * 진행 상태 저장이 실패하면 이미 저장한 기록이 다음 수집에서 한 번 더 저장될 수 있으며, PC는 `messageKey` 유일 색인으로 한 번만 쌓음.
 * 문자 본문·번호·이름은 로그에 남기지 않음.
 */
class SmsCollector(
    private val source: SmsSource,
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: UsageLogSettingsRepository,
        repository: UsageLogRepository,
    ) : this(SystemSmsSource(context), settings, repository)

    /**
     * 진행 상태 이후의 문자를 기록으로 저장하고 진행 상태를 옮김
     *
     * 문자를 처음 읽으면 지금부터 모으도록 진행 상태만 저장함.
     * 권한이 없어 조회하지 못하면 진행 상태를 그대로 둠.
     * 기록 추가와 진행 상태 저장은 취소돼도 끝까지 실행하며, 진행 상태 저장이 실패하면 다음 수집이 같은 구간을 다시 읽음.
     * 문자 저장소 조회가 블로킹 호출이라 IO 디스패처에서 실행함.
     */
    suspend fun collect() = UsageStatsCollector.withCollectionLock { collectWithinLock() }

    /**
     * 수집 잠금을 이미 잡은 호출자가 쓰는 [collect]
     *
     * 잠금이 재진입되지 않으므로 [UsageStatsCollector.withCollectionLock] 안에서는 [collect] 대신 이 함수를 부름.
     */
    suspend fun collectWithinLock() = withContext(Dispatchers.IO) { collectLocked() }

    private suspend fun collectLocked() {
        if (!settings.isCollectionEnabled()) return
        val now = clock()
        val progress = settings.getSmsProgress()
        // 처음 읽는 설치는 지금부터 모음
        if (progress == null) {
            settings.setSmsProgress(SmsProgress(now, now)).onFailure {
                Log.w("[SmsCollector] 문자 수집 시작 시각 저장 실패: ${it.javaClass.simpleName}")
            }
            return
        }
        val begin = maxOf(progress.floor, progress.cursor - LOOKBACK_MILLIS)
        val end = now - SETTLE_DELAY_MILLIS
        if (end <= begin) return

        val messages = source.query(begin, end)?.filter { it.time in begin until end } ?: return
        val records = messages.filter { it.messageKey !in progress.seenKeys }.distinctBy { it.messageKey }
        // 시계가 되돌아가 구간 끝이 이전 커서보다 앞서면 이전 키를 버리지 않음
        val seenKeys = messages.mapTo(HashSet()) { it.messageKey }.also { if (end < progress.cursor) it.addAll(progress.seenKeys) }

        // 기록을 먼저 저장하고 진행 상태 저장, 취소돼도 둘 다 끝까지 실행
        val progressSaved = withContext(NonCancellable) {
            repository.add(records)
            settings.setSmsProgress(SmsProgress(progress.floor, maxOf(end, progress.cursor), seenKeys))
        }
        progressSaved.onFailure {
            Log.w("[SmsCollector] 진행 상태 저장 실패, 다음 수집에서 같은 구간을 다시 읽음: ${it.javaClass.simpleName}")
            return
        }
        Log.i("[SmsCollector] 문자 ${messages.size}건 중 새 기록 ${records.size}건 저장")
    }

    companion object {
        const val LOOKBACK_MILLIS = 24 * 60 * 60 * 1000L
        const val SETTLE_DELAY_MILLIS = 2 * 60 * 1000L
    }
}
