package com.unitasks.app.domain

import java.time.LocalDate

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
 * every task list is visible in one place, grouped either by list or by due date.
 */
object UnifiedViewBuilder {

    fun buildAllTasks(
        lists: List<TaskList>,
        tasks: List<Task>,
        grouping: UnifiedGrouping,
        hiddenListIds: Set<String>,
        query: String,
        today: LocalDate,
    ): AggregateContent {
        val visibleLists = lists.filter { it.id !in hiddenListIds }
        val visibleIds = visibleLists.mapTo(HashSet()) { it.id }
        val visibleTasks = tasks.filter { it.listId in visibleIds }
        val titles = lists.associate { it.id to it.title }

        val sections = when (grouping) {
            UnifiedGrouping.BY_LIST -> {
                val byList = visibleTasks.groupBy { it.listId }
                visibleLists.mapNotNull { list ->
                    val listTasks = byList[list.id].orEmpty()
                    val nodes = TaskOrganizer.filterNodes(
                        TaskOrganizer.buildTree(listTasks.filter { !it.completed }), query,
                    )
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
            UnifiedGrouping.BY_DATE -> TaskOrganizer.dateSections(
                visibleTasks.filter { !it.completed && TaskOrganizer.matches(it, query) },
                today,
                compact = true,
            )
        }
        val completed = TaskOrganizer.completed(visibleTasks.filter { TaskOrganizer.matches(it, query) })
        return AggregateContent(sections, completed, titles)
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
