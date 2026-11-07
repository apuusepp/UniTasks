package com.unitasks.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Repeat rule for a task, modelled after the Google Tasks "Repeat" dialog:
 * "Repeats every N day/week/month/year", optional weekdays for weekly rules,
 * and an end condition (never / on a date / after N occurrences).
 *
 * The public Google Tasks API does not expose recurrence, so rules are stored locally and
 * the next occurrence is materialised when the current one is completed.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val endDate: LocalDate? = null,
    /** Occurrences left including the current one; null = unlimited. */
    val remainingOccurrences: Int? = null,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(remainingOccurrences == null || remainingOccurrences >= 1) { "remainingOccurrences must be >= 1" }
    }

    /** Raw next date following [current], ignoring end conditions. */
    fun nextDateAfter(current: LocalDate): LocalDate = when (frequency) {
        Frequency.DAILY -> current.plusDays(interval.toLong())
        Frequency.MONTHLY -> current.plusMonths(interval.toLong())
        Frequency.YEARLY -> current.plusYears(interval.toLong())
        Frequency.WEEKLY -> nextWeekly(current)
    }

    private fun nextWeekly(current: LocalDate): LocalDate {
        if (daysOfWeek.isEmpty()) return current.plusWeeks(interval.toLong())
        val sorted = daysOfWeek.sortedBy { it.value }
        // Later day in the same (Mon-based) week?
        sorted.firstOrNull { it.value > current.dayOfWeek.value }?.let { day ->
            return current.with(TemporalAdjusters.nextOrSame(day))
        }
        val weekStart = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetWeek = weekStart.plusWeeks(interval.toLong())
        return targetWeek.with(TemporalAdjusters.nextOrSame(sorted.first()))
    }

    /**
     * Computes the next occurrence after completing the occurrence due on [current].
     * Returns null when the series has ended.
     */
    fun next(current: LocalDate): Occurrence? {
        if (remainingOccurrences != null && remainingOccurrences <= 1) return null
        val nextDate = nextDateAfter(current)
        if (endDate != null && nextDate.isAfter(endDate)) return null
        val nextRule = copy(remainingOccurrences = remainingOccurrences?.minus(1))
        return Occurrence(nextDate, nextRule)
    }

    data class Occurrence(val date: LocalDate, val rule: RecurrenceRule)

    /** Serialises to an RRULE-like string, e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=3`. */
    fun encode(): String = buildList {
        add("FREQ=${frequency.name}")
        add("INTERVAL=$interval")
        if (daysOfWeek.isNotEmpty()) {
            add("BYDAY=" + daysOfWeek.sortedBy { it.value }.joinToString(",") { DAY_CODES.getValue(it) })
        }
        endDate?.let { add("UNTIL=$it") }
        remainingOccurrences?.let { add("COUNT=$it") }
    }.joinToString(";")

    /** Human readable summary, e.g. "Every 2 weeks on Mon, Wed, until Dec 1, 2026". */
    fun describe(locale: Locale = Locale.getDefault()): String {
        val unit = when (frequency) {
            Frequency.DAILY -> "day"
            Frequency.WEEKLY -> "week"
            Frequency.MONTHLY -> "month"
            Frequency.YEARLY -> "year"
        }
        val sb = StringBuilder(if (interval == 1) "Every $unit" else "Every $interval ${unit}s")
        if (frequency == Frequency.WEEKLY && daysOfWeek.isNotEmpty()) {
            sb.append(" on ")
            sb.append(daysOfWeek.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) })
        }
        endDate?.let { sb.append(", until ").append(it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))) }
        remainingOccurrences?.let { sb.append(", ").append(if (it == 1) "1 more time" else "$it more times") }
        return sb.toString()
    }

    companion object {
        private val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_DAYS = DAY_CODES.entries.associate { (k, v) -> v to k }

        /** Parses [encode] output. Returns null for blank/invalid input. */
        fun decode(value: String?): RecurrenceRule? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val parts = value.split(';').mapNotNull {
                    val idx = it.indexOf('=')
                    if (idx <= 0) null else it.substring(0, idx).trim().uppercase() to it.substring(idx + 1).trim()
                }.toMap()
                RecurrenceRule(
                    frequency = Frequency.valueOf(parts.getValue("FREQ").uppercase()),
                    interval = parts["INTERVAL"]?.toInt() ?: 1,
                    daysOfWeek = parts["BYDAY"]?.split(',')?.filter { it.isNotBlank() }
                        ?.map { CODE_DAYS.getValue(it.trim().uppercase()) }?.toSet() ?: emptySet(),
                    endDate = parts["UNTIL"]?.let(LocalDate::parse),
                    remainingOccurrences = parts["COUNT"]?.toInt(),
                )
            }.getOrNull()
        }
    }
}
