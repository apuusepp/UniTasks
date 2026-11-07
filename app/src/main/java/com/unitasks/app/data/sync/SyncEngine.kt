package com.unitasks.app.data.sync

import com.unitasks.app.data.ReminderScheduler
import com.unitasks.app.data.Settings
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.data.local.TaskEntity
import com.unitasks.app.data.local.TaskListEntity
import com.unitasks.app.data.local.toDomain
import com.unitasks.app.data.remote.AuthRequiredException
import com.unitasks.app.data.remote.TaskBody
import com.unitasks.app.data.remote.TaskDto
import com.unitasks.app.data.remote.TaskListBody
import com.unitasks.app.data.remote.TaskListDto
import com.unitasks.app.data.remote.TasksApi
import com.unitasks.app.domain.RecurrenceRule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface SyncResult {
    data object Success : SyncResult
    data object AuthRequired : SyncResult
    data class Failure(val error: Throwable, val retryable: Boolean) : SyncResult
}

/**
 * Two-way sync between the local Room database and the Google Tasks REST API.
 *
 * Order: pull lists → push lists → push tasks (deletes, creates, patches, moves) → pull tasks.
 * Locally modified rows win conflicts; local-only fields (star, due time, repeat rule) are never
 * overwritten by the server.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val settings: Settings,
    private val api: TasksApi,
    private val reminders: ReminderScheduler = ReminderScheduler.NoOp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val listDao = db.taskListDao()
    private val taskDao = db.taskDao()
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        try {
            pullLists()
            pushLists()
            pushTasks()
            pullTasks()
            settings.markSynced(clock())
            taskDao.getActiveWithDueDate().forEach { reminders.schedule(it.toDomain()) }
            SyncResult.Success
        } catch (e: AuthRequiredException) {
            SyncResult.AuthRequired
        } catch (e: HttpException) {
            if (e.code() == 401) SyncResult.AuthRequired else SyncResult.Failure(e, retryable = e.code() >= 500 || e.code() == 429)
        } catch (e: IOException) {
            SyncResult.Failure(e, retryable = true)
        }
    }

    // ------------------------------------------------------------------ lists

    private suspend fun pullLists() {
        val remote = mutableListOf<TaskListDto>()
        var page: String? = null
        do {
            val response = api.listTaskLists(pageToken = page)
            remote += response.items
            page = response.nextPageToken
        } while (page != null)

        val locals = listDao.getAll()
        remote.forEachIndexed { index, dto ->
            var local = locals.firstOrNull { it.remoteId == dto.id }
            // First sign-in after using the app locally: adopt the server's default list
            // instead of uploading a duplicate "My Tasks".
            if (local == null && index == 0) {
                local = locals.firstOrNull { it.id == settings.defaultListId && it.remoteId == null }
                    ?.also { listDao.update(it.copy(remoteId = dto.id, isDirty = true)) }
                    ?.copy(remoteId = dto.id, isDirty = true)
            }
            when {
                local == null -> listDao.insert(
                    TaskListEntity(id = idGenerator(), remoteId = dto.id, title = dto.title, sortIndex = index, updatedAt = clock()),
                )
                local.isDirty || local.pendingDelete -> listDao.update(local.copy(sortIndex = index))
                else -> listDao.update(local.copy(title = dto.title, sortIndex = index))
            }
        }
        remote.firstOrNull()?.let { listDao.getByRemoteId(it.id) }?.let { settings.defaultListId = it.id }
        listDao.getAll().filter { it.remoteId == null && !it.pendingDelete }.forEachIndexed { offset, local ->
            listDao.update(local.copy(sortIndex = remote.size + offset))
        }

        val remoteIds = remote.mapTo(HashSet()) { it.id }
        listDao.getAll().filter { it.remoteId != null && it.remoteId !in remoteIds }.forEach { gone ->
            taskDao.getForList(gone.id).forEach { reminders.cancel(it.id) }
            listDao.deleteById(gone.id)
            settings.setListSyncToken(gone.remoteId!!, null)
        }
    }

    private suspend fun pushLists() {
        for (list in listDao.getAll()) {
            when {
                list.pendingDelete -> {
                    list.remoteId?.let { rid ->
                        api.deleteTaskList(rid).requireSuccessOrGone()
                        settings.setListSyncToken(rid, null)
                    }
                    listDao.deleteById(list.id)
                }
                list.remoteId == null -> {
                    val dto = api.insertTaskList(TaskListBody(list.title))
                    val current = listDao.getById(list.id) ?: continue
                    listDao.update(current.copy(remoteId = dto.id, isDirty = current.title != list.title))
                }
                list.isDirty -> {
                    api.patchTaskList(list.remoteId, TaskListBody(list.title))
                    val current = listDao.getById(list.id) ?: continue
                    listDao.update(current.copy(isDirty = current.title != list.title))
                }
            }
        }
    }

    // ------------------------------------------------------------------ tasks

    private suspend fun remoteListId(localListId: String): String? = listDao.getById(localListId)?.remoteId

    private suspend fun pushTasks() {
        // 1. Deletes
        for (task in taskDao.getPendingDeletes()) {
            val listRemote = remoteListId(task.listId)
            if (task.remoteId != null && listRemote != null) {
                api.deleteTask(listRemote, task.remoteId).requireSuccessOrGone()
            }
            taskDao.delete(task)
        }

        // 2. Creates – parents before children, top to bottom so `previous` already exists.
        val created = taskDao.getUnsyncedNew().sortedWith(compareBy<TaskEntity>({ it.parentId != null }, { it.position }))
        for (snapshot in created) {
            val task = taskDao.getById(snapshot.id) ?: continue
            val listRemote = remoteListId(task.listId) ?: continue
            val parentRemote = task.parentId?.let { taskDao.getById(it)?.remoteId }
            if (task.parentId != null && parentRemote == null) continue // parent not synced yet; next round
            val dto = api.insertTask(listRemote, task.toBody(), parent = parentRemote, previous = previousSiblingRemoteId(task))
            markPushed(task, dto, clearMove = true)
        }

        // 3. Content updates
        for (task in taskDao.getDirty()) {
            val listRemote = remoteListId(task.listId) ?: continue
            val dto = try {
                api.patchTask(listRemote, task.remoteId!!, task.toBody())
            } catch (e: HttpException) {
                if (e.code() == 404) { taskDao.delete(task); continue } else throw e
            }
            markPushed(task, dto, clearMove = false)
        }

        // 4. Moves (indent / outdent / reorder)
        for (snapshot in taskDao.getNeedsMove().sortedWith(compareBy<TaskEntity>({ it.parentId != null }, { it.position }))) {
            val task = taskDao.getById(snapshot.id) ?: continue
            val listRemote = remoteListId(task.listId) ?: continue
            val parentRemote = task.parentId?.let { taskDao.getById(it)?.remoteId }
            if (task.parentId != null && parentRemote == null) continue
            val dto = try {
                api.moveTask(listRemote, task.remoteId!!, parent = parentRemote, previous = previousSiblingRemoteId(task))
            } catch (e: HttpException) {
                if (e.code() == 404) { taskDao.delete(task); continue } else throw e
            }
            val current = taskDao.getById(task.id) ?: continue
            taskDao.update(
                current.copy(
                    remotePosition = dto.position ?: current.remotePosition,
                    needsMove = current.position != task.position || current.parentId != task.parentId,
                ),
            )
        }
    }

    /** Clears dirty flags unless the row was edited again while the request was in flight. */
    private suspend fun markPushed(sent: TaskEntity, dto: TaskDto, clearMove: Boolean) {
        val current = taskDao.getById(sent.id) ?: return
        val unchanged = current.updatedAt == sent.updatedAt
        taskDao.update(
            current.copy(
                remoteId = dto.id,
                remotePosition = dto.position ?: current.remotePosition,
                isDirty = if (unchanged) false else current.isDirty || current.toBody() != sent.toBody(),
                needsMove = if (clearMove) current.position != sent.position || current.parentId != sent.parentId else current.needsMove,
            ),
        )
    }

    private suspend fun previousSiblingRemoteId(task: TaskEntity): String? =
        taskDao.getForList(task.listId)
            .filter { it.parentId == task.parentId && it.id != task.id && !it.pendingDelete && it.remoteId != null && it.position < task.position }
            .maxByOrNull { it.position }
            ?.remoteId

    private suspend fun pullTasks() {
        for (list in listDao.getAll()) {
            val listRemote = list.remoteId ?: continue
            if (list.pendingDelete) continue
            val token = settings.getListSyncToken(listRemote)
            val startedAt = clock()
            val items = mutableListOf<TaskDto>()
            var page: String? = null
            do {
                val response = api.listTasks(listRemote, updatedMin = token, pageToken = page)
                items += response.items
                page = response.nextPageToken
            } while (page != null)
            applyRemoteTasks(list, items, fullSync = token == null)
            // Watermark with a safety margin for clock skew; re-applying a task is idempotent.
            settings.setListSyncToken(listRemote, Instant.ofEpochMilli(startedAt - SKEW_MARGIN_MS).toString())
        }
    }

    private suspend fun applyRemoteTasks(list: TaskListEntity, items: List<TaskDto>, fullSync: Boolean) {
        val now = clock()
        for (dto in items) {
            val local = taskDao.getByRemoteId(dto.id)
            if (dto.deleted == true) {
                if (local != null) {
                    reminders.cancel(local.id)
                    taskDao.delete(local)
                }
                continue
            }
            when {
                local == null -> taskDao.insert(
                    TaskEntity(id = idGenerator(), remoteId = dto.id, listId = list.id, title = "", createdAt = now).merge(dto, list.id),
                )
                local.pendingDelete -> Unit
                local.isDirty || local.needsMove -> taskDao.update(local.copy(remotePosition = dto.position ?: local.remotePosition))
                else -> {
                    val merged = local.merge(dto, list.id)
                    if (!local.completed && merged.completed) spawnNextOccurrence(local)
                    if (merged != local) taskDao.update(merged)
                }
            }
        }
        // Resolve parents once all rows exist locally.
        for (dto in items) {
            if (dto.deleted == true) continue
            val local = taskDao.getByRemoteId(dto.id) ?: continue
            if (local.needsMove || local.pendingDelete) continue
            val parentLocalId = dto.parent?.let { taskDao.getByRemoteId(it)?.id }
            if (local.parentId != parentLocalId) taskDao.update(local.copy(parentId = parentLocalId))
        }
        if (fullSync) {
            val remoteIds = items.filter { it.deleted != true }.mapTo(HashSet()) { it.id }
            taskDao.getForList(list.id)
                .filter { it.remoteId != null && it.remoteId !in remoteIds && !it.pendingDelete && !it.isDirty }
                .forEach { reminders.cancel(it.id); taskDao.delete(it) }
        }
        renumberPositions(list.id)
    }

    /** Re-derives local "My order" positions from server positions for each sibling group. */
    private suspend fun renumberPositions(listId: String) {
        val tasks = taskDao.getForList(listId).filter { !it.pendingDelete }
        tasks.groupBy { it.parentId }.values.forEach { group ->
            if (group.any { it.needsMove }) return@forEach
            val ordered = group.sortedWith(
                compareBy<TaskEntity>({ it.remotePosition != null }, { it.remotePosition ?: "" }, { it.position }),
            )
            val updates = ordered.mapIndexedNotNull { index, e ->
                val pos = index.toDouble()
                if (e.position != pos) e.copy(position = pos) else null
            }
            if (updates.isNotEmpty()) taskDao.updateAll(updates)
        }
    }

    /** A repeating task was completed in another client: create its next occurrence locally. */
    private suspend fun spawnNextOccurrence(task: TaskEntity) {
        val rule = RecurrenceRule.decode(task.recurrence) ?: return
        val due = task.dueDate?.let(LocalDate::ofEpochDay) ?: return
        val next = rule.next(due) ?: return
        val now = clock()
        val top = taskDao.getForList(task.listId).filter { it.parentId == null && !it.completed }.minOfOrNull { it.position } ?: 0.0
        taskDao.insert(
            task.copy(
                id = idGenerator(), remoteId = null, remotePosition = null, parentId = null,
                completed = false, completedAt = null, dueDate = next.date.toEpochDay(), recurrence = next.rule.encode(),
                position = top - 1.0, createdAt = now, updatedAt = now, isDirty = false, needsMove = false, pendingDelete = false,
            ),
        )
    }

    companion object {
        const val SKEW_MARGIN_MS = 5 * 60 * 1000L
        const val STATUS_COMPLETED = "completed"
        const val STATUS_NEEDS_ACTION = "needsAction"

        fun formatDue(epochDay: Long): String = "${LocalDate.ofEpochDay(epochDay)}T00:00:00.000Z"
        fun parseDue(value: String?): LocalDate? = value?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
        fun parseInstant(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}

private fun Response<*>.requireSuccessOrGone() {
    if (!isSuccessful && code() != 404 && code() != 410) throw HttpException(this)
}

internal fun TaskEntity.toBody() = TaskBody(
    title = title,
    notes = notes.ifEmpty { null },
    status = if (completed) SyncEngine.STATUS_COMPLETED else SyncEngine.STATUS_NEEDS_ACTION,
    due = dueDate?.let(SyncEngine::formatDue),
    completed = if (completed) Instant.ofEpochMilli(completedAt ?: updatedAt).toString() else null,
)

/** Applies server fields onto a local row, keeping local-only fields. */
internal fun TaskEntity.merge(dto: TaskDto, listId: String): TaskEntity {
    val due = SyncEngine.parseDue(dto.due)?.toEpochDay()
    val completedNow = dto.status == SyncEngine.STATUS_COMPLETED
    val link = dto.links?.firstOrNull { !it.link.isNullOrBlank() }
    return copy(
        listId = listId,
        title = dto.title.orEmpty(),
        notes = dto.notes.orEmpty(),
        completed = completedNow,
        completedAt = if (completedNow) SyncEngine.parseInstant(dto.completed) ?: completedAt else null,
        dueDate = due,
        dueTimeMinutes = if (due != null && due == dueDate) dueTimeMinutes else null,
        recurrence = if (due == null || completedNow) null else recurrence,
        remotePosition = dto.position ?: remotePosition,
        linkUrl = link?.link,
        linkTitle = link?.description,
        updatedAt = SyncEngine.parseInstant(dto.updated) ?: updatedAt,
    )
}
