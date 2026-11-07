package com.unitasks.app.ui

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.unitasks.app.data.AuthMode
import com.unitasks.app.data.Settings
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.ui.theme.UniTasksTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    fun unified_view_shows_all_lists_switches_to_date_grouping_and_filters_lists() {
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
        composeRule.onNodeWithTag("grouping_by_date").performClick()
        org.junit.Assert.assertEquals(
            "grouping=${vm.state.value.unifiedGrouping}, sections=${vm.state.value.sections.map { it.title }}",
            com.unitasks.app.domain.UnifiedGrouping.BY_DATE,
            vm.state.value.unifiedGrouping,
        )
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Send invoice").assertIsDisplayed()

        // Filter out the "Work" list via its filter chip
        composeRule.onNodeWithTag("filter_list:$workId").performClick()
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Send invoice").assertDoesNotExist()

        // Switch to the Starred tab
        composeRule.onNodeWithTag("tab_starred").performClick()
        composeRule.onNodeWithText("Buy coffee").assertIsDisplayed()

        // Complete the task from the UI and verify the "Completed (1)" section appears
        composeRule.onNodeWithTag("complete:$taskAId").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Completed (1)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Completed (1)").assertIsDisplayed()
    }
}
