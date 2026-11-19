package com.black.core.system

import android.content.Context
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * [PartialWakeLock]의 wake lock 획득과 제한 시간, 다시 획득할 때 이전 것 해제, 해제 동작 검증
 *
 * 섀도 wake lock은 제한 시간을 [android.os.SystemClock.elapsedRealtime] 기준으로 판정하므로 시계를 앞당겨 만료를 확인
 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PartialWakeLockTest : BaseTest() {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * 태그를 붙여 획득하고 제한 시간이 지나면 풀리는지 검증
     *
     * Given: 태그 "JustApp:Test"의 PartialWakeLock
     * When: 10분으로 acquire 호출 뒤 시계를 9분, 다시 2분 앞당김
     * Then: 같은 태그, 9분 시점은 쥔 상태, 11분 시점은 풀린 상태
     */
    @Test
    fun test_01_acquireHoldsWakeLockUntilTimeout() = runTest {
        /** Given **/
        val partialWakeLock = PartialWakeLock(context, TAG)

        /** When **/
        partialWakeLock.acquire(10.minutes)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        ShadowSystemClock.advanceBy(9.minutes.toJavaDuration())
        val heldBeforeTimeout = wakeLock.isHeld
        ShadowSystemClock.advanceBy(2.minutes.toJavaDuration())
        val heldAfterTimeout = wakeLock.isHeld

        /** Then **/
        assertEquals(TAG, shadowOf(wakeLock).tag)
        assertTrue(heldBeforeTimeout)
        assertFalse(heldAfterTimeout)
    }

    /**
     * 다시 획득하면 이전 wake lock을 풀고 새 제한 시간으로 쥐는지 검증
     *
     * Given: 10분으로 획득한 PartialWakeLock
     * When: 5분으로 다시 acquire 호출 뒤 시계를 6분 앞당김
     * Then: 이전 wake lock은 바로 풀림, 새 wake lock은 다시 획득 직후 쥔 상태이고 6분 뒤 풀린 상태
     */
    @Test
    fun test_02_acquireAgainReleasesPreviousWakeLock() = runTest {
        /** Given **/
        val partialWakeLock = PartialWakeLock(context, TAG)
        partialWakeLock.acquire(10.minutes)
        val previous = ShadowPowerManager.getLatestWakeLock()

        /** When **/
        partialWakeLock.acquire(5.minutes)
        val current = ShadowPowerManager.getLatestWakeLock()
        val previousHeld = previous.isHeld
        val currentHeld = current.isHeld
        ShadowSystemClock.advanceBy(6.minutes.toJavaDuration())
        val currentHeldAfterTimeout = current.isHeld

        /** Then **/
        assertNotSame(previous, current)
        assertFalse(previousHeld)
        assertTrue(currentHeld)
        assertFalse(currentHeldAfterTimeout)
    }

    /**
     * 해제하면 쥐고 있던 wake lock이 풀리고, 쥔 것이 없을 때 해제해도 예외 없이 넘어가는지 검증
     *
     * Given: 획득한 PartialWakeLock, 획득한 적 없는 PartialWakeLock
     * When: 획득한 쪽은 release 두 번, 획득한 적 없는 쪽은 한 번 호출
     * Then: 획득했던 wake lock은 풀림, 새로 만든 wake lock 없음
     */
    @Test
    fun test_03_releaseReleasesHeldWakeLockAndIgnoresMissing() = runTest {
        /** Given **/
        val acquired = PartialWakeLock(context, TAG)
        acquired.acquire(10.minutes)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        ShadowPowerManager.clearWakeLocks()
        val neverAcquired = PartialWakeLock(context, TAG)

        /** When **/
        acquired.release()
        acquired.release()
        neverAcquired.release()

        /** Then **/
        assertFalse(wakeLock.isHeld)
        assertNull(ShadowPowerManager.getLatestWakeLock())
    }

    private companion object {
        const val TAG = "JustApp:Test"
    }
}
