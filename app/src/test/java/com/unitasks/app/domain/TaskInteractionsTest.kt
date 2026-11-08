package com.unitasks.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskInteractionsTest {
    @Test fun links_in_notes_and_title_are_browser_links_and_preserve_full_addresses() {
        val task = Task("t", "l", "Read https://example.com/a", notes = "Visit www.example.org/path. Also (https://example.com/b?q=yes).",
            linkUrl = "https://example.com/a")
        assertEquals(listOf("https://example.com/a", "https://www.example.org/path", "https://example.com/b?q=yes"), TaskLinks.extract(task))
        assertTrue(TaskLinks.extract(task.copy(title = "No URL", notes = "javascript:alert(1)", linkUrl = "content://private/data")).isEmpty())
    }

    @Test fun date_group_drop_targets_are_deterministic_including_no_date_and_exact_dates() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals(DateDropTarget(today.plusDays(1)), TaskSection("tomorrow", "Tomorrow", emptyList()).dateDropTarget(today))
        assertEquals(DateDropTarget(today.plusDays(2)), TaskSection("week", "Next 7 days", emptyList()).dateDropTarget(today))
        assertEquals(DateDropTarget(null), TaskSection("nodate", "No date", emptyList()).dateDropTarget(today))
        assertEquals(DateDropTarget(LocalDate.of(2027, 2, 3)), TaskSection("date:2027-02-03", "Future", emptyList()).dateDropTarget(today))
        assertNull(TaskSection("list:id", "List", emptyList()).dateDropTarget(today))
    }
}
