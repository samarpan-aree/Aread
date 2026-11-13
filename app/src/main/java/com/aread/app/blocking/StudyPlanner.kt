package com.aread.app.blocking

import java.time.LocalDateTime
import java.time.LocalTime

data class StudySlot(val start: LocalDateTime, val minutes: Int, val reason: String)

/**
 * Picks study sessions automatically. Rules are simple and explainable:
 * - Avoid school hours and late night.
 * - Prefer the student's own "best time" (from the goal screen) when set.
 * - Hard subjects early in the slot list, 45 min blocks with a break between.
 */
object StudyPlanner {
    fun planToday(now: LocalDateTime, hardSubjects: List<String>, preferred: LocalTime?): List<StudySlot> {
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
                val subject = hardSubjects.getOrNull(i)
                StudySlot(
                    start = t,
                    minutes = 45,
                    reason = if (subject != null) "Good time for $subject while you're fresh" else "Quiet part of your day",
                )
            }
    }
}
