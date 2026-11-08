package com.unitasks.app.domain

import java.time.LocalDate
import java.time.LocalTime

/** Result of building the unified "All tasks" or "Starred" views. */
data class AggregateContent(
    val sections: List<TaskSection>,
    val completed: List<Task>,
    /** listId → list title, used to badge rows with their originating list. */
    val listTitles: Map<String, String>,
) {
    val activeCount: Int get() = sections.sumOf { s -> s.nodes.sumOf { 1 + it.children.size } }
}

/**
 * Builds the cross-list views. This is the app's key differentiator over Google Tasks:
 * every task list is visible in one place — grouped by list, grouped by due date, or ungrouped
 * with a list badge on each task — and sortable by My order, Date, Starred recently, or Title.
 */
object UnifiedViewBuilder {

    private val byDueDateThenOrder = compareBy<Task>(
        { it.dueDate ?: LocalDate.MAX },
        { it.dueTime ?: LocalTime.MAX },
        { it.position },
        { it.title.lowercase() },
    )

    private val byStarredThenOrder = compareByDescending<Task> { it.starred }
        .thenByDescending { it.starredAt ?: 0L }
        .thenBy { it.position }
        .thenBy { it.title.lowercase() }

    private val byTitle = compareBy<Task>({ it.title.lowercase() }, { it.position })

    fun buildAllTasks(
        lists: List<TaskList>,
        tasks: List<Task>,
        grouping: UnifiedGrouping,
        sort: SortMode = SortMode.MY_ORDER,
        hiddenListIds: Set<String>,
        query: String,
        today: LocalDate,
    ): AggregateContent {
        val visibleLists = lists.filter { it.id !in hiddenListIds }
        val visibleIds = visibleLists.mapTo(HashSet()) { it.id }
        val listOrder = visibleLists.mapIndexed { idx, l -> l.id to idx }.toMap()
        val visibleTasks = tasks.filter { it.listId in visibleIds }
        val titles = lists.associate { it.id to it.title }

        val sections = when (grouping) {
            UnifiedGrouping.BY_LIST -> {
                val byList = visibleTasks.groupBy { it.listId }
                visibleLists.mapNotNull { list ->
                    val listTasks = byList[list.id].orEmpty()
                    val active = listTasks.filter { !it.completed }
                    val nodes = sortNodesForGroup(active, sort, query)
                    // While searching, drop lists without hits to keep results focused.
                    if (query.isNotBlank() && nodes.isEmpty()) return@mapNotNull null
                    TaskSection(
                        key = "list:${list.id}",
                        title = list.title,
                        nodes = nodes,
                        listId = list.id,
                        completedCount = listTasks.count { it.completed },
                    )
                }
            }
            UnifiedGrouping.BY_DATE -> {
                val active = visibleTasks.filter { !it.completed && TaskOrganizer.matches(it, query) }
                TaskOrganizer.dateSections(active, today, compact = true).map { section ->
                    val sortedNodes = when (sort) {
                        SortMode.TITLE -> section.nodes.sortedBy { it.task.title.lowercase() }
                        SortMode.STARRED_RECENTLY -> section.nodes.sortedWith(
                            compareByDescending<TaskNode> { it.task.starred }
                                .thenByDescending { it.task.starredAt ?: 0L }
                                .thenBy { it.task.dueDate ?: LocalDate.MAX },
                        )
                        SortMode.MY_ORDER, SortMode.DATE -> section.nodes
                    }
                    section.copy(nodes = sortedNodes)
                }
            }
            UnifiedGrouping.NONE -> {
                val active = visibleTasks.filter { !it.completed }
                val nodes = when (sort) {
                    SortMode.MY_ORDER -> {
                        val orderedByList = active.sortedWith(
                            compareBy<Task>({ listOrder[it.listId] ?: Int.MAX_VALUE }, { it.position }, { it.title.lowercase() }),
                        )
                        TaskOrganizer.filterNodes( buildTreePreservingListOrder(orderedByList, listOrder), query)
                    }
                    SortMode.DATE -> active.filter { TaskOrganizer.matches(it, query) }
                        .sortedWith(byDueDateThenOrder)
                        .map { TaskNode(it) }
                    SortMode.STARRED_RECENTLY -> active.filter { TaskOrganizer.matches(it, query) }
                        .sortedWith(byStarredThenOrder)
                        .map { TaskNode(it) }
                    SortMode.TITLE -> active.filter { TaskOrganizer.matches(it, query) }
                        .sortedWith(byTitle)
                        .map { TaskNode(it) }
                }
                if (nodes.isEmpty()) emptyList() else listOf(TaskSection(key = "all_flat", title = null, nodes = nodes))
            }
        }
        val completed = TaskOrganizer.completed(visibleTasks.filter { TaskOrganizer.matches(it, query) })
        return AggregateContent(sections, completed, titles)
    }

    private fun sortNodesForGroup(active: List<Task>, sort: SortMode, query: String): List<TaskNode> = when (sort) {
        SortMode.MY_ORDER -> TaskOrganizer.filterNodes(TaskOrganizer.buildTree(active), query)
        SortMode.DATE -> active.filter { TaskOrganizer.matches(it, query) }.sortedWith(byDueDateThenOrder).map { TaskNode(it) }
        SortMode.STARRED_RECENTLY -> active.filter { TaskOrganizer.matches(it, query) }.sortedWith(byStarredThenOrder).map { TaskNode(it) }
        SortMode.TITLE -> active.filter { TaskOrganizer.matches(it, query) }.sortedWith(byTitle).map { TaskNode(it) }
    }

    private fun buildTreePreservingListOrder(tasks: List<Task>, listOrder: Map<String, Int>): List<TaskNode> {
        val cmp = compareBy<Task>({ listOrder[it.listId] ?: Int.MAX_VALUE }, { it.position }, { it.title.lowercase() })
        val ids = tasks.mapTo(HashSet()) { it.id }
        val childrenByParent = tasks.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId!! }
        return tasks
            .filter { it.parentId == null || it.parentId !in ids }
            .sortedWith(cmp)
            .map { TaskNode(it, childrenByParent[it.id].orEmpty().sortedWith(cmp)) }
    }

    /** Google Tasks' "Starred" tab: starred tasks from every list. */
    fun buildStarred(
        lists: List<TaskList>,
        tasks: List<Task>,
        sort: SortMode,
        query: String,
        today: LocalDate,
    ): AggregateContent {
        val titles = lists.associate { it.id to it.title }
        val starred = tasks.filter { it.starred && it.listId in titles && TaskOrganizer.matches(it, query) }
        val active = starred.filter { !it.completed }
        val sections = when (sort) {
            SortMode.DATE -> TaskOrganizer.dateSections(active, today, compact = false)
            SortMode.TITLE -> listOf(TaskSection("starred", null, active.sortedBy { it.title.lowercase() }.map { TaskNode(it) }))
            else -> listOf(TaskSection("starred", null, active.sortedByDescending { it.starredAt ?: 0L }.map { TaskNode(it) }))
        }.filter { it.nodes.isNotEmpty() }
        return AggregateContent(sections, TaskOrganizer.completed(starred), titles)
    }
}
