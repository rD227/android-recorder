package io.github.xvsu.daymark.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayRecordTest {
    private fun record(date: String) = DayRecord("test", "新的开始", LocalDate.parse(date))

    @Test fun startDateIsDayZero() {
        assertEquals(0L, record("2026-10-26").dayCount(LocalDate.of(2026, 10, 26)))
        assertEquals("距 新的开始 已经开始", record("2026-10-26").headline(LocalDate.of(2026, 10, 26)))
    }

    @Test fun countsAcrossMonthYearAndLeapDay() {
        assertEquals(2L, record("2024-02-28").daysSince(LocalDate.of(2024, 3, 1)))
        assertEquals(1L, record("2025-12-31").daysSince(LocalDate.of(2026, 1, 1)))
    }

    @Test fun futureDatesShowPositiveCountdown() {
        val record = record("2026-11-01")
        val today = LocalDate.of(2026, 10, 26)
        assertEquals(-6L, record.daysSince(today))
        assertEquals(6L, record.dayCount(today))
        assertEquals("距 新的开始 还有", record.headline(today))
    }

    @Test fun calendarDaysIgnoreDaylightSavingHourChanges() {
        val zone = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)
        val next = start.plusDays(1)
        assertEquals(23L, java.time.Duration.between(start, next).toHours())
        assertEquals(1L, record(start.toLocalDate().toString()).daysSince(next.toLocalDate()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankTitle() { DayRecord("test", " ", LocalDate.now()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUntrimmedTitle() { DayRecord("test", " 旅行 ", LocalDate.now()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedTitle() { DayRecord("test", "日".repeat(41), LocalDate.now()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingIdentity() { DayRecord("", "旅行", LocalDate.now()) }
}
