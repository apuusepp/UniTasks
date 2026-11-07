package com.unitasks.app.domain

import java.time.LocalDate
import java.time.LocalTime

/** A Google Tasks task list. */
data class TaskList(
    val id: String,
    val title: String,
    val sortMode: SortMode = SortMode.MY_ORDER,
    val isDefault: Boolean = false,
)

/** A single task (top-level or subtask). */
data class Task(
    val id: String,
    val listId: String,
    val title: String,
    val notes: String = "",
    val parentId: String? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val starred: Boolean = false,
    val starredAt: Long? = null,
    val recurrence: RecurrenceRule? = null,
    /** Order within siblings for "My order" (smaller = higher). */
    val position: Double = 0.0,
    val linkUrl: String? = null,
    val linkTitle: String? = null,
    val updatedAt: Long = 0L,
) {
    val isSubtask: Boolean get() = parentId != null
}

/** A task together with its (ordered) subtasks, as displayed in a list. */
data class TaskNode(
    val task: Task,
    val children: List<Task> = emptyList(),
)

enum class SortMode { MY_ORDER, DATE, STARRED_RECENTLY, TITLE }

/** How the unified "All tasks" view groups tasks. */
enum class UnifiedGrouping { BY_LIST, BY_DATE }
