package com.unitasks.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.unitasks.app.data.Settings
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.data.remote.TasksApi
import com.unitasks.app.domain.RecurrenceRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var settings: Settings
    private lateinit var repo: TasksRepository
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        settings = Settings(context, "sync_prefs_${System.nanoTime()}")
        repo = TasksRepository(db, settings)
        val api = TasksApi.create(OkHttpClient(), server.url("/").toString())
        engine = SyncEngine(db, settings, api, clock = { 1_760_000_000_000L })
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun pulls_remote_lists_and_tasks_and_pushes_local_creations_edits_and_deletes() = runBlocking {
        val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                return when {
                    request.method == "GET" && path == "/tasks/v1/users/@me/lists" -> MockResponse().setBody(
                        """{"items":[{"id":"r_default","title":"My Tasks"},{"id":"r_work","title":"Work"}]}""",
                    )
                    request.method == "POST" && path == "/tasks/v1/users/@me/lists" -> MockResponse().setBody(
                        """{"id":"r_new_list","title":"Groceries"}""",
                    )
                    request.method == "POST" && path == "/tasks/v1/lists/r_default/tasks" -> {
                        val parent = request.requestUrl?.queryParameter("parent")
                        val id = if (parent == null) "r_local_parent" else "r_local_sub"
                        MockResponse().setBody(
                            """{"id":"$id","title":"Created","status":"needsAction","position":"00001"}""",
                        )
                    }
                    request.method == "GET" && path == "/tasks/v1/lists/r_default/tasks" -> MockResponse().setBody(
                        """{"items":[
                            {"id":"r_local_parent","title":"Local parent","status":"needsAction","due":"2026-11-10T00:00:00.000Z","position":"00001"},
                            {"id":"r_local_sub","title":"Local sub","status":"needsAction","parent":"r_local_parent","position":"00001"},
                            {"id":"r_remote_1","title":"Remote task","notes":"from server","status":"needsAction","position":"00002"}
                        ]}""",
                    )
                    request.method == "GET" && path == "/tasks/v1/lists/r_work/tasks" -> MockResponse().setBody(
                        """{"items":[{"id":"r_work_1","title":"Review PR","status":"completed","completed":"2026-11-06T12:00:00Z","position":"00001"}]}""",
                    )
                    request.method == "GET" && path == "/tasks/v1/lists/r_new_list/tasks" -> MockResponse().setBody(
                        """{"items":[]}""",
                    )
                    request.method == "PATCH" -> MockResponse().setBody(
                        """{"id":"r_remote_1","title":"Edited locally","status":"needsAction","position":"00002"}""",
                    )
                    request.method == "DELETE" -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        // Seed a local default list + parent + subtask + extra list before first sync
        val defaultList = repo.ensureDefaultList()
        val parent = repo.addTask(
            listId = defaultList,
            title = "Local parent",
            dueDate = LocalDate.of(2026, 11, 10),
            dueTime = LocalTime.of(14, 30),
            starred = true,
            recurrence = RecurrenceRule(RecurrenceRule.Frequency.WEEKLY),
        )
        val sub = repo.addTask(defaultList, "Local sub", parentId = parent)
        repo.createList("Groceries")

        assertEquals(SyncResult.Success, engine.sync())

        val lists = repo.observeLists().first()
        assertEquals(listOf("My Tasks", "Work", "Groceries"), lists.map { it.title })

        val tasks = repo.observeTasks().first()
        assertEquals(4, tasks.size)

        // Local-only fields (star, dueTime, recurrence) survived the server round-trip
        val syncedParent = repo.getTask(parent)!!
        assertTrue(syncedParent.starred)
        assertEquals(LocalTime.of(14, 30), syncedParent.dueTime)
        assertNotNull(syncedParent.recurrence)
        assertEquals(parent, repo.getTask(sub)?.parentId)

        // Remote completed task in Work list was pulled
        val workTask = tasks.first { it.title == "Review PR" }
        assertTrue(workTask.completed)

        // Edit the pulled remote task and delete the subtask, then sync again
        val remoteTask = tasks.first { it.title == "Remote task" }
        repo.updateTask(remoteTask.id, "Edited locally", remoteTask.notes, null, null, null)
        repo.deleteTask(sub)

        assertEquals(SyncResult.Success, engine.sync())
        assertTrue(requests.any { it.method == "PATCH" && it.path!!.contains("r_remote_1") })
        assertTrue(requests.any { it.method == "DELETE" && it.path!!.contains("r_local_sub") })
        assertFalse(db.taskDao().getById(remoteTask.id)!!.isDirty)
    }
}
