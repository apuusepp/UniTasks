package com.unitasks.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskList
import java.time.LocalDate
import java.time.LocalTime

/**
 * Local copy of a task list. `remoteId == null` means it has not been created on the server yet.
 */
@Entity(tableName = "task_lists", indices = [Index(value = ["remoteId"], unique = true)])
data class TaskListEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val title: String,
    val sortIndex: Int = 0,
    val sortMode: String = SortMode.MY_ORDER.name,
    val updatedAt: Long = 0L,
    val isDirty: Boolean = false,
    val pendingDelete: Boolean = false,
)

/**
 * Local copy of a task. Fields marked "local only" have no Google Tasks API equivalent.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId"), Index("parentId"), Index(value = ["remoteId"], unique = true)],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /** Epoch day of the due date. */
    val dueDate: Long? = null,
    /** Minutes after midnight (local only: the API drops the time part). */
    val dueTimeMinutes: Int? = null,
    /** Local only. */
    val starred: Boolean = false,
    val starredAt: Long? = null,
    /** Encoded [RecurrenceRule] (local only). */
    val recurrence: String? = null,
    /** Order among siblings for "My order". */
    val position: Double = 0.0,
    /** Server-side `position` string (lexicographically sortable). */
    val remotePosition: String? = null,
    val linkUrl: String? = null,
    val linkTitle: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Content fields changed locally and must be PATCHed. */
    val isDirty: Boolean = false,
    /** Parent / sibling position changed locally and must be sent via tasks.move. */
    val needsMove: Boolean = false,
    /** Deleted locally; remote delete pending. */
    val pendingDelete: Boolean = false,
)

fun TaskListEntity.toDomain(isDefault: Boolean = false) = TaskList(
    id = id,
    title = title,
    sortMode = runCatching { SortMode.valueOf(sortMode) }.getOrDefault(SortMode.MY_ORDER),
    isDefault = isDefault,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    parentId = parentId,
    completed = completed,
    completedAt = completedAt,
    dueDate = dueDate?.let(LocalDate::ofEpochDay),
    dueTime = dueTimeMinutes?.let { LocalTime.of(it / 60, it % 60) },
    starred = starred,
    starredAt = starredAt,
    recurrence = RecurrenceRule.decode(recurrence),
    position = position,
    linkUrl = linkUrl,
    linkTitle = linkTitle,
    updatedAt = updatedAt,
)

fun LocalTime.toMinutes(): Int = hour * 60 + minute
