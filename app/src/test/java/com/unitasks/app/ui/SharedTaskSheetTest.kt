package com.unitasks.app.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import com.unitasks.app.domain.TaskList
import com.unitasks.app.ui.components.AddTaskSheet
import com.unitasks.app.ui.theme.UniTasksTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SharedTaskSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun shared_draft_can_be_edited_and_survives_state_restoration_before_saving() {
        var saved: Triple<String, String, String>? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            UniTasksTheme(dynamicColor = false) {
                AddTaskSheet(
                    lists = listOf(TaskList(id = "inbox", title = "Inbox")),
                    initialListId = "inbox", allowListSelection = true,
                    initialTitle = "Article", initialNotes = "https://example.com",
                    heading = "Add to UniTasks", onDismiss = {},
                    onSave = { list, title, notes, _, _, _, _ -> saved = Triple(list, title, notes) },
                )
            }
        }
        composeRule.onNodeWithTag("add_title").performTextReplacement("Read article")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("add_save").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        assertEquals(Triple("inbox", "Read article", "https://example.com"), saved)
    }
}
