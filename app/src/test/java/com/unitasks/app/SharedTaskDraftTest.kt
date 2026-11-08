package com.unitasks.app

import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedTaskDraftTest {
    private fun send(text: CharSequence) = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    @Test fun android_resolves_share_and_selected_text_to_the_exported_task_entry_point() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun resolves(action: String, type: String) = context.packageManager.queryIntentActivities(
            Intent(action).setType(type), PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.name == ShareTaskActivity::class.java.name && it.activityInfo.exported }
        assertTrue(resolves(Intent.ACTION_SEND, "text/plain"))
        assertTrue(resolves(Intent.ACTION_PROCESS_TEXT, "text/plain"))
        assertFalse(resolves(Intent.ACTION_SEND, "image/png"))
    }

    @Test fun subject_becomes_title_and_url_is_preserved_in_notes() {
        val draft = SharedTaskDraft.fromIntent(send("https://example.com/article").putExtra(Intent.EXTRA_SUBJECT, "Read this"))
        assertEquals(SharedTaskDraft("Read this", "https://example.com/article"), draft)
    }

    @Test fun selected_styled_text_is_received_from_the_context_menu() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, SpannableString("  Call the dentist  "))
        assertEquals(SharedTaskDraft("Call the dentist", ""), SharedTaskDraft.fromIntent(intent))
    }

    @Test fun multiline_and_long_text_are_not_lost() {
        val text = "Read report\n" + "More detail ".repeat(50)
        assertEquals(SharedTaskDraft("Read report", text.trim()), SharedTaskDraft.fromIntent(send(text)))
        val longText = "x".repeat(500)
        assertEquals(SharedTaskDraft("x".repeat(120), longText), SharedTaskDraft.fromIntent(send(longText)))
    }

    @Test fun a_plain_link_or_subject_only_can_be_saved_as_a_title() {
        assertEquals(SharedTaskDraft("https://example.com", ""), SharedTaskDraft.fromIntent(send("https://example.com")))
        assertEquals(SharedTaskDraft("Subject only", ""), SharedTaskDraft.fromIntent(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Subject only"),
        ))
    }

    @Test fun empty_unsupported_and_malformed_payloads_do_not_create_drafts() {
        assertNull(SharedTaskDraft.fromIntent(send(" \n ")))
        assertNull(SharedTaskDraft.fromIntent(send("text").setType("image/png")))
        assertNull(SharedTaskDraft.fromIntent(send("text").setAction(Intent.ACTION_VIEW)))
        assertNull(SharedTaskDraft.fromIntent(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, 42)))
    }
}
