package com.aread.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val phone: String = "",
    @SerialName("class_level") val classLevel: String = "",
    @SerialName("roll_no") val rollNo: String = "",
    @SerialName("avg_grade") val avgGrade: String = "",
    @SerialName("school_name") val schoolName: String = "",
    @SerialName("school_type") val schoolType: String = "government",
    val goals: List<String> = emptyList(),
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("focus_minutes_week") val focusMinutesWeek: Int = 0,
)

@Serializable
data class ProfileInput(
    val id: String,
    val name: String,
    val phone: String,
    @SerialName("class_level") val classLevel: String,
    @SerialName("roll_no") val rollNo: String,
    @SerialName("avg_grade") val avgGrade: String,
    @SerialName("school_name") val schoolName: String,
    @SerialName("school_type") val schoolType: String,
    val goals: List<String>,
)

@Serializable
data class LeaderRow(
    val name: String,
    @SerialName("school_name") val schoolName: String = "",
    @SerialName("focus_minutes_week") val focusMinutesWeek: Int = 0,
    @SerialName("streak_days") val streakDays: Int = 0,
)

@Serializable
data class TutorRequest(
    val message: String,
    val attempts: Int,
    val subject: String? = null,
    val imageBase64: String? = null,
    val history: List<TutorTurn> = emptyList(),
)

@Serializable
data class TutorTurn(val role: String, val text: String)

@Serializable
data class TutorReply(val reply: String, val mode: String = "hint")
