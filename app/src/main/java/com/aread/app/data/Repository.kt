package com.aread.app.data

import android.content.Context
import android.net.Uri
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

    suspend fun restoreSession() = runCatching { sb.auth.awaitInitialization() }

    suspend fun signUpEmail(email: String, password: String) =
        sb.auth.signUpWith(Email) { this.email = email; this.password = password }

    suspend fun signInEmail(email: String, password: String) =
        sb.auth.signInWith(Email) { this.email = email; this.password = password }

    suspend fun signInGoogle(idToken: String) =
        sb.auth.signInWith(IDToken) { this.idToken = idToken; provider = Google }

    suspend fun signInFacebook() = sb.auth.signInWith(Facebook)

    suspend fun sendPhoneOtp(phone: String) = sb.auth.signInWith(OTP) { this.phone = phone }

    suspend fun verifyPhoneOtp(phone: String, code: String) =
        sb.auth.verifyPhoneOtp(type = io.github.jan.supabase.auth.OtpType.Phone.SMS, phone = phone, token = code)

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

    suspend fun myPages(): List<TextbookPage> =
        sb.from("textbook_pages").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun logSession(minutes: Int) {
        sb.postgrest.rpc("log_focus_session", mapOf("minutes" to minutes))
    }

    suspend fun leaderboard(): List<LeaderRow> =
        sb.from("leaderboard_weekly").select {
            order("focus_minutes_week", Order.DESCENDING)
            limit(50)
        }.decodeList()

    suspend fun uploadTextbookPage(context: Context, uri: Uri, subject: String): String {
        val uid = userId ?: error("Not signed in")
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val path = "$uid/${System.currentTimeMillis()}.jpg"
        sb.storage.from("textbooks").upload(path, bytes)
        sb.from("textbook_pages").insert(mapOf("user_id" to uid, "subject" to subject, "path" to path))
        return path
    }

    suspend fun askTutor(req: TutorRequest): TutorReply {
        val res = sb.functions.invoke("tutor", body = req)
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun adminStats(): Map<String, Int> =
        sb.postgrest.rpc("admin_stats").decodeAs<Map<String, Int>>()

    suspend fun adminAllProfiles(): List<Profile> =
        sb.from("profiles").select { order("created_at", Order.DESCENDING); limit(200) }.decodeList()
}
