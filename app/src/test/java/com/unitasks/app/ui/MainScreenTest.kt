package com.unitasks.app.ui

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.unitasks.app.data.AuthMode
import com.unitasks.app.data.Settings
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.UnifiedGrouping
import com.unitasks.app.ui.theme.UniTasksTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var settings: Settings
    private lateinit var repo: TasksRepository
    private val today = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        settings = Settings(context, "ui_prefs_${System.nanoTime()}").also { it.setAuth(AuthMode.LOCAL) }
        repo = TasksRepository(db, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unified_view_shows_all_lists_switches_to_date_and_none_grouping_sorts_and_swipes_lists() {
        var personalId = ""
        var workId = ""
        var taskAId = ""
        runBlocking {
            personalId = repo.ensureDefaultList()
            workId = repo.createList("Work")
            taskAId = repo.addTask(personalId, "Buy coffee", dueDate = today.minusDays(1), starred = true)
            repo.addTask(workId, "Send invoice", dueDate = today)
        }
        val vm = MainViewModel(repo, settings, syncEngine = null, todayProvider = { today })

        composeRule.setContent {
            UniTasksTheme(dynamicColor = false) {
                val state by vm.state.collectAsState()
                MainScreen(
                    state = state,
                    today = today,
                    onSelectTab = vm::selectTab,
                    onSetGrouping = vm::setUnifiedGrouping,
                    onToggleListFilter = vm::toggleListVisibility,
                    onSearchChange = vm::setSearch,
                    onCreateList = vm::createList,
                    onRenameCurrentList = vm::renameCurrentList,
                    onDeleteCurrentList = vm::deleteCurrentList,
                    onSetSort = vm::setCurrentSort,
                    onAddTask = { l, t, n, d, ti, s, r -> vm.addTask(l, t, n, d, ti, s, recurrence = r) },
                    onToggleComplete = vm::toggleComplete,
                    onToggleStar = vm::toggleStar,
                    onUpdateTaskDateTime = vm::updateTaskDateTime,
                    onOpenTask = {},
                    onClearCompleted = vm::clearCompleted,
                    onUndo = vm::undo,
                    onSyncNow = {},
                    onConnectGoogle = {},
                    onSignOut = {},
                )
            }
        }

        // Default tab is "All tasks" grouped by list: tasks from both lists are visible together
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Send invoice").assertIsDisplayed()

        // Switch unified grouping to "By date": Overdue header appears and rows show list badges
        composeRule.onNodeWithTag("grouping_by_date").assertDoesNotExist()
        composeRule.onNodeWithTag("filter_list:$workId").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_grouping").performClick()
        composeRule.onNodeWithTag("grouping_by_date").performClick()
        assertEquals(UnifiedGrouping.BY_DATE, vm.state.value.unifiedGrouping)
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Send invoice").assertIsDisplayed()

        // Switch unified grouping to "None": no section headers, each task shows its list badge
        composeRule.onNodeWithTag("btn_grouping").performClick()
        composeRule.onNodeWithTag("grouping_none").performClick()
        assertEquals(UnifiedGrouping.NONE, vm.state.value.unifiedGrouping)
        composeRule.onNodeWithText("Overdue").assertDoesNotExist()
        composeRule.onNodeWithTag("badge:$personalId", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("badge:$workId", useUnmergedTree = true).assertIsDisplayed()

        // Sort All tasks by Title using the sort control
        composeRule.onNodeWithTag("btn_all_tasks_sort").performClick()
        composeRule.onNodeWithTag("sort_option:TITLE").performClick()
        assertEquals(SortMode.TITLE, vm.state.value.allTasksSort)

        // Filters live in a sheet, keeping the task view to one compact row.
        composeRule.onNodeWithTag("btn_list_filter").performClick()
        composeRule.onNodeWithTag("filter_list:$workId").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        assertEquals(setOf(workId), vm.state.value.hiddenListIds)
        composeRule.onNodeWithTag("filter_done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Send invoice").assertDoesNotExist()
        composeRule.onNodeWithText("  Lists 1/2").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_list_filter").performClick()
        composeRule.onNodeWithTag("filter_show_all").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Send invoice").assertIsDisplayed()

        // A completely hidden view explains the filter and offers a direct reset.
        composeRule.onNodeWithTag("btn_list_filter").performClick()
        composeRule.onNodeWithTag("filter_list:$personalId").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("filter_list:$workId").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("filter_done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithText("No lists selected").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_show_all_lists").performClick()
        composeRule.onNodeWithText("Send invoice").assertIsDisplayed()

        // Swipe left on the pager from "All tasks" -> "My Tasks" list tab, then swipe left -> "Work" list tab
        composeRule.onNodeWithTag("tasks_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(SelectedTab.ListTab(personalId), vm.state.value.selectedTab)

        composeRule.onNodeWithTag("tasks_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(SelectedTab.ListTab(workId), vm.state.value.selectedTab)

        // Swipe right back to "My Tasks"
        composeRule.onNodeWithTag("tasks_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(SelectedTab.ListTab(personalId), vm.state.value.selectedTab)

        // Switch to the Starred tab and complete the task
        composeRule.onNodeWithTag("tab_starred").performClick()
        composeRule.waitForIdle()
        assertEquals(SelectedTab.Starred, vm.state.value.selectedTab)
        composeRule.onNode(hasText("Buy coffee") and hasAnyAncestor(hasTestTag("tasks_list"))).assertIsDisplayed()

        composeRule.onNode(hasTestTag("complete:$taskAId") and hasAnyAncestor(hasTestTag("tasks_list"))).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Completed (1)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Completed (1)") and hasAnyAncestor(hasTestTag("tasks_list"))).assertIsDisplayed()
    }
}

