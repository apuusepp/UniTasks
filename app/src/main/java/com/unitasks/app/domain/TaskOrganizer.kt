package com.unitasks.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A (possibly header-less) block of tasks rendered in a list. */
data class TaskSection(
    val key: String,
    val title: String?,
    val nodes: List<TaskNode>,
    /** Set when the section represents a task list (unified "by list" view). */
    val listId: String? = null,
    /** True for "Past"/"Overdue" date buckets so the UI can tint them. */
    val overdue: Boolean = false,
    /** Number of completed tasks belonging to the section (unified "by list" view). */
    val completedCount: Int = 0,
)

/** Pure functions that turn flat task rows into what the screens display. */
object TaskOrganizer {

    private val myOrder = compareBy<Task>({ it.position }, { it.title.lowercase() })

    /**
     * Builds parent → children trees from active tasks using "My order".
     * Subtasks whose parent isn't present (e.g. completed parent) are shown at top level.
     */
    fun buildTree(tasks: List<Task>): List<TaskNode> {
        val ids = tasks.mapTo(HashSet()) { it.id }
        val childrenByParent = tasks.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId!! }
        return tasks
            .filter { it.parentId == null || it.parentId !in ids }
            .sortedWith(myOrder)
            .map { TaskNode(it, childrenByParent[it.id].orEmpty().sortedWith(myOrder)) }
    }

    /** Completed tasks, most recently completed first (like Google Tasks' "Completed" section). */
    fun completed(tasks: List<Task>): List<Task> =
        tasks.filter { it.completed }.sortedWith(compareByDescending<Task> { it.completedAt ?: 0L }.thenBy { it.position })

    /** Sections for a single list in the given sort mode (active tasks only). */
    fun sectionsForList(tasks: List<Task>, sort: SortMode, today: LocalDate): List<TaskSection> {
        val active = tasks.filter { !it.completed }
        return when (sort) {
            SortMode.MY_ORDER -> listOf(TaskSection("order", null, buildTree(active)))
            SortMode.DATE -> dateSections(active, today, compact = false)
            SortMode.TITLE -> listOf(
                TaskSection("title", null, active.sortedBy { it.title.lowercase() }.map { TaskNode(it) }),
            )
            SortMode.STARRED_RECENTLY -> {
                val starred = active.filter { it.starred }.sortedByDescending { it.starredAt ?: 0L }
                val rest = buildTree(active.filter { !it.starred && (it.parentId == null || active.none { p -> p.id == it.parentId && p.starred }) })
                buildList {
                    if (starred.isNotEmpty()) add(TaskSection("starred", "Starred recently", starred.map { TaskNode(it) }))
                    if (rest.isNotEmpty()) add(TaskSection("rest", if (starred.isEmpty()) null else "Other tasks", rest))
                }
            }
        }
    }

    /**
     * Groups tasks into date buckets. Non-compact mode mirrors Google Tasks' date sort
     * (Past, Today, Tomorrow, one header per future date, No date). Compact mode is used by the
     * unified view (Overdue, Today, Tomorrow, Next 7 days, Later, No date).
     */
    fun dateSections(tasks: List<Task>, today: LocalDate, compact: Boolean): List<TaskSection> {
        val byDate = compareBy<Task>({ it.dueDate }, { it.position }, { it.dueTime })
        val (dated, undated) = tasks.partition { it.dueDate != null }
        val sections = mutableListOf<TaskSection>()
        val past = dated.filter { it.dueDate!!.isBefore(today) }.sortedWith(byDate)
        if (past.isNotEmpty()) sections += TaskSection("past", if (compact) "Overdue" else "Past", past.map { TaskNode(it) }, overdue = true)
        val todays = dated.filter { it.dueDate == today }.sortedWith(byDate)
        if (todays.isNotEmpty()) sections += TaskSection("today", "Today", todays.map { TaskNode(it) })
        val tomorrow = today.plusDays(1)
        val tomorrows = dated.filter { it.dueDate == tomorrow }.sortedWith(byDate)
        if (tomorrows.isNotEmpty()) sections += TaskSection("tomorrow", "Tomorrow", tomorrows.map { TaskNode(it) })
        val future = dated.filter { it.dueDate!!.isAfter(tomorrow) }.sortedWith(byDate)
        if (compact) {
            val weekEnd = today.plusDays(7)
            val (week, later) = future.partition { !it.dueDate!!.isAfter(weekEnd) }
            if (week.isNotEmpty()) sections += TaskSection("week", "Next 7 days", week.map { TaskNode(it) })
            if (later.isNotEmpty()) sections += TaskSection("later", "Later", later.map { TaskNode(it) })
        } else {
            future.groupBy { it.dueDate!! }.toSortedMap().forEach { (date, items) ->
                sections += TaskSection("date:$date", formatHeaderDate(date, today), items.map { TaskNode(it) })
            }
        }
        if (undated.isNotEmpty()) {
            sections += TaskSection("nodate", "No date", undated.sortedWith(myOrder).map { TaskNode(it) })
        }
        return sections
    }

    fun formatHeaderDate(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val pattern = if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** Case-insensitive match on title or notes. */
    fun matches(task: Task, query: String): Boolean =
        query.isBlank() || task.title.contains(query, ignoreCase = true) || task.notes.contains(query, ignoreCase = true)

    /** Filters trees: keep a parent when it or any child matches (only matching children kept unless parent matches). */
    fun filterNodes(nodes: List<TaskNode>, query: String): List<TaskNode> {
        if (query.isBlank()) return nodes
        return nodes.mapNotNull { node ->
            val parentMatches = matches(node.task, query)
            val kids = if (parentMatches) node.children else node.children.filter { matches(it, query) }
            if (parentMatches || kids.isNotEmpty()) node.copy(children = kids) else null
        }
    }
}
