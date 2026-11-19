package com.black.feature.usagelog.data.usecase

import android.Manifest
import android.content.Context
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import com.black.core.util.runSuspendCatching
import com.black.feature.usagelog.data.UsageLogRepository
import com.black.feature.usagelog.data.UsageLogSettingsRepository
import com.black.feature.usagelog.model.GapCause
import com.black.feature.usagelog.model.GapRecord
import com.black.feature.usagelog.model.SmsProgress
import com.black.feature.usagelog.model.WifiState
import com.black.feature.usagelog.receiver.UsageLogPackageReplacedReceiver
import com.black.feature.usagelog.worker.UsageLogScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 기기 사용 기록 수집의 켜기·끄기 상태 전환과 주기 작업 예약
 *
 * [isSmsGranted]는 문자 읽기 권한 허용 여부이며, 끄기의 공백 시작에 문자 커서를 반영할지 가름.
 * [collectSmsWithinLock]은 수집 잠금 안에서 문자를 수집하는 함수이며, 끄기에서 꺼진 시각을 정하기 전에 부름.
 */
class UsageLogCollectionController(
    private val settings: UsageLogSettingsRepository,
    private val repository: UsageLogRepository,
    private val scheduler: UsageLogScheduler,
    private val isSmsGranted: () -> Boolean = { true },
    private val collectSmsWithinLock: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: UsageLogSettingsRepository,
        repository: UsageLogRepository,
        scheduler: UsageLogScheduler,
        smsCollector: SmsCollector,
    ) : this(
        settings,
        repository,
        scheduler,
        { PermissionHelper.isGranted(context, Manifest.permission.READ_SMS) },
        { smsCollector.collectWithinLock() },
        System::currentTimeMillis,
    )

    /**
     * 수집 켜기
     *
     * 암호화 키가 없으면 먼저 만들며, 실패하면 상태를 바꾸지 않고 그 결과를 돌려줌.
     * 꺼져 있던 구간은 공백 기록으로 남기고 커서·열린 구간·Wi-Fi 상태·문자 진행 상태를 지금 기준으로 비움.
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
            runSuspendCatching { repository.add(listOf(GapRecord(offSince, now, GapCause.COLLECTION_OFF)), now) }
                .onFailure { return Result.failure(it) }
        }
        // 진행 상태 초기화 후 켜짐 저장
        settings.setUsageProgress(null, now).onFailure { return Result.failure(it) }
        settings.setSmsProgress(SmsProgress(now, now)).onFailure { return Result.failure(it) }
        settings.setLastWifiState(WifiState(false, null)).onFailure { return Result.failure(it) }
        settings.setCollectionEnabled(true).onFailure { return Result.failure(it) }
        // 켜졌으면 예약을 먼저 해 꺼진 시각 정리가 실패해도 주기 수집은 돌아감
        runSuspendCatching { scheduler.schedulePeriodicCollect() }.onFailure { return Result.failure(it) }
        return settings.setCollectionOffSince(0L)
    }

    /**
     * 수집 끄기
     *
     * 다시 켤 때 공백 기록의 시작으로 쓸 꺼진 시각을 저장함.
     * 커서 이후와 열린 구간은 아직 기록되지 않아 끈 뒤 읽지 않으므로, 지금·커서·열린 구간 시작 중 가장 이른 시각을 씀.
     * 문자 읽기 권한이 있으면 문자 커서도 후보에 넣고, 권한이 없으면 문자를 읽지 못해 공백이 문자 커서까지 늘어나지 않도록 뺌.
     * 권한을 막 허용해 문자 커서가 켠 시각에 머물러 있으면 공백이 앱 사용 기록이 있는 기간까지 늘어나므로, 후보를 정하기 전에 문자를 한 번 수집함.
     * 그 수집이 실패하면 로그만 남기고 끄기를 계속하며, 이때는 문자 커서부터 공백이 시작됨.
     * 꺼진 시각을 꺼짐보다 먼저 저장해, 꺼진 시각 저장이 실패하면 켜진 채로 남아 공백 기록 없이 꺼지는 일이 없음.
     * 진행 중인 수집이 끝난 뒤 실행해 그 수집의 저장과 겹치지 않음.
     * 저장이 실패하면 남은 단계를 멈추고 그 결과를 돌려줌.
     */
    suspend fun disable(): Result<Unit> = UsageStatsCollector.withCollectionLock { disableLocked() }

    private suspend fun disableLocked(): Result<Unit> {
        // 문자 커서를 지금 근처로 옮겨 둠, 실패해도 끄기는 계속
        runSuspendCatching { collectSmsWithinLock() }.onFailure {
            Log.w("[UsageLogCollectionController] 끄기 전 문자 수집 실패: ${it.javaClass.simpleName}")
        }
        // 아직 읽지 않은 범위의 시작, 커서 0은 한 번도 켠 적 없음
        val now = clock()
        val cursor = settings.getUsageCursor().takeIf { it > 0 } ?: now
        val openSessionStart = settings.getOpenSession()?.start ?: now
        val smsCursor = if (isSmsGranted()) settings.getSmsProgress()?.cursor ?: now else now
        settings.setCollectionOffSince(minOf(now, cursor, openSessionStart, smsCursor)).onFailure { return Result.failure(it) }
        settings.setCollectionEnabled(false).onFailure { return Result.failure(it) }
        settings.setOpenSession(null).onFailure { return Result.failure(it) }
        return runSuspendCatching { scheduler.cancelPeriodicCollect() }
    }

    /**
     * 보관 기간 정리와 주기 작업 예약 복구
     *
     * 기기 사용 기록 화면에 들어올 때와 앱 업데이트 직후 [UsageLogPackageReplacedReceiver]에서 호출됨.
     * 예약은 Worker 클래스 경로가 바뀐 옛 주기 작업도 새 클래스로 갱신함([UsageLogScheduler.schedulePeriodicCollect]).
     * 수집을 끈 동안에는 주기 수집 작업이 없어 정리가 돌지 않으므로 수집 여부와 관계없이 정리하며, 실패는 로그만 남기고 진행함.
     * 켜져 있으면 암호화 키를 보장하고 사라진 주기 작업 예약을 복구함.
     * 키가 생기기 전에 수집을 켠 설치도 전송이 설정 미비로 끝나지 않도록 키를 만들며, 실패해도 예약은 진행함.
     * 켜짐 확인부터 예약까지는 켜기·끄기와 같은 수집 잠금 안에서 실행해, 켜짐을 확인한 뒤 끄기가 취소한 주기 작업을 다시 예약하지 않음.
     */
    suspend fun ensureScheduled() {
        runSuspendCatching { repository.purgeExpired(clock()) }.onFailure {
            Log.w("[UsageLogCollectionController] 보관 기간 정리 실패: ${it.javaClass.simpleName}")
        }
        UsageStatsCollector.withCollectionLock { ensureScheduledLocked() }
    }

    private suspend fun ensureScheduledLocked() {
        if (!settings.isCollectionEnabled()) {
            return
        }
        settings.ensureEncryptionKey().onFailure {
            Log.w("[UsageLogCollectionController] 암호화 키 생성 실패: ${it.javaClass.simpleName}")
        }
        scheduler.schedulePeriodicCollect()
    }
}
