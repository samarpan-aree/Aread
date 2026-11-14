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
data class SaveProfileParams(
    @SerialName("p_name") val name: String,
    @SerialName("p_phone") val phone: String,
    @SerialName("p_class") val classLevel: String,
    @SerialName("p_roll") val rollNo: String,
    @SerialName("p_grade") val avgGrade: String,
    @SerialName("p_school") val schoolName: String,
    @SerialName("p_type") val schoolType: String,
    @SerialName("p_goals") val goals: List<String>,
)

@Serializable
data class TextbookPage(
    val subject: String = "General",
    val path: String = "",
    val chapter: String = "",
    val topics: List<String> = emptyList(),
    val summary: String = "",
    val difficulty: String = "medium",
    @SerialName("est_minutes") val estMinutes: Int = 30,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class TextbookInsert(
    @SerialName("user_id") val userId: String,
    val subject: String,
    val path: String,
    val chapter: String,
    val topics: List<String>,
    val summary: String,
    val difficulty: String,
    @SerialName("est_minutes") val estMinutes: Int,
)

@Serializable
data class AnalyzeRequest(val imageBase64: String, val subjectHint: String = "")

@Serializable
data class PageAnalysis(
    @SerialName("is_study_material") val isStudyMaterial: Boolean = false,
    val reason: String = "",
    val readable: Boolean = true,
    val subject: String = "Other",
    @SerialName("chapter_or_topic") val chapter: String = "",
    val topics: List<String> = emptyList(),
    val summary: String = "",
    val difficulty: String = "medium",
    @SerialName("estimated_study_minutes") val estMinutes: Int = 30,
    @SerialName("has_questions") val hasQuestions: Boolean = false,
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
    val studying: List<String> = emptyList(),
    val pro: Boolean = false,
)

@Serializable
data class TutorTurn(val role: String, val text: String)

@Serializable
data class TutorReply(val reply: String, val mode: String = "hint", val credits: Int? = null)

@Serializable
data class CreditRow(val balance: Int = 0, @SerialName("ref_code") val refCode: String = "")

@Serializable
data class ConfigRow(val key: String, val value: String)

@Serializable
data class Pack(val name: String, val credits: Int, val price: String, val note: String = "")
