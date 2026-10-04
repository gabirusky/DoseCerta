package com.dosecerta.domain

import com.dosecerta.data.local.entity.Schedule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Calendar rules use local wall-clock slots; INTERVAL retains explicitly displayed slots. */
enum class RecurrenceKind { DAILY, INTERVAL, WEEKLY, SELECTED_DAYS, MONTHLY, AS_NEEDED }

data class OccurrenceDate(
    val originalDueAt: Long,
    val localDateTime: LocalDateTime,
    val zoneId: String,
    val adjustedForDst: Boolean,
    val requestedLocalDateTime: LocalDateTime = localDateTime,
    val isPinned: Boolean = false
)

/** One occurrence per local slot, including daylight-saving overlaps. Monthly missing days are skipped. */
class RecurrenceCalculator(
    private val clock: Clock = Clock.systemUTC(),
    private val fallbackZone: ZoneId = ZoneId.systemDefault()
) {
    fun nextOccurrence(schedule: Schedule, after: Instant = clock.instant()): OccurrenceDate? {
        if (!schedule.isActive || schedule.recurrenceKind == RecurrenceKind.AS_NEEDED) return null
        validate(schedule)
        val start = maxOf(after, Instant.ofEpochMilli(schedule.validFrom))
        val zone = zone(schedule)
        var date = start.atZone(zone).toLocalDate()
        repeat(371) {
            if (matches(schedule, date)) {
                val candidate = resolve(schedule, date, zone)
                val due = candidate.originalDueAt
                if (schedule.validUntil != null && due >= schedule.validUntil) return null
                if (due >= schedule.validFrom && due > after.toEpochMilli()) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun preview(schedule: Schedule, count: Int = 5, after: Instant = clock.instant()): List<OccurrenceDate> {
        require(count in 1..100)
        val result = mutableListOf<OccurrenceDate>()
        var cursor = after
        repeat(count) {
            val occurrence = nextOccurrence(schedule, cursor) ?: return result
            result += occurrence
            cursor = Instant.ofEpochMilli(occurrence.originalDueAt)
        }
        return result
    }

    /** Range uses [start, endExclusive), avoiding midnight duplication. */
    fun occurrencesBetween(schedule: Schedule, start: Instant, endExclusive: Instant): List<OccurrenceDate> {
        validate(schedule)
        if (endExclusive <= start || schedule.recurrenceKind == RecurrenceKind.AS_NEEDED) return emptyList()
        val zone = zone(schedule)
        val effectiveStart = maxOf(start, Instant.ofEpochMilli(schedule.validFrom))
        val effectiveEnd = schedule.validUntil?.let { minOf(endExclusive, Instant.ofEpochMilli(it)) } ?: endExclusive
        if (effectiveEnd <= effectiveStart) return emptyList()
        var date = effectiveStart.atZone(zone).toLocalDate()
        val lastDate = effectiveEnd.atZone(zone).toLocalDate()
        val result = mutableListOf<OccurrenceDate>()
        while (date <= lastDate) {
            if (matches(schedule, date)) {
                val candidate = resolve(schedule, date, zone)
                val instant = Instant.ofEpochMilli(candidate.originalDueAt)
                if (instant >= effectiveStart && instant < effectiveEnd) {
                    result += candidate
                }
            }
            date = date.plusDays(1)
        }
        return result.distinctBy { it.originalDueAt }.sortedBy { it.originalDueAt }
    }

    private fun zone(schedule: Schedule): ZoneId = if (schedule.zoneId.isBlank()) fallbackZone else ZoneId.of(schedule.zoneId)

    private fun validate(schedule: Schedule) {
        require(schedule.timeInMinutes in 0..1439)
        require(schedule.daysOfWeek.all { it in 1..7 })
        require(schedule.recurrenceKind != RecurrenceKind.MONTHLY || schedule.monthDay in 1..31)
    }

    private fun resolve(schedule: Schedule, date: LocalDate, zone: ZoneId): OccurrenceDate {
        val requested = date.atTime(LocalTime.of(schedule.timeInMinutes / 60, schedule.timeInMinutes % 60))
        // atZone shifts nonexistent times forward by the gap; overlap selects earlier offset once.
        val resolved = requested.atZone(zone)
        return OccurrenceDate(resolved.toInstant().toEpochMilli(), resolved.toLocalDateTime(), zone.id,
            zone.rules.getValidOffsets(requested).size != 1, requested)
    }

    private fun matches(schedule: Schedule, date: LocalDate): Boolean {
        val calendarDay = date.dayOfWeek.value % 7 + 1
        return when (schedule.recurrenceKind) {
            RecurrenceKind.DAILY, RecurrenceKind.INTERVAL -> schedule.daysOfWeek.isEmpty() || calendarDay in schedule.daysOfWeek
            RecurrenceKind.WEEKLY, RecurrenceKind.SELECTED_DAYS -> calendarDay in schedule.daysOfWeek
            RecurrenceKind.MONTHLY -> date.dayOfMonth == schedule.monthDay
            RecurrenceKind.AS_NEEDED -> false
        }
    }
}
