package com.black.app.usagelog

import com.black.app.model.UsageLogRepository
import com.black.app.model.UsageLogSettingsRepository
import com.black.app.usagelog.collect.UsageStatsCollector
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.WifiState
import com.black.app.usagelog.work.UsageLogScheduler
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** 기기 사용 기록 수집의 켜기·끄기 상태 전환과 주기 작업 예약 */
class UsageLogCollectionController(
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val scheduler: UsageLogScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        settings: UsageLogSettingsRepository,
        repository: UsageLogRepository,
        scheduler: UsageLogScheduler,
    ) : this(settings, repository, scheduler, System::currentTimeMillis)

    /**
     * 수집 켜기
     *
     * 암호화 키가 없으면 먼저 만들며, 실패하면 상태를 바꾸지 않고 그 결과를 돌려줌.
     * 꺼져 있던 구간은 공백 기록으로 남기고 커서·열린 구간·Wi-Fi 상태를 지금 기준으로 비움.
     * Wi-Fi 감시는 켜짐으로 바뀌는 순간 저장된 Wi-Fi 상태를 기준으로 맞추므로 Wi-Fi 상태를 비운 뒤에 켜짐을 저장함.
     * 꺼진 시각은 켜짐 저장과 예약 뒤에 지워, 켜짐 저장이 실패해도 다음 켜기에서 공백 기록의 시작을 잃지 않음.
     * 진행 중인 수집이 끝난 뒤 실행해 그 수집의 커서 저장이 켠 시각을 덮지 않음.
     * 저장이 실패하면 남은 단계를 멈추고 그 결과를 돌려줌.
     * 공백 기록을 넣은 뒤 이어지는 저장이 실패하면 재시도 때 같은 구간의 공백이 중복될 수 있으며, PC는 같은 구간으로 다룸.
     */
    suspend fun enable(): Result<Unit> = UsageStatsCollector.withCollectionLock { enableLocked() }

    private suspend fun enableLocked(): Result<Unit> {
        // 전송 암호화 키 보장, 실패하면 아무것도 바꾸지 않음
        settings.ensureEncryptionKey().onFailure { return Result.failure(it) }
        val now = clock()
        // 꺼져 있던 구간 공백 기록
        val offSince = settings.getCollectionOffSince()
        if (offSince > 0) {
            catching { repository.add(listOf(GapRecord(offSince, now, GapCause.COLLECTION_OFF)), now) }
                .onFailure { return Result.failure(it) }
        }
        // 진행 상태 초기화 후 켜짐 저장
        settings.setUsageProgress(null, now).onFailure { return Result.failure(it) }
        settings.setLastWifiState(WifiState(false, null)).onFailure { return Result.failure(it) }
        settings.setCollectionEnabled(true).onFailure { return Result.failure(it) }
        // 켜졌으면 예약을 먼저 해 꺼진 시각 정리가 실패해도 주기 수집은 돌아감
        catching { scheduler.schedulePeriodicCollect() }.onFailure { return Result.failure(it) }
        return settings.setCollectionOffSince(0L)
    }

    /**
     * 수집 끄기
     *
     * 다시 켤 때 공백 기록의 시작으로 쓸 꺼진 시각을 저장함.
     * 커서 이후와 열린 구간은 아직 기록되지 않아 끈 뒤 읽지 않으므로, 지금·커서·열린 구간 시작 중 가장 이른 시각을 씀.
     * 꺼진 시각을 꺼짐보다 먼저 저장해, 꺼진 시각 저장이 실패하면 켜진 채로 남아 공백 기록 없이 꺼지는 일이 없음.
     * 진행 중인 수집이 끝난 뒤 실행해 그 수집의 저장과 겹치지 않음.
     * 저장이 실패하면 남은 단계를 멈추고 그 결과를 돌려줌.
     */
    suspend fun disable(): Result<Unit> = UsageStatsCollector.withCollectionLock { disableLocked() }

    private suspend fun disableLocked(): Result<Unit> {
        // 아직 읽지 않은 범위의 시작, 커서 0은 한 번도 켠 적 없음
        val now = clock()
        val cursor = settings.getUsageCursor().takeIf { it > 0 } ?: now
        val openSessionStart = settings.getOpenSession()?.start ?: now
        settings.setCollectionOffSince(minOf(now, cursor, openSessionStart)).onFailure { return Result.failure(it) }
        settings.setCollectionEnabled(false).onFailure { return Result.failure(it) }
        settings.setOpenSession(null).onFailure { return Result.failure(it) }
        return catching { scheduler.cancelPeriodicCollect() }
    }

    /**
     * 보관 기간 정리와 주기 작업 예약 복구
     *
     * 기기 사용 기록 화면에 들어올 때 호출됨.
     * 수집을 끈 동안에는 주기 수집 작업이 없어 정리가 돌지 않으므로 수집 여부와 관계없이 정리하며, 실패는 로그만 남기고 진행함.
     * 켜져 있으면 암호화 키를 보장하고 사라진 주기 작업 예약을 복구함.
     * 키가 생기기 전에 수집을 켠 설치도 전송이 설정 미비로 끝나지 않도록 키를 만들며, 실패해도 예약은 진행함.
     */
    suspend fun ensureScheduled() {
        catching { repository.purgeExpired(clock()) }.onFailure {
            Log.w("[UsageLogCollectionController] 보관 기간 정리 실패: ${it.javaClass.simpleName}")
        }
        if (!settings.isCollectionEnabled()) return
        settings.ensureEncryptionKey().onFailure {
            Log.w("[UsageLogCollectionController] 암호화 키 생성 실패: ${it.javaClass.simpleName}")
        }
        scheduler.schedulePeriodicCollect()
    }

    /**
     * 예외를 [Result]로 바꿈
     *
     * [CancellationException]은 다시 던짐.
     */
    private inline fun catching(block: () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
