package com.unitasks.app.domain

import java.net.URI
import java.time.LocalDate

data class DateDropTarget(val date: LocalDate?)

/** Headers with ranges use the first day in that range; task rows use their exact date. */
fun TaskSection.dateDropTarget(today: LocalDate): DateDropTarget? = when {
    key == "past" -> DateDropTarget(today.minusDays(1))
    key == "today" -> DateDropTarget(today)
    key == "tomorrow" -> DateDropTarget(today.plusDays(1))
    key == "week" -> DateDropTarget(today.plusDays(2))
    key == "later" -> DateDropTarget(today.plusDays(8))
    key == "nodate" -> DateDropTarget(null)
    key.startsWith("date:") -> runCatching { DateDropTarget(LocalDate.parse(key.removePrefix("date:"))) }.getOrNull()
    else -> null
}

data class TaskDrop(
    val taskId: String,
    val anchorId: String?,
    val before: Boolean,
    val dateTarget: DateDropTarget?,
    val keepDateGrouping: Boolean,
)

/** Only browser links are launchable; the original title and notes stay untouched. */
object TaskLinks {
    private val urlPattern = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>]+")

    fun extract(task: Task): List<String> = buildList {
        task.linkUrl?.let { add(it) }
        urlPattern.findAll(task.title + "\n" + task.notes).forEach { match ->
            var value = match.value.trimEnd('.', ',', ';', ':', '!', '?', '\"', '\'')
            for ((close, open) in listOf(')' to '(', ']' to '[', '}' to '{')) {
                while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) value = value.dropLast(1)
            }
            add(value)
        }
    }.mapNotNull { value ->
        val normalized = if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
        runCatching { URI(normalized) }.getOrNull()?.takeIf {
            it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank()
        }?.toString()
    }.distinct()
}
