package com.black.app.usagelog.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.black.app.usagelog.upload.SendReason
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [UsageLogScheduler]의 고유 주기 작업 예약과 취소 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogSchedulerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: UsageLogScheduler

    /** 아무 일도 하지 않고 성공을 돌려주는 작업자(실제 수집을 돌리지 않기 위함) */
    private class NoOpWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            reasons.add(inputData.getString(UsageLogSendWorker.KEY_REASON).orEmpty())
            return Result.success()
        }

        companion object {
            /** 실행된 작업이 받은 입력 reason 기록 */
            val reasons = ArrayList<String>()
        }
    }

    private val noOpWorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = NoOpWorker(appContext, workerParameters)
    }

    /**
     * 작업자를 아무 일도 하지 않는 것으로 바꿔 WorkManager 초기화
     *
     * 지연이 없는 주기 작업은 예약 즉시 실행되며, 실행 뒤에도 ENQUEUED로 돌아옴.
     */
    @Before
    fun setupScheduler() {
        NoOpWorker.reasons.clear()
        val configuration = Configuration.Builder()
            .setWorkerFactory(noOpWorkerFactory)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        scheduler = UsageLogScheduler(context)
    }

    private fun workInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(UsageLogScheduler.PERIODIC_COLLECT_WORK_NAME).get()

    /**
     * 두 번 예약해도 고유 작업이 하나만 대기하는지 검증
     *
     * Given: 예약 없음
     * When: schedulePeriodicCollect 두 번 호출
     * Then: 작업 하나, 상태 ENQUEUED
     */
    @Test
    fun test_01_schedulePeriodicEnqueuesOneUniqueWork() {
        /** Given / When **/
        scheduler.schedulePeriodicCollect()
        scheduler.schedulePeriodicCollect()

        /** Then **/
        val infos = workInfos()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    /**
     * 취소하면 작업이 취소 상태가 되는지 검증
     *
     * Given: 예약된 주기 작업
     * When: cancelPeriodicCollect 호출
     * Then: 상태 CANCELLED
     */
    @Test
    fun test_02_cancelPeriodicCancels() {
        /** Given **/
        scheduler.schedulePeriodicCollect()

        /** When **/
        scheduler.cancelPeriodicCollect()

        /** Then **/
        assertEquals(WorkInfo.State.CANCELLED, workInfos().single().state)
    }

    private fun sendWorkInfos(name: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    /**
     * 정기 전송을 두 번 예약해도 대기 작업이 하나만 남는지 검증
     *
     * Given: 예약 없음
     * When: enqueueSend(PERIODIC) 두 번 호출
     * Then: SEND_WORK_NAME에 작업 하나, 실행하면 입력 reason "periodic"
     */
    @Test
    fun test_03_periodicSendKeepsPendingWork() {
        /** Given / When **/
        scheduler.enqueueSend(SendReason.PERIODIC)
        scheduler.enqueueSend(SendReason.PERIODIC)

        /** Then **/
        val infos = sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME)
        assertEquals(1, infos.size)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(infos.single().id)
        assertEquals(listOf("periodic"), NoOpWorker.reasons)
    }

    /**
     * 즉시 전송이 대기 중이던 즉시 전송을 대체하고 정기 전송과 별개인지 검증
     *
     * Given: 정기 전송과 MANUAL 즉시 전송이 대기 중
     * When: enqueueSend(HOME_ARRIVE) 호출
     * Then: SEND_NOW_WORK_NAME에서 취소되지 않은 작업은 하나이며 실행하면 입력 reason "home_arrive", 정기 전송은 대기 그대로
     */
    @Test
    fun test_04_immediateSendReplacesPendingWork() {
        /** Given **/
        scheduler.enqueueSend(SendReason.PERIODIC)
        scheduler.enqueueSend(SendReason.MANUAL)

        /** When **/
        scheduler.enqueueSend(SendReason.HOME_ARRIVE)

        /** Then **/
        val pending = sendWorkInfos(UsageLogScheduler.SEND_NOW_WORK_NAME).single { it.state != WorkInfo.State.CANCELLED }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(pending.id)
        assertEquals(listOf("home_arrive"), NoOpWorker.reasons)
        assertEquals(WorkInfo.State.ENQUEUED, sendWorkInfos(UsageLogScheduler.SEND_WORK_NAME).single().state)
    }
}
