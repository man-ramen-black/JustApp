package com.black.app.assistanttask.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 비서 할 일 노션 동기화 작업
 *
 * 15분 주기 작업과 완료 체크·추가 직후의 바로 동기화 작업이 함께 쓰며, 노션 목록을 읽은 뒤 반영 대기 항목도 노션에 보냄.
 * `UsageLogSendWorker`처럼 hilt-work를 쓰지 않고 저장소를 직접 만듦.
 */
class AssistantTaskSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val status = try {
            AssistantTaskSynchronizer(applicationContext).sync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("[AssistantTaskSyncWorker] 동기화 작업 실패: ${failure::class.java.simpleName}")
            return Result.failure()
        }
        return toWorkResult(status, runAttemptCount)
    }

    companion object {
        /** 네트워크·서버 오류가 거듭될 때 첫 실행을 포함해 실행하는 최대 횟수 */
        const val MAX_ATTEMPTS = 3

        /** 잠시 뒤 다시 하면 나아질 수 있어 재시도하는 결과 */
        private val RETRY_STATUSES = setOf(AssistantTaskSyncStatus.NETWORK_ERROR, AssistantTaskSyncStatus.SERVER_ERROR)

        /**
         * 동기화 결과를 작업 결과로 변환
         *
         * 네트워크 오류와 노션 서버 오류만 [MAX_ATTEMPTS]번째 실행 전까지 재시도하고, 그 뒤에는 실패로 끝내 다음 15분 주기를 기다림.
         * 인증·요청 오류, 기기 저장본 오류와 설정 없음은 재시도해도 같아서 성공으로 끝내며, 사유는 동기화가 남긴 결과 상태가 보여 줌.
         * [runAttemptCount]는 WorkManager가 넘기는 값으로 첫 실행이 0.
         */
        internal fun toWorkResult(status: AssistantTaskSyncStatus, runAttemptCount: Int): ListenableWorker.Result =
            when {
                status !in RETRY_STATUSES -> ListenableWorker.Result.success()
                runAttemptCount + 1 < MAX_ATTEMPTS -> ListenableWorker.Result.retry()
                else -> ListenableWorker.Result.failure()
            }
    }
}
