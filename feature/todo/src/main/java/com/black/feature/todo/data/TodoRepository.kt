package com.black.feature.todo.data

import android.content.Context
import com.black.feature.todo.data.database.TodoDao
import com.black.feature.todo.data.database.TodoDatabase
import com.black.feature.todo.data.database.TodoEntity
import com.black.feature.todo.model.Todo
import com.black.feature.todo.model.TodoDue
import com.black.feature.todo.model.TodoReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Todo 저장본의 조회와 행 단위 변경, 동기화 결과 교체, 삭제 */
class TodoRepository(private val dao: TodoDao) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(TodoDatabase.getInstance(context).dao())

    val tasks: Flow<List<Todo>> get() = dao.observeAll().map { entities -> entities.map { it.toTodo() } }

    suspend fun getAll(): List<Todo> = dao.getAll().map { it.toTodo() }

    /** [pageId] 행, 없으면 null */
    suspend fun getById(pageId: String): Todo? = dao.getById(pageId)?.toTodo()

    /** 반영 대기 행 */
    suspend fun getPending(): List<Todo> = dao.getPending().map { it.toTodo() }

    /** 행 하나 추가 */
    suspend fun add(task: Todo) {
        dao.insertAll(listOf(task.toEntity()))
    }

    /**
     * [pageId] 행의 완료 여부를 [done]으로 바꾸고 반영 대기로 표시
     *
     * [changedAt]을 수정 시각으로 적으며, 행이 없으면 아무것도 하지 않음.
     */
    suspend fun markDone(pageId: String, done: Boolean, changedAt: Instant) {
        dao.updateDone(pageId, done, changedAt.toEpochMilli())
    }

    /**
     * 노션으로 보낸 [snapshot]의 결과 [resolved]를 반영
     *
     * 보내는 사이 행이 다시 바뀌었는지에 따른 갈래는 [TodoDao.resolvePending] 참고.
     */
    suspend fun resolvePending(snapshot: Todo, resolved: Todo?) {
        dao.resolvePending(snapshot.toEntity(), resolved?.toEntity(), snapshot.isLocalOnly)
    }

    /** 반영 대기 행은 남기고 나머지 저장본을 [synced]로 교체하며 한 트랜잭션으로 처리 */
    suspend fun replaceSynced(synced: List<Todo>) {
        dao.replaceSynced(synced.map { it.toEntity() })
    }

    /** 저장본 전체 삭제 */
    suspend fun clear() {
        dao.deleteAll()
    }
}

internal fun Todo.toEntity() = TodoEntity(
    pageId = pageId,
    title = title,
    done = done,
    dueDate = (due as? TodoDue.Date)?.date?.format(DateTimeFormatter.ISO_LOCAL_DATE),
    dueAt = (due as? TodoDue.DateTime)?.instant?.toEpochMilli(),
    reminder = reminder.name,
    content = content,
    lastEditedTime = lastEditedTime.toEpochMilli(),
    url = url,
    pendingSync = pendingSync,
)

/** 모르는 [TodoEntity.reminder] 문자열은 NONE으로, 해석할 수 없는 [TodoEntity.dueDate]는 기한 없음으로 복원 */
internal fun TodoEntity.toTodo() = Todo(
    pageId = pageId,
    title = title,
    done = done,
    due = when {
        dueDate != null -> parseDateOrNull(dueDate)?.let { TodoDue.Date(it) }
        dueAt != null -> TodoDue.DateTime(Instant.ofEpochMilli(dueAt))
        else -> null
    },
    reminder = TodoReminder.entries.firstOrNull { it.name == reminder } ?: TodoReminder.NONE,
    content = content,
    lastEditedTime = Instant.ofEpochMilli(lastEditedTime),
    url = url,
    pendingSync = pendingSync,
)

/** `yyyy-MM-dd` 문자열 [text]의 날짜이며, 형식이 틀렸거나 없는 날짜면 null */
private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
} catch (exception: DateTimeParseException) {
    null
}
