package com.unitasks.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskOrganizerAndUnifiedTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val personal = TaskList(id = "l1", title = "My Tasks", isDefault = true)
    private val work = TaskList(id = "l2", title = "Work")
    private val shopping = TaskList(id = "l3", title = "Shopping")

    @Test
    fun buildTree_nests_children_under_parent_in_position_order() {
        val tasks = listOf(
            Task(id = "p2", listId = "l1", title = "Parent 2", position = 2.0),
            Task(id = "c2", listId = "l1", title = "Child B", parentId = "p1", position = 2.0),
            Task(id = "p1", listId = "l1", title = "Parent 1", position = 1.0),
            Task(id = "c1", listId = "l1", title = "Child A", parentId = "p1", position = 1.0),
        )
        val tree = TaskOrganizer.buildTree(tasks)
        assertEquals(listOf("p1", "p2"), tree.map { it.task.id })
        assertEquals(listOf("c1", "c2"), tree[0].children.map { it.id })
    }

    @Test
    fun unified_by_list_combines_all_visible_lists_and_respects_hidden_and_query() {
        val tasks = listOf(
            Task(id = "t1", listId = "l1", title = "Call dentist"),
            Task(id = "t2", listId = "l1", title = "Done item", completed = true, completedAt = 10L),
            Task(id = "t3", listId = "l2", title = "Prepare quarterly deck"),
            Task(id = "t4", listId = "l3", title = "Buy milk"),
        )
        val all = UnifiedViewBuilder.buildAllTasks(
            lists = listOf(personal, work, shopping),
            tasks = tasks,
            grouping = UnifiedGrouping.BY_LIST,
            hiddenListIds = emptySet(),
            query = "",
            today = today,
        )
        assertEquals(listOf("My Tasks", "Work", "Shopping"), all.sections.map { it.title })
        assertEquals(1, all.sections[0].completedCount)
        assertEquals(3, all.activeCount)
        assertEquals(listOf("t2"), all.completed.map { it.id })

        // Hide Shopping list
        val filtered = UnifiedViewBuilder.buildAllTasks(
            lists = listOf(personal, work, shopping),
            tasks = tasks,
            grouping = UnifiedGrouping.BY_LIST,
            hiddenListIds = setOf("l3"),
            query = "",
            today = today,
        )
        assertEquals(listOf("My Tasks", "Work"), filtered.sections.map { it.title })

        // Search across all lists keeps only matching lists
        val searched = UnifiedViewBuilder.buildAllTasks(
            lists = listOf(personal, work, shopping),
            tasks = tasks,
            grouping = UnifiedGrouping.BY_LIST,
            hiddenListIds = emptySet(),
            query = "quarterly",
            today = today,
        )
        assertEquals(listOf("Work"), searched.sections.map { it.title })
        assertEquals(listOf("t3"), searched.sections.single().nodes.map { it.task.id })
    }

    @Test
    fun unified_by_date_buckets_tasks_from_multiple_lists_chronologically() {
        val tasks = listOf(
            Task(id = "overdue", listId = "l1", title = "Pay rent", dueDate = today.minusDays(2)),
            Task(id = "today1", listId = "l2", title = "Ship release", dueDate = today),
            Task(id = "tmrw", listId = "l1", title = "Pack bag", dueDate = today.plusDays(1)),
            Task(id = "week", listId = "l3", title = "Team lunch", dueDate = today.plusDays(4)),
            Task(id = "later", listId = "l2", title = "Renew passport", dueDate = today.plusDays(30)),
            Task(id = "nodate", listId = "l3", title = "Read book"),
        )
        val result = UnifiedViewBuilder.buildAllTasks(
            lists = listOf(personal, work, shopping),
            tasks = tasks,
            grouping = UnifiedGrouping.BY_DATE,
            hiddenListIds = emptySet(),
            query = "",
            today = today,
        )
        assertEquals(
            listOf("Overdue", "Today", "Tomorrow", "Next 7 days", "Later", "No date"),
            result.sections.map { it.title },
        )
        assertTrue(result.sections.first().overdue)
        assertEquals("Work", result.listTitles[result.sections[1].nodes.single().task.listId])
    }

    @Test
    fun starred_aggregates_across_lists_ordered_by_starredAt() {
        val tasks = listOf(
            Task(id = "t1", listId = "l1", title = "Older star", starred = true, starredAt = 100L),
            Task(id = "t2", listId = "l2", title = "Newer star", starred = true, starredAt = 200L),
            Task(id = "t3", listId = "l2", title = "Not starred"),
        )
        val starred = UnifiedViewBuilder.buildStarred(
            lists = listOf(personal, work),
            tasks = tasks,
            sort = SortMode.STARRED_RECENTLY,
            query = "",
            today = today,
        )
        assertEquals(listOf("t2", "t1"), starred.sections.single().nodes.map { it.task.id })
    }

    @Test
    fun unified_none_grouping_produces_flat_list_and_sorts_across_all_lists() {
        val tasks = listOf(
            Task(id = "t1", listId = "l1", title = "Zebra task", position = 1.0, dueDate = today.plusDays(5), starred = false),
            Task(id = "t2", listId = "l2", title = "Alpha task", position = 1.0, dueDate = today.plusDays(1), starred = true, starredAt = 300L),
            Task(id = "t3", listId = "l3", title = "Middle task", position = 2.0, dueDate = today.plusDays(2), starred = true, starredAt = 100L),
        )
        val lists = listOf(personal, work, shopping)

        // Title sort across ungrouped All tasks
        val byTitle = UnifiedViewBuilder.buildAllTasks(
            lists = lists,
            tasks = tasks,
            grouping = UnifiedGrouping.NONE,
            sort = SortMode.TITLE,
            hiddenListIds = emptySet(),
            query = "",
            today = today,
        )
        assertEquals(1, byTitle.sections.size)
        assertEquals(null, byTitle.sections.single().title)
        assertEquals(listOf("t2", "t3", "t1"), byTitle.sections.single().nodes.map { it.task.id })
        assertEquals("My Tasks", byTitle.listTitles["l1"])
        assertEquals("Work", byTitle.listTitles["l2"])
        assertEquals("Shopping", byTitle.listTitles["l3"])

        // Date sort across ungrouped All tasks
        val byDate = UnifiedViewBuilder.buildAllTasks(
            lists = lists,
            tasks = tasks,
            grouping = UnifiedGrouping.NONE,
            sort = SortMode.DATE,
            hiddenListIds = emptySet(),
            query = "",
            today = today,
        )
        assertEquals(listOf("t2", "t3", "t1"), byDate.sections.single().nodes.map { it.task.id })

        // Starred recently sort across ungrouped All tasks
        val byStarred = UnifiedViewBuilder.buildAllTasks(
            lists = lists,
            tasks = tasks,
            grouping = UnifiedGrouping.NONE,
            sort = SortMode.STARRED_RECENTLY,
            hiddenListIds = emptySet(),
            query = "",
            today = today,
        )
        assertEquals(listOf("t2", "t3", "t1"), byStarred.sections.single().nodes.map { it.task.id })
    }
}

