package com.aread.app.blocking

import java.time.LocalDateTime
import java.time.LocalTime

data class StudyItem(val subject: String, val chapter: String, val minutes: Int)

data class StudySlot(val start: LocalDateTime, val minutes: Int, val title: String, val reason: String)

object StudyPlanner {
    fun planToday(now: LocalDateTime, items: List<StudyItem>, preferred: LocalTime?): List<StudySlot> {
        val windows = listOfNotNull(
            preferred,
            LocalTime.of(6, 0),
            LocalTime.of(17, 30),
            LocalTime.of(19, 30),
        ).distinct().sortedBy { it.toSecondOfDay() }

        return windows.map { now.toLocalDate().atTime(it) }
            .filter { it.isAfter(now.plusMinutes(10)) && it.toLocalTime() < LocalTime.of(21, 30) }
            .take(3)
            .mapIndexed { i, t ->
                val item = items.getOrNull(i)
                if (item != null) {
                    StudySlot(
                        t, item.minutes.coerceIn(20, 60),
                        item.subject,
                        if (item.chapter.isNotBlank()) item.chapter else "From your uploaded pages",
                    )
                } else {
                    StudySlot(t, 45, "Free study", "A quiet part of your day. Upload textbook pages for a tailored plan.")
                }
            }
    }
}
