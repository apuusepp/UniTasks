package com.unitasks.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceRuleTest {

    @Test
    fun daily_and_monthly_advance_by_interval() {
        val start = LocalDate.of(2026, 11, 7)
        val daily = RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = 3)
        assertEquals(LocalDate.of(2026, 11, 10), daily.next(start)?.date)

        val monthly = RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 2)
        assertEquals(LocalDate.of(2027, 1, 7), monthly.next(start)?.date)
    }

    @Test
    fun weekly_picks_later_weekday_in_same_week_then_jumps_interval_weeks() {
        // 2026-11-02 is Monday
        val monday = LocalDate.of(2026, 11, 2)
        val rule = RecurrenceRule(
            frequency = RecurrenceRule.Frequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )
        val first = rule.next(monday)!!
        assertEquals(LocalDate.of(2026, 11, 5), first.date) // Thursday of the same week
        val second = first.rule.next(first.date)!!
        assertEquals(LocalDate.of(2026, 11, 16), second.date) // Monday two weeks later
    }

    @Test
    fun respects_end_date_and_remaining_occurrences() {
        val start = LocalDate.of(2026, 11, 7)
        val untilRule = RecurrenceRule(
            frequency = RecurrenceRule.Frequency.DAILY,
            interval = 5,
            endDate = LocalDate.of(2026, 11, 10),
        )
        assertNull(untilRule.next(start))

        val countRule = RecurrenceRule(
            frequency = RecurrenceRule.Frequency.DAILY,
            interval = 1,
            remainingOccurrences = 2,
        )
        val next = countRule.next(start)!!
        assertEquals(1, next.rule.remainingOccurrences)
        assertNull(next.rule.next(next.date))
    }

    @Test
    fun encode_and_decode_round_trip() {
        val rule = RecurrenceRule(
            frequency = RecurrenceRule.Frequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            endDate = LocalDate.of(2027, 3, 1),
            remainingOccurrences = 6,
        )
        assertEquals(rule, RecurrenceRule.decode(rule.encode()))
        assertNull(RecurrenceRule.decode(null))
        assertNull(RecurrenceRule.decode("not-a-rule"))
    }
}
