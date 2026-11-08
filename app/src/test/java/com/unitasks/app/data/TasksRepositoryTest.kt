package com.unitasks.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.domain.RecurrenceRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TasksRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: Settings
    private lateinit var repo: TasksRepository
    private var syncRequests = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        settings = Settings(context, "test_prefs_${System.nanoTime()}")
        repo = TasksRepository(
            db = db,
            settings = settings,
            syncRequester = SyncRequester { syncRequests++ },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun default_list_cannot_be_deleted_but_other_lists_can() = runBlocking {
        val defaultId = repo.ensureDefaultList()
        val workId = repo.createList("Work")
        assertFalse(repo.deleteList(defaultId))
        assertTrue(repo.deleteList(workId))
        assertEquals(listOf(defaultId), repo.observeLists().first().map { it.id })
    }

    @Test
    fun completing_repeating_task_spawns_next_occurrence_and_undo_reverts_both() = runBlocking {
        val listId = repo.ensureDefaultList()
        val due = LocalDate.of(2026, 11, 7)
        val rule = RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = 1, remainingOccurrences = 3)
        val id = repo.addTask(listId, "Take vitamins", dueDate = due, starred = true, recurrence = rule)

        val result = repo.setCompleted(id, true)
        assertNotNull(result?.spawnedTaskId)

        val all = repo.observeTasks().first()
        assertEquals(2, all.size)
        val spawned = all.first { it.id == result!!.spawnedTaskId }
        assertFalse(spawned.completed)
        assertEquals(due.plusDays(1), spawned.dueDate)
        assertEquals(2, spawned.recurrence?.remainingOccurrences)
        assertTrue(spawned.starred)

        // Undo removes the spawned task and restores the original task + its recurrence rule
        repo.undoCompletion(result!!)
        val afterUndo = repo.observeTasks().first()
        assertEquals(1, afterUndo.size)
        assertFalse(afterUndo.single().completed)
        assertEquals(rule, afterUndo.single().recurrence)
    }

    @Test
    fun indent_outdent_and_subtask_completion_cascade() = runBlocking {
        val listId = repo.ensureDefaultList()
        // New top-level tasks prepend to the top, so add "Second" first then "First".
        val second = repo.addTask(listId, "Second")
        val first = repo.addTask(listId, "First")

        assertFalse(repo.indent(first)) // topmost cannot indent
        assertTrue(repo.indent(second))
        assertEquals(first, repo.getTask(second)?.parentId)
        assertTrue(db.taskDao().getById(second)!!.needsMove)

        // Completing parent completes its active child
        repo.setCompleted(first, true)
        assertTrue(repo.getTask(second)!!.completed)

        // Uncompleting child reopens parent
        repo.setCompleted(second, false)
        assertFalse(repo.getTask(first)!!.completed)

        // Outdent returns to top level
        assertTrue(repo.outdent(second))
        assertNull(repo.getTask(second)?.parentId)
    }

    @Test
    fun move_task_with_subtasks_to_another_list() = runBlocking {
        val listA = repo.ensureDefaultList()
        val listB = repo.createList("Work")
        val parent = repo.addTask(listA, "Prepare trip")
        val sub = repo.addTask(listA, "Book flight", parentId = parent)

        val movedParentId = repo.moveTaskToList(parent, listB)
        assertEquals(listB, repo.getTask(movedParentId)?.listId)
        assertEquals(listB, repo.getTask(sub)?.listId)
        assertEquals(movedParentId, repo.getTask(sub)?.parentId)
    }

    @Test fun bulk_dates_preserve_individual_times_and_repeat_rules_and_skip_completed_tasks() = runBlocking {
        val list = repo.ensureDefaultList()
        val originalDate = LocalDate.of(2026, 11, 7)
        val rule = RecurrenceRule(RecurrenceRule.Frequency.WEEKLY)
        val a = repo.addTask(list, "A", notes = "Keep notes", dueDate = originalDate, dueTime = java.time.LocalTime.of(9, 30), recurrence = rule)
        val b = repo.addTask(list, "B", dueDate = originalDate, dueTime = java.time.LocalTime.of(16, 0))
        val done = repo.addTask(list, "Done", dueDate = originalDate)
        repo.setCompleted(done, true)
        val requestsBefore = syncRequests
        repo.changeDueDates(setOf(a, b, done), originalDate.plusDays(3))
        assertEquals(originalDate.plusDays(3), repo.getTask(a)!!.dueDate)
        assertEquals(java.time.LocalTime.of(9, 30), repo.getTask(a)!!.dueTime)
        assertEquals(java.time.LocalTime.of(16, 0), repo.getTask(b)!!.dueTime)
        assertEquals(rule, repo.getTask(a)!!.recurrence)
        assertEquals("Keep notes", repo.getTask(a)!!.notes)
        assertEquals(originalDate, repo.getTask(done)!!.dueDate)
        assertEquals(requestsBefore + 1, syncRequests)
        repo.changeDueDates(setOf(a, b), null)
        assertNull(repo.getTask(a)!!.dueDate)
        assertNull(repo.getTask(a)!!.dueTime)
        assertNull(repo.getTask(a)!!.recurrence)
    }

    @Test fun drag_reorders_full_sibling_order_without_changing_list_or_subtask_parent() = runBlocking {
        val list = repo.ensureDefaultList()
        val a = repo.addTask(list, "A")
        val hidden = repo.addTask(list, "Hidden by search")
        val b = repo.addTask(list, "B")
        val child = repo.addTask(list, "Child", parentId = a)
        repo.moveRelative(a, b, before = true)
        val order = repo.getTasks().filter { it.parentId == null }.sortedBy { it.position }.map { it.id }
        assertEquals(listOf(a, b, hidden), order)
        assertEquals(a, repo.getTask(child)!!.parentId)
        assertTrue(db.taskDao().getById(a)!!.needsMove)
        repo.moveRelative(child, b, before = false)
        assertEquals(a, repo.getTask(child)!!.parentId)
        val otherList = repo.createList("Other")
        val outside = repo.addTask(otherList, "Outside")
        repo.moveRelative(a, outside, before = true)
        assertEquals(list, repo.getTask(a)!!.listId)
        // Partial orders must not overwrite ranks belonging to hidden siblings.
        repo.reorder(listOf(b, a), b)
        assertEquals(order, repo.getTasks().filter { it.listId == list && it.parentId == null }.sortedBy { it.position }.map { it.id })
    }
}
