package com.dosecerta.domain

import com.dosecerta.data.local.entity.Schedule
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RecurrenceCalculatorTest {
    private val utc = ZoneId.of("UTC")
    private fun schedule(kind: RecurrenceKind = RecurrenceKind.DAILY, minutes: Int = 480, days: List<Int> = emptyList(), day: Int = 1, zone: String = "UTC") =
        Schedule(id = 3, medicationId = 2, timeInMinutes = minutes, daysOfWeek = days, recurrenceKind = kind, monthDay = day, zoneId = zone)
    private fun next(rule: Schedule, after: String): String? = RecurrenceCalculator(Clock.fixed(Instant.parse(after), utc), utc).nextOccurrence(rule)?.let { Instant.ofEpochMilli(it.originalDueAt).toString() }

    @Test fun dailyMovesToTomorrowOnceTodaySlotHasPassed() {
        assertEquals("2026-09-30T08:00:00Z", next(schedule(), "2026-09-29T08:00:00Z"))
        assertEquals("2026-09-29T08:00:00Z", next(schedule(), "2026-09-29T07:59:59Z"))
    }
    @Test fun prnAndInactiveNeverProduceAnAlarm() {
        assertNull(next(schedule(RecurrenceKind.AS_NEEDED), "2026-09-29T00:00:00Z"))
        assertNull(next(schedule().copy(isActive = false), "2026-09-29T00:00:00Z"))
    }
    @Test fun intervalKeepsEachExplicitWallClockSlot() {
        assertEquals("2026-09-29T16:00:00Z", next(schedule(RecurrenceKind.INTERVAL, 960), "2026-09-29T12:00:00Z"))
        assertEquals("2026-09-30T00:00:00Z", next(schedule(RecurrenceKind.INTERVAL, 0), "2026-09-29T16:00:00Z"))
    }
    @Test fun weeklyPassesSaturdaySundayUsingCalendarWeekdayNumbering() {
        assertEquals("2026-10-05T08:00:00Z", next(schedule(RecurrenceKind.WEEKLY, days = listOf(2)), "2026-10-03T09:00:00Z"))
        assertEquals("2026-10-04T08:00:00Z", next(schedule(RecurrenceKind.SELECTED_DAYS, days = listOf(1, 4)), "2026-10-03T09:00:00Z"))
    }
    @Test fun monthly31SkipsShortMonthsRatherThanAddingThirtyDays() {
        assertEquals("2026-03-31T08:00:00Z", next(schedule(RecurrenceKind.MONTHLY, day = 31), "2026-01-31T08:00:00Z"))
        assertEquals("2027-01-31T08:00:00Z", next(schedule(RecurrenceKind.MONTHLY, day = 31), "2026-12-31T08:00:00Z"))
    }
    @Test fun february29UsesLeapCalendar() {
        assertEquals("2028-02-29T08:00:00Z", next(schedule(RecurrenceKind.MONTHLY, day = 29), "2028-02-01T00:00:00Z"))
        assertEquals("2027-03-29T08:00:00Z", next(schedule(RecurrenceKind.MONTHLY, day = 29), "2027-02-01T00:00:00Z"))
    }
    @Test fun monthly30SkipsFebruaryAndKeepsTheCalendarDayAcrossLongMonths() {
        val rule = schedule(RecurrenceKind.MONTHLY, day = 30)
        assertEquals("2027-03-30T08:00:00Z", next(rule, "2027-01-30T08:00:00Z"))
        assertEquals("2028-03-30T08:00:00Z", next(rule, "2028-01-30T08:00:00Z"))
        assertEquals("2026-04-30T08:00:00Z", next(rule, "2026-03-30T08:00:00Z"))
        assertEquals("2027-01-30T08:00:00Z", next(rule, "2026-12-30T08:00:00Z"))
    }
    @Test fun halfOpenRangeDoesNotDuplicateMidnight() {
        val rule = schedule(minutes = 0)
        val calculator = RecurrenceCalculator()
        val first = calculator.occurrencesBetween(rule, Instant.parse("2026-09-29T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"))
        val second = calculator.occurrencesBetween(rule, Instant.parse("2026-09-30T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"))
        assertEquals(1, first.size); assertEquals(1, second.size)
        assertNotEquals(first.single().originalDueAt, second.single().originalDueAt)
    }
    @Test fun springDstGapMovesForwardButRetainsRequestedIdentitySlot() {
        val occurrence = RecurrenceCalculator().nextOccurrence(schedule(minutes = 150, zone = "America/New_York"), Instant.parse("2026-03-08T00:00:00Z"))!!
        assertEquals("2026-03-08T07:30:00Z", Instant.ofEpochMilli(occurrence.originalDueAt).toString())
        assertEquals("2026-03-08T02:30", occurrence.requestedLocalDateTime.toString())
        assertEquals("2026-03-08T03:30", occurrence.localDateTime.toString())
        assertTrue(occurrence.adjustedForDst)
    }
    @Test fun autumnOverlapHasExactlyOneEarlierOffsetOccurrence() {
        val occurrences = RecurrenceCalculator().occurrencesBetween(schedule(minutes = 90, zone = "America/New_York"), Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"))
        assertEquals(1, occurrences.size)
        assertEquals("2026-11-01T05:30:00Z", Instant.ofEpochMilli(occurrences.single().originalDueAt).toString())
        assertTrue(occurrences.single().adjustedForDst)
    }
    @Test fun versionValidityExcludesOldOrFutureSlots() {
        val rule = schedule().copy(validFrom = Instant.parse("2026-09-30T00:00:00Z").toEpochMilli(), validUntil = Instant.parse("2026-10-01T08:00:00Z").toEpochMilli())
        val all = RecurrenceCalculator().occurrencesBetween(rule, Instant.parse("2026-09-29T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"))
        assertEquals(listOf("2026-09-30T08:00:00Z"), all.map { Instant.ofEpochMilli(it.originalDueAt).toString() })
    }
}
