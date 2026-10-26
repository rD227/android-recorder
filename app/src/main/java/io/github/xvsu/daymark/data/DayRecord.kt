package io.github.xvsu.daymark.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class CardTone { SAGE, CLAY, LAVENDER, SAND }

/** A date on the user's calendar, rather than an instant or a 24-hour duration. */
data class DayRecord(
    val id: String,
    val title: String,
    val startDate: LocalDate,
    val tone: CardTone = CardTone.SAGE,
    val customColor: Int? = null,
) {
    init {
        require(id.isNotBlank()) { "Record ID must not be blank" }
        require(title.isNotBlank() && title == title.trim() && title.length <= 40) {
            "Title must be trimmed and contain 1–40 characters"
        }
        require(customColor == null || customColor ushr 24 == 0xFF) {
            "Custom card color must be opaque"
        }
    }

    fun daysSince(today: LocalDate): Long = ChronoUnit.DAYS.between(startDate, today)

    fun dayCount(today: LocalDate): Long = abs(daysSince(today))

    fun headline(today: LocalDate): String =
        if (today.isBefore(startDate)) "距 $title 还有" else "距 $title 已经开始"
}
