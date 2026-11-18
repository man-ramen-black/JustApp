package com.black.app.model

import android.content.Context
import com.black.app.assistanttask.AssistantTask
import com.black.app.assistanttask.AssistantTaskDue
import com.black.app.assistanttask.AssistantTaskKind
import com.black.app.assistanttask.AssistantTaskReminder
import com.black.app.model.database.assistanttask.AssistantTaskDao
import com.black.app.model.database.assistanttask.AssistantTaskDatabase
import com.black.app.model.database.assistanttask.AssistantTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 비서 할 일 저장본의 조회와 행 단위 변경, 동기화 결과 교체, 삭제 */
class AssistantTaskRepository(private val dao: AssistantTaskDao) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(AssistantTaskDatabase.getInstance(context).dao())

    val tasks: Flow<List<AssistantTask>> get() = dao.observeAll().map { entities -> entities.map { it.toTask() } }

    suspend fun getAll(): List<AssistantTask> = dao.getAll().map { it.toTask() }

    /** [pageId] 행, 없으면 null */
    suspend fun getById(pageId: String): AssistantTask? = dao.getById(pageId)?.toTask()

    /** 반영 대기 행 */
    suspend fun getPending(): List<AssistantTask> = dao.getPending().map { it.toTask() }

    /** 행 하나 추가 */
    suspend fun add(task: AssistantTask) {
        dao.insertAll(listOf(task.toEntity()))
    }

    /**
     * [pageId] 행의 완료 여부를 [done]으로 바꾸고 반영 대기로 표시
     *
     * [changedAt]을 수정 시각으로 적으며, 행이 없으면 아무것도 하지 않음.
     */
    suspend fun markDone(pageId: String, done: Boolean, changedAt: Long) {
        dao.updateDone(pageId, done, changedAt)
    }

    /**
     * 노션으로 보낸 [snapshot]의 결과 [resolved]를 반영
     *
     * 보내는 사이 행이 다시 바뀌었는지에 따른 갈래는 [AssistantTaskDao.resolvePending] 참고.
     */
    suspend fun resolvePending(snapshot: AssistantTask, resolved: AssistantTask?) {
        dao.resolvePending(snapshot.toEntity(), resolved?.toEntity(), snapshot.isLocalOnly)
    }

    /** 반영 대기 행은 남기고 나머지 저장본을 [synced]로 교체하며 한 트랜잭션으로 처리 */
    suspend fun replaceSynced(synced: List<AssistantTask>) {
        dao.replaceSynced(synced.map { it.toEntity() })
    }

    /** 저장본 전체 삭제 */
    suspend fun clear() {
        dao.deleteAll()
    }
}

internal fun AssistantTask.toEntity() = AssistantTaskEntity(
    pageId = pageId,
    title = title,
    kind = kind.name,
    done = done,
    dueDate = (due as? AssistantTaskDue.DateOnly)?.date,
    dueAt = (due as? AssistantTaskDue.DateTime)?.epochMillis,
    reminder = reminder.name,
    content = content,
    lastEditedTime = lastEditedTime,
    url = url,
    pendingSync = pendingSync,
)

/** 모르는 [AssistantTaskEntity.kind]·[AssistantTaskEntity.reminder] 문자열은 각각 TASK·NONE으로 복원 */
internal fun AssistantTaskEntity.toTask() = AssistantTask(
    pageId = pageId,
    title = title,
    kind = AssistantTaskKind.entries.firstOrNull { it.name == kind } ?: AssistantTaskKind.TASK,
    done = done,
    due = when {
        dueDate != null -> AssistantTaskDue.DateOnly(dueDate)
        dueAt != null -> AssistantTaskDue.DateTime(dueAt)
        else -> null
    },
    reminder = AssistantTaskReminder.entries.firstOrNull { it.name == reminder } ?: AssistantTaskReminder.NONE,
    content = content,
    lastEditedTime = lastEditedTime,
    url = url,
    pendingSync = pendingSync,
)
