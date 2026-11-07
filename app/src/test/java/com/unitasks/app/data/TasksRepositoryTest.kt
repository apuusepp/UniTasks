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
}
