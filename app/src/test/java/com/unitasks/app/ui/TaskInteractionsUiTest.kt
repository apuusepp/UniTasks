package com.unitasks.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.unitasks.app.data.*
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.domain.*
import com.unitasks.app.ui.theme.UniTasksTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TaskInteractionsUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var repo: TasksRepository
    private lateinit var vm: MainViewModel
    private val today = LocalDate.of(2026, 11, 8)
    private var openedUri: String? = null
    private var lastDrop: TaskDrop? = null
    private lateinit var preferencesName: String

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        preferencesName = "interactions_${System.nanoTime()}"
        val settings = Settings(context, preferencesName).also { it.setAuth(AuthMode.LOCAL) }
        repo = TasksRepository(db, settings)
        vm = MainViewModel(repo, settings, null, todayProvider = { today })
    }
    @After fun close() { vm.viewModelScope.cancel(); db.close() }

    private fun render() {
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { openedUri = uri }
            }) {
                UniTasksTheme(dynamicColor = false) {
                    val state by vm.state.collectAsState()
                    MainScreen(state = state, today = today,
                        onSelectTab = vm::selectTab, onSetGrouping = vm::setUnifiedGrouping,
                        onToggleListFilter = vm::toggleListVisibility, onSearchChange = vm::setSearch,
                        onCreateList = vm::createList, onRenameCurrentList = vm::renameCurrentList,
                        onDeleteCurrentList = vm::deleteCurrentList, onSetSort = vm::setCurrentSort,
                        onAddTask = { l, t, n, d, ti, s, r -> vm.addTask(l, t, n, d, ti, s, recurrence = r) },
                        onToggleComplete = vm::toggleComplete, onToggleStar = vm::toggleStar,
                        onUpdateTaskDateTime = vm::updateTaskDateTime, onOpenTask = {},
                        onClearCompleted = vm::clearCompleted, onUndo = vm::undo,
                        onSyncNow = {}, onConnectGoogle = {}, onSignOut = {},
                        onChangeDueDates = vm::changeDueDates, onDropTask = { lastDrop = it; vm.dropTask(it) })
                }
            }
        }
    }
    private fun active(tag: String) = rule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("tasks_list")), useUnmergedTree = true)
    private fun awaitDatabase(predicate: suspend () -> Boolean) {
        rule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            runBlocking { predicate() }
        }
        rule.waitForIdle()
    }

    @Test fun active_counts_selection_bulk_date_and_row_links_work_together() {
        lateinit var list: String
        lateinit var a: String
        lateinit var b: String
        runBlocking {
            list = repo.ensureDefaultList()
            a = repo.addTask(list, "Read article", notes = "https://example.com/article")
            b = repo.addTask(list, "Call dentist")
        }
        render()
        rule.onNodeWithTag("count_all_tasks", useUnmergedTree = true).assertTextEquals("2")
        rule.onNodeWithTag("count_list:$list", useUnmergedTree = true).assertTextEquals("2")
        active("task_link:$a:0").performClick()
        assertEquals("https://example.com/article", openedUri)
        rule.onNodeWithTag("btn_select_tasks").performClick()
        active("select:$a").performClick()
        active("select:$b").performClick()
        rule.onNodeWithText("2 selected").assertIsDisplayed()
        rule.onNodeWithTag("bulk_change_date").performClick()
        rule.onNodeWithTag("bulk_date_tomorrow").performClick()
        awaitDatabase { repo.getTask(a)?.dueDate == today.plusDays(1) && repo.getTask(b)?.dueDate == today.plusDays(1) }
        active("complete:$a").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitDatabase { repo.getTask(a)?.completed == true && vm.state.value.tasks.firstOrNull { it.id == a }?.completed == true }
        rule.onNodeWithTag("count_all_tasks", useUnmergedTree = true).assertTextEquals("1")
        rule.onNodeWithTag("count_list:$list", useUnmergedTree = true).assertTextEquals("1")
    }

    @Test fun dragging_a_handle_persists_a_new_manual_order() {
        lateinit var list: String
        lateinit var a: String
        lateinit var b: String
        runBlocking { list = repo.ensureDefaultList(); a = repo.addTask(list, "A"); b = repo.addTask(list, "B") }
        vm.selectTab(SelectedTab.ListTab(list))
        render()
        val handle = active("drag:$b").fetchSemanticsNode().boundsInRoot
        val destination = active("task:$a").fetchSemanticsNode().boundsInRoot
        active("drag:$b").performTouchInput {
            swipe(center, Offset(center.x, destination.center.y + destination.height / 4 - handle.top), 600)
        }
        rule.runOnIdle { assertEquals(TaskDrop(b, a, false, null, false), lastDrop) }
        awaitDatabase {
            repo.getTasks().filter { it.listId == list }.sortedBy { it.position }.map { it.id } == listOf(a, b) &&
                vm.state.value.tasks.sortedBy { it.position }.map { it.id } == listOf(a, b)
        }
        assertEquals(listOf(a, b), vm.state.value.tasks.sortedBy { it.position }.map { it.id })
    }

    @Test fun dragging_from_today_to_tomorrow_changes_the_persisted_date() {
        lateinit var a: String
        lateinit var b: String
        runBlocking {
            val list = repo.ensureDefaultList()
            repo.addTask(list, "Overdue", dueDate = today.minusDays(1))
            a = repo.addTask(list, "Today task", dueDate = today)
            b = repo.addTask(list, "Tomorrow task", dueDate = today.plusDays(1))
        }
        vm.setUnifiedGrouping(UnifiedGrouping.BY_DATE)
        render()
        val handle = active("drag:$a").fetchSemanticsNode().boundsInRoot
        val destination = active("task:$b").fetchSemanticsNode().boundsInRoot
        active("drag:$a").performTouchInput {
            swipe(center, Offset(center.x, destination.center.y - handle.top), 600)
        }
        awaitDatabase { repo.getTask(a)?.dueDate == today.plusDays(1) }
    }

    @Test fun flat_all_tasks_drag_reorders_across_lists_without_moving_the_tasks_to_another_list() {
        lateinit var listA: String
        lateinit var listB: String
        lateinit var a: String
        lateinit var b: String
        runBlocking {
            listA = repo.ensureDefaultList(); listB = repo.createList("Work")
            a = repo.addTask(listA, "Personal"); b = repo.addTask(listB, "Work task")
        }
        vm.setUnifiedGrouping(UnifiedGrouping.NONE)
        render()
        val handle = active("drag:$a").fetchSemanticsNode().boundsInRoot
        val destination = active("task:$b").fetchSemanticsNode().boundsInRoot
        active("drag:$a").performTouchInput {
            swipe(center, Offset(center.x, destination.center.y + destination.height / 4 - handle.top), 600)
        }
        awaitDatabase { vm.state.value.sections.flatMap { it.nodes }.map { it.task.id } == listOf(b, a) }
        assertEquals(listA, runBlocking { repo.getTask(a)!!.listId })
        assertEquals(listB, runBlocking { repo.getTask(b)!!.listId })
        assertEquals(listOf(b, a), Settings(ApplicationProvider.getApplicationContext(), preferencesName).manualTaskOrder.value)
    }
}
