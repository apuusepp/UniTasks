package com.unitasks.app

import android.content.Intent

/** Plain text only: never open URLs or read streams supplied by another app. */
data class SharedTaskDraft(val title: String, val notes: String) {
    companion object {
        fun fromIntent(intent: Intent): SharedTaskDraft? = runCatching {
            if (intent.type != "text/plain") return null
            val text = when (intent.action) {
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                else -> return null
            }?.toString()?.trim().orEmpty()
            val subject = if (intent.action == Intent.ACTION_SEND) {
                intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim().orEmpty()
            } else ""
            if (text.isBlank() && subject.isBlank()) return null
            val heading = subject.ifBlank { text.lineSequence().first { it.isNotBlank() } }
                .replace(Regex("\\s+"), " ")
            val title = heading.take(120)
            // Preserve the complete shared content when the title cannot hold it all.
            val body = text.ifBlank { subject }
            val notes = if (body != title) body else ""
            SharedTaskDraft(title, notes)
        }.getOrNull()
    }
}
