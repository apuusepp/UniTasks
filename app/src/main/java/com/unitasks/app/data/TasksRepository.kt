package com.unitasks.app.data

import androidx.room.withTransaction
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.data.local.TaskEntity
import com.unitasks.app.data.local.TaskListEntity
import com.unitasks.app.data.local.toDomain
import com.unitasks.app.data.local.toMinutes
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Asks the sync layer to push local changes soon. */
fun interface SyncRequester {
    fun requestSync()
}

/** Schedules / cancels due-date reminder notifications. */
interface ReminderScheduler {
    fun schedule(task: Task)
    fun cancel(taskId: String)

    object NoOp : ReminderScheduler {
        override fun schedule(task: Task) = Unit
        override fun cancel(taskId: String) = Unit
    }
}

/** Info needed to undo a completion (incl. the spawned next occurrence of a repeating task). */
data class CompletionResult(
    val taskId: String,
    val completedChildIds: List<String>,
    val previousRecurrence: String?,
    val spawnedTaskId: String?,
)

/**
 * Single source of truth for the UI. All writes go to Room first (offline-first) and flag
 * rows for the [com.unitasks.app.data.sync.SyncEngine].
 */
class TasksRepository(
    private val db: AppDatabase,
    private val settings: Settings,
    private val reminders: ReminderScheduler = ReminderScheduler.NoOp,
    private val syncRequester: SyncRequester = SyncRequester {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val listDao = db.taskListDao()
    private val taskDao = db.taskDao()

    // ---------------------------------------------------------------- observe

    fun observeLists(): Flow<List<TaskList>> = listDao.observeLists().map { lists ->
        val defaultId = settings.defaultListId ?: lists.firstOrNull()?.id
        lists.map { it.toDomain(isDefault = it.id == defaultId) }
    }

    fun observeTasks(): Flow<List<Task>> = taskDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Tasks whose list still exists (filters rows of lists pending deletion). */
    fun observeVisibleTasks(): Flow<List<Task>> = combine(observeLists(), observeTasks()) { lists, tasks ->
        val ids = lists.mapTo(HashSet()) { it.id }
        tasks.filter { it.listId in ids }
    }

    fun observeTask(id: String): Flow<Task?> = taskDao.observeById(id).map { it?.takeUnless { e -> e.pendingDelete }?.toDomain() }

    fun observeSubtasks(parentId: String): Flow<List<Task>> =
        taskDao.observeChildren(parentId).map { rows -> rows.map { it.toDomain() }.sortedBy { it.position } }

    suspend fun getTask(id: String): Task? = taskDao.getById(id)?.takeUnless { it.pendingDelete }?.toDomain()

    suspend fun getLists(): List<TaskList> {
        val lists = listDao.getAll().filter { !it.pendingDelete }
        val defaultId = settings.defaultListId ?: lists.firstOrNull()?.id
        return lists.map { it.toDomain(isDefault = it.id == defaultId) }
    }

    suspend fun getTasks(): List<Task> =
        taskDao.getAll().filter { !it.pendingDelete }.map { it.toDomain() }

    // ---------------------------------------------------------------- lists

    /** Local mode only: make sure there is a default "My Tasks" list. */
    suspend fun ensureDefaultList(): String {
        val existing = listDao.getAll().filter { !it.pendingDelete }
        settings.defaultListId?.let { id -> if (existing.any { it.id == id }) return id }
        val first = existing.firstOrNull()
        if (first != null) {
            settings.defaultListId = first.id
            return first.id
        }
        val id = createList("My Tasks")
        settings.defaultListId = id
        return id
    }

    suspend fun createList(title: String): String {
        val id = idGenerator()
        listDao.insert(
            TaskListEntity(
                id = id,
                title = title.trim().ifEmpty { "Untitled list" },
                sortIndex = listDao.maxSortIndex() + 1,
                updatedAt = clock(),
            ),
        )
        syncRequester.requestSync()
        return id
    }

    suspend fun renameList(id: String, title: String) {
        val list = listDao.getById(id) ?: return
        val newTitle = title.trim()
        if (newTitle.isEmpty() || newTitle == list.title) return
        listDao.update(list.copy(title = newTitle, isDirty = true, updatedAt = clock()))
        syncRequester.requestSync()
    }

    /** Deletes a list and its tasks. The default list can't be deleted (same as Google Tasks). */
    suspend fun deleteList(id: String): Boolean {
        val list = listDao.getById(id) ?: return false
        val defaultId = settings.defaultListId ?: listDao.getAll().firstOrNull { !it.pendingDelete }?.id
        if (id == defaultId) return false
        val tasks = taskDao.getForList(id)
        db.withTransaction {
            if (list.remoteId == null) {
                listDao.deleteById(id) // cascades to tasks
            } else {
                taskDao.deleteByIds(tasks.map { it.id })
                listDao.update(list.copy(pendingDelete = true))
            }
        }
        tasks.forEach { reminders.cancel(it.id) }
        settings.setListHidden(id, false)
        syncRequester.requestSync()
        return true
    }

    suspend fun setListSort(id: String, sortMode: SortMode) {
        val list = listDao.getById(id) ?: return
        listDao.update(list.copy(sortMode = sortMode.name))
    }

    // ---------------------------------------------------------------- tasks

    suspend fun addTask(
        listId: String,
        title: String,
        notes: String = "",
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        starred: Boolean = false,
        parentId: String? = null,
        recurrence: RecurrenceRule? = null,
    ): String {
        val now = clock()
        val id = idGenerator()
        val siblings = activeSiblings(listId, parentId)
        // New top-level tasks go to the top (like Google Tasks); new subtasks go to the bottom.
        val position = when {
            siblings.isEmpty() -> 0.0
            parentId == null -> siblings.minOf { it.position } - 1.0
            else -> siblings.maxOf { it.position } + 1.0
        }
        val entity = TaskEntity(
            id = id,
            listId = listId,
            parentId = parentId,
            title = title.trim(),
            notes = notes.trim(),
            dueDate = dueDate?.toEpochDay(),
            dueTimeMinutes = dueDate?.let { dueTime?.toMinutes() },
            starred = starred,
            starredAt = if (starred) now else null,
            recurrence = if (dueDate != null) recurrence?.encode() else null,
            position = position,
            createdAt = now,
            updatedAt = now,
        )
        taskDao.insert(entity)
        reminders.schedule(entity.toDomain())
        syncRequester.requestSync()
        return id
    }

    /** Saves edits to title, notes, due date/time and repeat rule. */
    suspend fun updateTask(
        id: String,
        title: String,
        notes: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        recurrence: RecurrenceRule?,
    ) {
        val current = taskDao.getById(id) ?: return
        val updated = current.copy(
            title = title.trim(),
            notes = notes.trim(),
            dueDate = dueDate?.toEpochDay(),
            dueTimeMinutes = dueDate?.let { dueTime?.toMinutes() },
            recurrence = if (dueDate != null) recurrence?.encode() else null,
        )
        if (updated == current) return
        val remoteChanged = updated.title != current.title || updated.notes != current.notes || updated.dueDate != current.dueDate
        val saved = updated.copy(updatedAt = clock(), isDirty = current.isDirty || remoteChanged)
        taskDao.update(saved)
        reminders.cancel(id)
        reminders.schedule(saved.toDomain())
        if (remoteChanged) syncRequester.requestSync()
    }

    suspend fun setCompleted(id: String, completed: Boolean): CompletionResult? {
        val task = taskDao.getById(id) ?: return null
        if (task.completed == completed) return null
        val now = clock()
        return if (completed) {
            var spawnedId: String? = null
            val children = taskDao.getChildren(id).filter { !it.completed && !it.pendingDelete }
            db.withTransaction {
                taskDao.update(
                    task.copy(completed = true, completedAt = now, recurrence = null, isDirty = true, updatedAt = now),
                )
                taskDao.updateAll(children.map { it.copy(completed = true, completedAt = now, isDirty = true, updatedAt = now) })
                val rule = RecurrenceRule.decode(task.recurrence)
                val due = task.dueDate?.let(LocalDate::ofEpochDay)
                val next = if (rule != null && due != null) rule.next(due) else null
                if (next != null) {
                    spawnedId = addTask(
                        listId = task.listId,
                        title = task.title,
                        notes = task.notes,
                        dueDate = next.date,
                        dueTime = task.dueTimeMinutes?.let { LocalTime.of(it / 60, it % 60) },
                        starred = task.starred,
                        recurrence = next.rule,
                    )
                }
            }
            reminders.cancel(id)
            children.forEach { reminders.cancel(it.id) }
            syncRequester.requestSync()
            CompletionResult(id, children.map { it.id }, task.recurrence, spawnedId)
        } else {
            db.withTransaction {
                taskDao.update(task.copy(completed = false, completedAt = null, isDirty = true, updatedAt = now))
                // Un-completing a subtask re-opens its parent so the subtask is visible again.
                task.parentId?.let { pid ->
                    taskDao.getById(pid)?.takeIf { it.completed }?.let { parent ->
                        taskDao.update(parent.copy(completed = false, completedAt = null, isDirty = true, updatedAt = now))
                    }
                }
            }
            taskDao.getById(id)?.let { reminders.schedule(it.toDomain()) }
            syncRequester.requestSync()
            CompletionResult(id, emptyList(), null, null)
        }
    }

    /** Reverts [setCompleted] (used by the "Undo" snackbar). */
    suspend fun undoCompletion(result: CompletionResult) {
        val now = clock()
        db.withTransaction {
            (listOf(result.taskId) + result.completedChildIds).forEach { tid ->
                taskDao.getById(tid)?.let {
                    taskDao.update(it.copy(completed = false, completedAt = null, isDirty = true, updatedAt = now))
                }
            }
            taskDao.getById(result.taskId)?.let { taskDao.update(it.copy(recurrence = result.previousRecurrence)) }
        }
        result.spawnedTaskId?.let { deleteTask(it) }
        taskDao.getById(result.taskId)?.let { reminders.schedule(it.toDomain()) }
        syncRequester.requestSync()
    }

    suspend fun toggleStar(id: String) {
        val task = taskDao.getById(id) ?: return
        val starred = !task.starred
        taskDao.update(task.copy(starred = starred, starredAt = if (starred) clock() else null))
    }

    /** Deletes a task and its subtasks; returns a snapshot that can be passed to [restoreTasks]. */
    suspend fun deleteTask(id: String): List<TaskEntity> {
        val task = taskDao.getById(id) ?: return emptyList()
        val snapshot = taskDao.getChildren(id) + task
        db.withTransaction {
            snapshot.forEach { e ->
                if (e.remoteId == null) taskDao.delete(e) else taskDao.update(e.copy(pendingDelete = true))
            }
        }
        snapshot.forEach { reminders.cancel(it.id) }
        syncRequester.requestSync()
        return snapshot
    }

    suspend fun restoreTasks(snapshot: List<TaskEntity>) {
        db.withTransaction {
            // Parents first so children can reference them.
            snapshot.sortedBy { it.parentId != null }.forEach { e ->
                val existing = taskDao.getById(e.id)
                if (existing != null) {
                    taskDao.update(existing.copy(pendingDelete = false))
                } else {
                    // Already deleted remotely: recreate as a brand new task.
                    taskDao.insert(e.copy(remoteId = null, remotePosition = null, pendingDelete = false, isDirty = false, needsMove = false))
                }
            }
        }
        snapshot.forEach { e -> taskDao.getById(e.id)?.let { reminders.schedule(it.toDomain()) } }
        syncRequester.requestSync()
    }

    /** Deletes all completed tasks of a list ("Delete all completed tasks"). */
    suspend fun clearCompleted(listId: String): List<TaskEntity> {
        val completed = taskDao.getForList(listId).filter { it.completed && !it.pendingDelete }
        val ids = completed.mapTo(HashSet()) { it.id }
        return completed
            .filter { it.parentId == null || it.parentId !in ids }
            .flatMap { deleteTask(it.id) }
    }

    /**
     * Moves a task (and its subtasks) to another list. Synced tasks are re-created in the target
     * list and deleted from the source, which works for every task type the API supports.
     * Returns the id of the moved task.
     */
    suspend fun moveTaskToList(id: String, targetListId: String): String {
        val task = taskDao.getById(id) ?: return id
        if (task.listId == targetListId) return id
        val now = clock()
        val top = activeSiblings(targetListId, null).minOfOrNull { it.position }?.minus(1.0) ?: 0.0
        val children = taskDao.getChildren(id).filter { !it.pendingDelete }
        var newParentId = id
        db.withTransaction {
            fun relocated(e: TaskEntity, newId: String, parentId: String?, position: Double) = e.copy(
                id = newId, remoteId = null, remotePosition = null, listId = targetListId, parentId = parentId,
                position = position, isDirty = false, needsMove = false, pendingDelete = false, updatedAt = now,
            )
            if (task.remoteId == null) {
                taskDao.update(relocated(task, id, null, top))
            } else {
                newParentId = idGenerator()
                taskDao.insert(relocated(task, newParentId, null, top))
                taskDao.update(task.copy(pendingDelete = true))
            }
            children.forEach { child ->
                if (child.remoteId == null) {
                    taskDao.update(relocated(child, child.id, newParentId, child.position))
                } else {
                    taskDao.insert(relocated(child, idGenerator(), newParentId, child.position))
                    taskDao.update(child.copy(pendingDelete = true))
                }
            }
        }
        reminders.cancel(id)
        taskDao.getById(newParentId)?.let { reminders.schedule(it.toDomain()) }
        syncRequester.requestSync()
        return newParentId
    }

    /** Makes a top-level task a subtask of the task above it. Returns false if not possible. */
    suspend fun indent(id: String): Boolean {
        val task = taskDao.getById(id) ?: return false
        if (task.parentId != null || task.completed) return false
        if (taskDao.getChildren(id).any { !it.pendingDelete }) return false // only one level of nesting
        val siblings = activeSiblings(task.listId, null).sortedBy { it.position }
        val index = siblings.indexOfFirst { it.id == id }
        if (index <= 0) return false
        val newParent = siblings[index - 1]
        val pos = activeSiblings(task.listId, newParent.id).maxOfOrNull { it.position }?.plus(1.0) ?: 0.0
        taskDao.update(task.copy(parentId = newParent.id, position = pos, needsMove = true, updatedAt = clock()))
        syncRequester.requestSync()
        return true
    }

    /** Moves a subtask out to top level, right below its former parent. */
    suspend fun outdent(id: String): Boolean {
        val task = taskDao.getById(id) ?: return false
        val parent = task.parentId?.let { taskDao.getById(it) } ?: return false
        val next = activeSiblings(task.listId, null).filter { it.position > parent.position }.minOfOrNull { it.position }
        val pos = if (next != null) (parent.position + next) / 2.0 else parent.position + 1.0
        taskDao.update(task.copy(parentId = null, position = pos, needsMove = true, updatedAt = clock()))
        syncRequester.requestSync()
        return true
    }

    /**
     * Applies a new "My order" for a sibling group after drag & drop. Only [movedId] needs a
     * server-side move; the others keep their relative order.
     */
    suspend fun reorder(orderedIds: List<String>, movedId: String) {
        val rows = orderedIds.mapNotNull { taskDao.getById(it) }
        if (rows.isEmpty()) return
        val base = rows.minOf { it.position }
        db.withTransaction {
            rows.forEachIndexed { index, e ->
                val pos = base + index
                val moved = e.id == movedId
                if (e.position != pos || moved) {
                    taskDao.update(e.copy(position = pos, needsMove = e.needsMove || moved))
                }
            }
        }
        syncRequester.requestSync()
    }

    suspend fun rescheduleAllReminders() {
        taskDao.getActiveWithDueDate().forEach { reminders.schedule(it.toDomain()) }
    }

    /** Removes all local data (sign-out / account switch). */
    suspend fun clearAll() {
        taskDao.getActiveWithDueDate().forEach { reminders.cancel(it.id) }
        db.clearAllTables()
        settings.clear()
    }

    private suspend fun activeSiblings(listId: String, parentId: String?): List<TaskEntity> =
        taskDao.getForList(listId).filter { it.parentId == parentId && !it.pendingDelete && !it.completed }
}
