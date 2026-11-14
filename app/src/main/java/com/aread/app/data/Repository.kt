package com.aread.app.data

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

object Repository {
    private val sb get() = Supabase.client
    private val json = Json { ignoreUnknownKeys = true }

    val userId: String? get() = sb.auth.currentUserOrNull()?.id
    val isSignedIn: Boolean get() = sb.auth.currentSessionOrNull() != null
    val sessionStatus get() = sb.auth.sessionStatus

    suspend fun restoreSession() = runCatching { sb.auth.awaitInitialization() }

    suspend fun signUpEmail(address: String, secret: String) =
        sb.auth.signUpWith(Email) {
            email = address
            password = secret
        }

    suspend fun signInEmail(address: String, secret: String) =
        sb.auth.signInWith(Email) {
            email = address
            password = secret
        }

    suspend fun signInGoogle(token: String) =
        sb.auth.signInWith(IDToken) {
            idToken = token
            provider = Google
        }

    suspend fun signInFacebook() = sb.auth.signInWith(Facebook)

    suspend fun sendPhoneOtp(number: String) = sb.auth.signInWith(OTP) { phone = number }

    suspend fun verifyPhoneOtp(number: String, code: String) =
        sb.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = number, token = code)

    suspend fun signOut() = sb.auth.signOut()

    suspend fun myProfile(): Profile? {
        val id = userId ?: return null
        return sb.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()
    }

    suspend fun saveProfile(p: Profile) {
        sb.postgrest.rpc(
            "save_profile",
            SaveProfileParams(p.name, p.phone, p.classLevel, p.rollNo, p.avgGrade, p.schoolName, p.schoolType, p.goals),
        )
    }

    suspend fun logSession(minutes: Int) {
        sb.postgrest.rpc("log_focus_session", mapOf("minutes" to minutes))
    }

    suspend fun leaderboard(): List<LeaderRow> = sb.postgrest.rpc("leaderboard_weekly").decodeList()

    suspend fun analyzePage(imageBase64: String, hint: String): PageAnalysis {
        val res = sb.functions.invoke("analyze-page", body = AnalyzeRequest(imageBase64, hint))
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun saveTextbookPage(bytes: ByteArray, a: PageAnalysis, subject: String) {
        val uid = userId ?: error("Not signed in")
        val path = "$uid/${System.currentTimeMillis()}.jpg"
        sb.storage.from("textbooks").upload(path, bytes)
        sb.from("textbook_pages").insert(
            TextbookInsert(
                userId = uid,
                subject = subject.ifBlank { a.subject }.take(40),
                path = path,
                chapter = a.chapter.take(120),
                topics = a.topics,
                summary = a.summary.take(300),
                difficulty = a.difficulty,
                estMinutes = a.estMinutes,
            ),
        )
    }

    suspend fun myPages(): List<TextbookPage> =
        sb.from("textbook_pages").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun askTutor(req: TutorRequest): TutorReply {
        val res = sb.functions.invoke("tutor", body = req)
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun adminStats(): Map<String, Int> = sb.postgrest.rpc("admin_stats").decodeAs()

    suspend fun adminAllProfiles(): List<Profile> =
        sb.from("profiles").select {
            order("created_at", Order.DESCENDING)
            limit(200)
        }.decodeList()
}
