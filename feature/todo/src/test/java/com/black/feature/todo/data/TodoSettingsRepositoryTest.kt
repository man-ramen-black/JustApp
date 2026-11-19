package com.black.feature.todo.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.black.feature.todo.data.datastore.TodoDataStore
import com.black.feature.todo.model.TodoConnection
import com.black.feature.todo.model.TodoSyncState
import com.black.feature.todo.model.TodoSyncStatus
import com.black.test.BaseTest
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [TodoSettingsRepository]의 연결 정보 판정과 저장·조회·삭제, 집 출발 시각 저장 로그 생략 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoSettingsRepositoryTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = TodoSettingsRepository(context)

    /**
     * 각 테스트 전 DataStore 비우기
     *
     * DataStore 파일이 프로세스 안에서 공유되어 앞 테스트의 저장값이 남으므로 테스트 간 격리용.
     */
    @Before
    fun clearDataStore() {
        runTest { TodoDataStore(context).clear() }
    }

    /**
     * 토큰과 data source ID가 모두 있을 때만 설정된 것으로 판정하는지 검증
     *
     * Given: 한쪽이 빈 연결 둘과 둘 다 있는 연결
     * When: isConfigured 조회
     * Then: 한쪽이 비면 false, 둘 다 있으면 true
     */
    @Test
    fun test_01_connectionIsConfiguredOnlyWhenBothPresent() = runTest {
        /** Given / When / Then **/
        assertFalse(TodoConnection("t", "").isConfigured)
        assertFalse(TodoConnection("", "d").isConfigured)
        assertTrue(TodoConnection("t", "d").isConfigured)
    }

    /**
     * 저장한 연결 정보를 그대로 다시 읽는지 검증
     *
     * Given: 연결 정보 저장
     * When: getConnection 조회
     * Then: 저장한 값과 같고 저장은 성공
     */
    @Test
    fun test_02_saveConnectionThenRead() = runTest {
        /** Given **/
        val saved = TodoConnection("secret-token", "data-source-id")

        /** When **/
        val result = repository.saveConnection(saved)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(saved, repository.getConnection())
        assertEquals(saved, repository.connection.first())
    }

    /**
     * 마지막 동기화 결과가 저장 전에는 null이고 저장 후에는 그대로 읽히는지 검증
     *
     * Given: 저장 전 상태
     * When: TodoSyncState(1000, AUTH_ERROR) 저장
     * Then: 저장 전 lastSync는 null, 저장 후에는 같은 값
     */
    @Test
    fun test_03_saveLastSyncThenRead() = runTest {
        /** Given **/
        assertNull(repository.lastSync.first())
        val state = TodoSyncState(1000, TodoSyncStatus.AUTH_ERROR)

        /** When **/
        val result = repository.saveLastSync(state)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(state, repository.lastSync.first())
    }

    /**
     * 마지막 동기화 결과를 지우면 다시 null로 읽히는지 검증
     *
     * Given: TodoSyncState(1000, SUCCESS) 저장
     * When: clearLastSync 호출
     * Then: 삭제 성공, lastSync null
     */
    @Test
    fun test_04_clearLastSyncRemovesState() = runTest {
        /** Given **/
        repository.saveLastSync(TodoSyncState(1000, TodoSyncStatus.SUCCESS))

        /** When **/
        val result = repository.clearLastSync()

        /** Then **/
        assertTrue(result.isSuccess)
        assertNull(repository.lastSync.first())
    }

    /**
     * 마지막 집 출발 시각이 저장 전에는 null이고 저장 후에는 그대로 읽히는지 검증
     *
     * Given: 저장 전 상태
     * When: 1234 저장
     * Then: 저장 전 null, 저장 후 1234
     */
    @Test
    fun test_05_saveLastHomeLeaveTimeThenRead() = runTest {
        /** Given **/
        assertNull(repository.getLastHomeLeaveTime())

        /** When **/
        val result = repository.saveLastHomeLeaveTime(1234L)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(1234L, repository.getLastHomeLeaveTime())
    }

    /**
     * 예약한 알람 ID 집합을 저장하고 다시 읽는지 검증
     *
     * Given: 저장 전 빈 집합
     * When: 두 ID 저장 뒤 한 ID로 다시 저장
     * Then: 저장 전 빈 집합, 이후 마지막으로 저장한 집합
     */
    @Test
    fun test_06_saveScheduledAlarmIdsThenRead() = runTest {
        /** Given **/
        assertEquals(emptySet<String>(), repository.getScheduledAlarmIds())

        /** When **/
        val first = repository.saveScheduledAlarmIds(setOf("a", "b"))
        val firstRead = repository.getScheduledAlarmIds()
        val second = repository.saveScheduledAlarmIds(setOf("c"))

        /** Then **/
        assertTrue(first.isSuccess && second.isSuccess)
        assertEquals(setOf("a", "b"), firstRead)
        assertEquals(setOf("c"), repository.getScheduledAlarmIds())
    }

    /**
     * 마지막 추정 집 출발 시각을 실제 출발 시각과 따로 저장하고 읽는지 검증
     *
     * Given: 저장 전 상태
     * When: 추정 출발 5678 저장
     * Then: 저장 전 null, 저장 후 5678, 실제 출발 시각은 여전히 null
     */
    @Test
    fun test_07_saveLastInferredHomeLeaveTimeThenRead() = runTest {
        /** Given **/
        assertNull(repository.getLastInferredHomeLeaveTime())

        /** When **/
        val result = repository.saveLastInferredHomeLeaveTime(5678L)

        /** Then **/
        assertTrue(result.isSuccess)
        assertEquals(5678L, repository.getLastInferredHomeLeaveTime())
        assertNull(repository.getLastHomeLeaveTime())
    }

    /**
     * 집 출발 시각 저장이 키 이름도 로그에 남기지 않는지 검증
     *
     * Given: 저장 전 상태
     * When: 실제 출발 1234, 추정 출발 5678 저장
     * Then: 두 값 저장, Log.v 메시지에 두 출발 시각 키 이름이 없음
     */
    @Test
    fun test_08_homeLeaveTimeSavesWithoutLog() = runTest {
        /** Given / When **/
        val leave = repository.saveLastHomeLeaveTime(1234L)
        val inferredLeave = repository.saveLastInferredHomeLeaveTime(5678L)

        /** Then **/
        assertTrue(leave.isSuccess && inferredLeave.isSuccess)
        assertEquals(1234L, repository.getLastHomeLeaveTime())
        assertEquals(5678L, repository.getLastInferredHomeLeaveTime())
        verify(exactly = 0) { Log.v(any(), match { it.contains("HomeLeaveTime") }) }
    }
}
