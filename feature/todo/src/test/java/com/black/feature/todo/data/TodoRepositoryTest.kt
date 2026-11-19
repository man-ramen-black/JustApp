package com.black.feature.todo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.black.feature.todo.data.database.TodoDao
import com.black.feature.todo.data.database.TodoDatabase
import com.black.feature.todo.data.database.TodoEntity
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import com.black.test.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.Instant
import java.time.LocalDate

/** [TodoRepository]의 엔티티 변환과 저장본 교체·관찰·삭제 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TodoRepositoryTest : BaseTest() {

    @MockK
    lateinit var dao: TodoDao

    private lateinit var database: TodoDatabase

    /** 메모리 DB를 쓰는 저장소 */
    private lateinit var repository: TodoRepository

    /**
     * 각 테스트 전 메모리 DB로 저장소 생성
     *
     * 테스트 간 격리용.
     */
    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.dao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun task(
        due: TodoDue?,
        pageId: String = "page-1",
        title: String = "제목",
        done: Boolean = true,
        pendingSync: Boolean = false,
        lastEditedTime: Long = 1794961000000,
        url: String = "https://www.notion.so/page-1",
    ) = Todo(
        pageId = pageId,
        title = title,
        done = done,
        due = due,
        reminder = TodoReminder.TIME,
        content = "내용",
        lastEditedTime = Instant.ofEpochMilli(lastEditedTime),
        url = url,
        pendingSync = pendingSync,
    )

    /**
     * 날짜만 있는 기한이 엔티티를 거쳐 그대로 복원되는지 검증
     *
     * Given: Date(2026-11-20) 기한의 할 일
     * When: toEntity 후 toTodo
     * Then: dueDate "2026-11-20", dueAt null이고 원본과 같음
     */
    @Test
    fun test_01_entityRoundTripKeepsDateOnly() = runTest {
        /** Given **/
        val original = task(TodoDue.Date(LocalDate.of(2026, 11, 20)), pendingSync = true)

        /** When **/
        val entity = original.toEntity()

        /** Then **/
        assertEquals("2026-11-20", entity.dueDate)
        assertNull(entity.dueAt)
        assertEquals(original, entity.toTodo())
    }

    /**
     * 시각까지 있는 기한이 엔티티를 거쳐 그대로 복원되는지 검증
     *
     * Given: DateTime(1794961800000) 기한의 할 일
     * When: toEntity 후 toTodo
     * Then: dueDate null, dueAt 1794961800000이고 원본과 같음
     */
    @Test
    fun test_02_entityRoundTripKeepsDateTime() = runTest {
        /** Given **/
        val original = task(TodoDue.DateTime(Instant.ofEpochMilli(1794961800000)))

        /** When **/
        val entity = original.toEntity()

        /** Then **/
        assertNull(entity.dueDate)
        assertEquals(1794961800000, entity.dueAt)
        assertEquals(original, entity.toTodo())
    }

    /**
     * 모르는 알림 문자열이 기본값으로 복원되는지 검증
     *
     * Given: reminder "Y"인 엔티티
     * When: toTodo
     * Then: reminder NONE
     */
    @Test
    fun test_03_unknownReminderStringFallsBack() = runTest {
        /** Given **/
        val entity = task(null).toEntity().copy(reminder = "Y")

        /** When **/
        val restored = entity.toTodo()

        /** Then **/
        assertEquals(TodoReminder.NONE, restored.reminder)
    }

    /**
     * 저장본 교체가 엔티티로 바뀌어 DAO에 위임되는지 검증
     *
     * Given: MockK DAO
     * When: replaceSynced(listOf(task))
     * Then: dao.replaceSynced(listOf(task.toEntity())) 호출
     */
    @Test
    fun test_04_replaceSyncedDelegatesToDao() = runTest {
        /** Given **/
        coEvery { dao.replaceSynced(any()) } returns Unit
        val task = task(TodoDue.Date(LocalDate.of(2026, 11, 20)))

        /** When **/
        TodoRepository(dao).replaceSynced(listOf(task))

        /** Then **/
        coVerify { dao.replaceSynced(listOf<TodoEntity>(task.toEntity())) }
    }

    /**
     * 저장본 교체가 이전 행을 모두 지우고 새 행으로 바꾸는지 검증
     *
     * Given: a·b 두 행 저장
     * When: 제목이 바뀐 b와 새 c로 replaceSynced
     * Then: 저장본은 바뀐 b와 c뿐이고 a는 없음
     */
    @Test
    fun test_05_replaceSyncedReplacesPreviousRows() = runTest {
        /** Given **/
        repository.replaceSynced(listOf(task(null, pageId = "a"), task(null, pageId = "b")))
        val replaced = listOf(task(null, pageId = "b", title = "바뀐 제목"), task(null, pageId = "c"))

        /** When **/
        repository.replaceSynced(replaced)

        /** Then **/
        assertEquals(replaced, repository.getAll().sortedBy { it.pageId })
    }

    /**
     * tasks Flow가 교체된 목록을 방출하는지 검증
     *
     * Given: a 한 행 저장, tasks 구독 중
     * When: b로 replaceSynced
     * Then: 구독 전 a, 구독자가 b만 있는 목록을 받음
     */
    @Test
    fun test_06_tasksFlowEmitsReplacedList() = runTest {
        /** Given **/
        repository.replaceSynced(listOf(task(null, pageId = "a")))
        assertEquals(listOf("a"), repository.tasks.first().map { it.pageId })
        val emitted = async(UnconfinedTestDispatcher(testScheduler)) {
            repository.tasks.first { tasks -> tasks.map { it.pageId } == listOf("b") }
        }

        /** When **/
        repository.replaceSynced(listOf(task(null, pageId = "b")))

        /** Then **/
        assertEquals(listOf(task(null, pageId = "b")), emitted.await())
    }

    /**
     * 저장본 삭제가 모든 행을 비우는지 검증
     *
     * Given: a·b 두 행 저장
     * When: clear 호출
     * Then: getAll과 tasks 모두 빈 목록
     */
    @Test
    fun test_07_clearRemovesAllRows() = runTest {
        /** Given **/
        repository.replaceSynced(listOf(task(null, pageId = "a"), task(null, pageId = "b")))

        /** When **/
        repository.clear()

        /** Then **/
        assertEquals(emptyList<Todo>(), repository.getAll())
        assertEquals(emptyList<Todo>(), repository.tasks.first())
    }

    /**
     * markDone이 완료 여부·반영 대기·수정 시각을 바꾸는지 검증
     *
     * Given: done false, 대기 아님인 a 행
     * When: markDone("a", true, 2000)
     * Then: done true, pendingSync true, lastEditedTime 2000
     */
    @Test
    fun test_08_markDoneUpdatesRow() = runTest {
        /** Given **/
        repository.add(task(null, pageId = "a", done = false, lastEditedTime = 1000))

        /** When **/
        repository.markDone("a", true, Instant.ofEpochMilli(2000))

        /** Then **/
        val row = repository.getAll().single()
        assertTrue(row.done)
        assertTrue(row.pendingSync)
        assertEquals(Instant.ofEpochMilli(2000), row.lastEditedTime)
    }

    /**
     * 없는 page ID의 markDone이 아무것도 하지 않는지 검증
     *
     * Given: a 한 행
     * When: markDone("missing", true, 2000)
     * Then: 행 수 그대로, a는 변경 없음
     */
    @Test
    fun test_09_markDoneIgnoresMissingRow() = runTest {
        /** Given **/
        val original = task(null, pageId = "a", done = false)
        repository.add(original)

        /** When **/
        repository.markDone("missing", true, Instant.ofEpochMilli(2000))

        /** Then **/
        assertEquals(listOf(original), repository.getAll())
    }

    /**
     * add한 임시 ID 행이 tasks에 나오고 임시 ID 판정이 되는지 검증
     *
     * Given: newLocalId로 만든 ID의 행
     * When: add
     * Then: tasks에 나오고 isLocalOnly true, ID는 "local-" 시작, 두 번 부르면 서로 다름
     */
    @Test
    fun test_10_addedLocalRowAppearsInTasks() = runTest {
        /** Given **/
        val localId = Todo.newLocalId()
        val local = task(null, pageId = localId)

        /** When **/
        repository.add(local)

        /** Then **/
        val emitted = repository.tasks.first().single()
        assertEquals(local, emitted)
        assertTrue(emitted.isLocalOnly)
        assertTrue(localId.startsWith("local-"))
        assertNotEquals(localId, Todo.newLocalId())
        assertFalse(task(null, pageId = "page-1").isLocalOnly)
    }

    /**
     * getPending이 반영 대기 행만 돌려주는지 검증
     *
     * Given: 대기 a, 일반 b
     * When: getPending
     * Then: a뿐
     */
    @Test
    fun test_11_getPendingReturnsOnlyPendingRows() = runTest {
        /** Given **/
        val pending = task(null, pageId = "a", pendingSync = true)
        repository.add(pending)
        repository.add(task(null, pageId = "b"))

        /** When **/
        val result = repository.getPending()

        /** Then **/
        assertEquals(listOf(pending), result)
    }

    /**
     * replaceSynced가 반영 대기 행을 남기고 나머지만 교체하는지 검증
     *
     * Given: 대기 a(done true), 일반 b
     * When: replaceSynced([a(done false), c])
     * Then: a(done true, 대기)와 c, b는 없음
     */
    @Test
    fun test_12_replaceSyncedKeepsPendingRows() = runTest {
        /** Given **/
        val pendingA = task(null, pageId = "a", done = true, pendingSync = true)
        repository.add(pendingA)
        repository.add(task(null, pageId = "b"))
        val synced = listOf(task(null, pageId = "a", done = false), task(null, pageId = "c", done = false))

        /** When **/
        repository.replaceSynced(synced)

        /** Then **/
        assertEquals(listOf(pendingA, synced[1]), repository.getAll().sortedBy { it.pageId })
    }

    /**
     * 보낸 뒤 그대로인 행이 확정본으로 교체되는지 검증
     *
     * Given: 대기 임시 행 snapshot
     * When: resolvePending(snapshot, resolved)
     * Then: 임시 행은 없고 resolved가 대기 해제로 저장
     */
    @Test
    fun test_13_resolvePendingReplacesUnchangedRow() = runTest {
        /** Given **/
        val snapshot = task(null, pageId = "local-1", pendingSync = true)
        val resolved = task(null, pageId = "real-1", pendingSync = true, url = "https://www.notion.so/real-1")
        repository.add(snapshot)

        /** When **/
        repository.resolvePending(snapshot, resolved)

        /** Then **/
        assertEquals(listOf(resolved.copy(pendingSync = false)), repository.getAll())
    }

    /**
     * 보낸 뒤 그대로이고 resolved가 없으면 행이 지워지는지 검증
     *
     * Given: 대기 행 snapshot
     * When: resolvePending(snapshot, null)
     * Then: 저장본이 빔
     */
    @Test
    fun test_14_resolvePendingDeletesRowWithoutResolved() = runTest {
        /** Given **/
        val snapshot = task(null, pageId = "a", pendingSync = true)
        repository.add(snapshot)

        /** When **/
        repository.resolvePending(snapshot, null)

        /** Then **/
        assertEquals(emptyList<Todo>(), repository.getAll())
    }

    /**
     * 보내는 사이 다시 바뀐 일반 행이 그대로인지 검증
     *
     * Given: snapshot 이후 done이 다시 바뀐 대기 일반 행
     * When: resolvePending(snapshot, resolved)
     * Then: 행 변경 없음
     */
    @Test
    fun test_15_resolvePendingKeepsChangedRow() = runTest {
        /** Given **/
        val snapshot = task(null, pageId = "a", done = true, pendingSync = true, lastEditedTime = 1000)
        val changed = snapshot.copy(done = false, lastEditedTime = Instant.ofEpochMilli(2000))
        repository.add(changed)

        /** When **/
        repository.resolvePending(snapshot, task(null, pageId = "a", done = true))

        /** Then **/
        assertEquals(listOf(changed), repository.getAll())
    }

    /**
     * 보내는 사이 다시 바뀐 임시 행이 page ID·URL만 바뀌고 대기를 유지하는지 검증
     *
     * Given: snapshot 이후 done이 다시 바뀐 대기 임시 행
     * When: resolvePending(snapshot, resolved)
     * Then: 임시 행은 resolved의 page ID·URL로 바뀌고 done·수정 시각·대기는 그대로
     */
    @Test
    fun test_16_resolvePendingRekeysChangedLocalRow() = runTest {
        /** Given **/
        val snapshot = task(null, pageId = "local-1", done = true, pendingSync = true, lastEditedTime = 1000)
        val changed = snapshot.copy(done = false, lastEditedTime = Instant.ofEpochMilli(2000))
        val resolved = task(null, pageId = "real-1", url = "https://www.notion.so/real-1")
        repository.add(changed)

        /** When **/
        repository.resolvePending(snapshot, resolved)

        /** Then **/
        assertEquals(listOf(changed.copy(pageId = "real-1", url = "https://www.notion.so/real-1")), repository.getAll())
    }

    /**
     * 행이 없으면 resolvePending이 아무것도 넣지 않는지 검증
     *
     * Given: 빈 저장본
     * When: resolvePending(snapshot, resolved)
     * Then: 저장본이 빈 채
     */
    @Test
    fun test_17_resolvePendingDoesNothingWithoutRow() = runTest {
        /** Given **/
        val snapshot = task(null, pageId = "local-1", pendingSync = true)

        /** When **/
        repository.resolvePending(snapshot, task(null, pageId = "real-1"))

        /** Then **/
        assertEquals(emptyList<Todo>(), repository.getAll())
    }

    /**
     * getById가 page ID가 같은 행을 돌려주는지 검증
     *
     * Given: a·b 두 행
     * When: getById("b")
     * Then: b
     */
    @Test
    fun test_18_getByIdReturnsMatchingRow() = runTest {
        /** Given **/
        val target = task(TodoDue.DateTime(Instant.ofEpochMilli(1794961800000)), pageId = "b", done = false)
        repository.add(task(null, pageId = "a"))
        repository.add(target)

        /** When **/
        val result = repository.getById("b")

        /** Then **/
        assertEquals(target, result)
    }

    /**
     * 없는 page ID면 getById가 null인지 검증
     *
     * Given: a 한 행
     * When: getById("missing")
     * Then: null
     */
    @Test
    fun test_19_getByIdReturnsNullForMissingRow() = runTest {
        /** Given **/
        repository.add(task(null, pageId = "a"))

        /** When **/
        val result = repository.getById("missing")

        /** Then **/
        assertNull(result)
    }

    /**
     * 해석할 수 없는 기한 날짜가 저장된 행이 기한 없음으로 복원되는지 검증
     *
     * Given: dueDate가 "내일", "2026-02-30"(없는 날짜), "2026-1-20"(한 자리 월)인 세 행
     * When: getAll
     * Then: 세 행 모두 기한 null이고 다른 값은 그대로
     */
    @Test
    fun test_20_unparsableDueDateRestoresWithoutDue() = runTest {
        /** Given **/
        val stored = listOf("내일", "2026-02-30", "2026-1-20").mapIndexed { index, dueDate ->
            task(null, pageId = "page-$index").toEntity().copy(dueDate = dueDate)
        }
        database.dao().insertAll(stored)

        /** When **/
        val restored = repository.getAll().sortedBy { it.pageId }

        /** Then **/
        assertEquals(listOf<TodoDue?>(null, null, null), restored.map { it.due })
        assertEquals(listOf("page-0", "page-1", "page-2").map { pageId -> task(null, pageId = pageId) }, restored)
    }
}
