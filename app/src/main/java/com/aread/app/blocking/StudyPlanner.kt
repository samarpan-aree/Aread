package com.aread.app.blocking

import java.time.LocalDateTime
import java.time.LocalTime

data class StudyItem(val subject: String, val chapter: String, val minutes: Int)

data class StudySlot(val start: LocalDateTime, val minutes: Int, val title: String, val reason: String)

object StudyPlanner {
    fun planToday(now: LocalDateTime, items: List<StudyItem>, preferred: LocalTime?): List<StudySlot> {
        // Natural study windows: early morning, after school, evening, before bed.
        val base = listOf(LocalTime.of(6, 30), LocalTime.of(16, 30), LocalTime.of(18, 30), LocalTime.of(20, 0))
        val candidates = (listOfNotNull(preferred?.let { roundHalfHour(it) }) + base)
            .distinct().sortedBy { it.toSecondOfDay() }

        val chosen = mutableListOf<LocalDateTime>()
        for (t in candidates) {
            val at = now.toLocalDate().atTime(t)
            if (at.isBefore(now.plusMinutes(10))) continue
            if (t > LocalTime.of(21, 0)) continue
            if (chosen.isNotEmpty() && java.time.Duration.between(chosen.last(), at).toMinutes() < 100) continue
            chosen += at
            if (chosen.size == 3) break
        }
        return chosen.mapIndexed { i, t ->
            val item = items.getOrNull(i)
            val gap = chosen.getOrNull(i + 1)?.let { java.time.Duration.between(t, it).toMinutes().toInt() - 20 } ?: 60
            if (item != null) {
                val mins = (item.minutes.coerceIn(25, 60) / 5 * 5).coerceAtMost(gap.coerceAtLeast(25))
                StudySlot(t, mins, item.subject, if (item.chapter.isNotBlank()) item.chapter else "From your uploaded pages")
            } else {
                StudySlot(t, 40.coerceAtMost(gap.coerceAtLeast(25)), "Free study", "A quiet part of your day. Upload textbook pages for a tailored plan.")
            }
        }
    }

    private fun roundHalfHour(t: LocalTime): LocalTime {
        val m = t.hour * 60 + t.minute
        val r = ((m + 15) / 30) * 30
        return LocalTime.of((r / 60).coerceAtMost(23), r % 60)
    }
}
