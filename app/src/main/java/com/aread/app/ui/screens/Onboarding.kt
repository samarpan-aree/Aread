package com.aread.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.aread.app.BuildConfig
import com.aread.app.data.Profile
import com.aread.app.data.Repository
import com.aread.app.ui.theme.AreadColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- 1. Intro ----------
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val words = listOf("Read.", "Focus.", "Improve.")
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(words.size) { delay(700); shown = it + 1 }
        delay(900)
        onDone()
    }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Aread", style = MaterialTheme.typography.headlineLarge, color = AreadColors.Accent)
        Spacer(Modifier.height(24.dp))
        words.forEachIndexed { i, w ->
            val a by animateFloatAsState(if (shown > i) 1f else 0f, tween(500), label = "w$i")
            Text(w, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alpha(a))
        }
    }
}

// ---------- 2. Login (no guest mode) ----------
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("choose") } // choose | email | phone
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+977") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        runCatching { block() }.onFailure { error = it.message ?: "Something went wrong. Try again." }
        busy = false
        if (Repository.isSignedIn) onSignedIn()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        ScreenHeader("Sign in to Aread", "Your streak and progress are saved to your account.")
        when (mode) {
            "choose" -> {
                PrimaryButton("Continue with Google", loading = busy) { run { googleSignIn(ctx) } }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Continue with Facebook") { run { Repository.signInFacebook() } }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Use phone number") { mode = "phone" }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Use email") { mode = "email" }
            }
            "email" -> {
                Field(email, { email = it }, "Email", KeyboardType.Email)
                Spacer(Modifier.height(10.dp))
                Field(password, { password = it }, "Password", KeyboardType.Password, secret = true)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Sign in", enabled = email.isNotBlank() && password.length >= 6, loading = busy) {
                    run { Repository.signInEmail(email.trim(), password) }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Create account") { run { Repository.signUpEmail(email.trim(), password) } }
                TextButton(onClick = { mode = "choose" }) { Text("Other ways to sign in") }
            }
            "phone" -> {
                Field(phone, { phone = it }, "Phone number", KeyboardType.Phone)
                if (codeSent) {
                    Spacer(Modifier.height(10.dp))
                    Field(code, { code = it }, "6-digit code", KeyboardType.Number)
                }
                Spacer(Modifier.height(16.dp))
                if (!codeSent) PrimaryButton("Send code", enabled = phone.length > 8, loading = busy) {
                    run { Repository.sendPhoneOtp(phone.trim()); codeSent = true }
                } else PrimaryButton("Verify", enabled = code.length == 6, loading = busy) {
                    run { Repository.verifyPhoneOtp(phone.trim(), code.trim()) }
                }
                TextButton(onClick = { mode = "choose"; codeSent = false }) { Text("Other ways to sign in") }
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun googleSignIn(context: Context) {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val result = CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Repository.signInGoogle(GoogleIdTokenCredential.createFrom(cred.data).idToken)
    }
}

// ---------- 3. Profile details ----------
@Composable
fun ProfileSetupScreen(onDone: (Profile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("government") }
    val ok = name.isNotBlank() && cls.isNotBlank() && school.isNotBlank()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenHeader("About you", "This helps Aread match your syllabus and your school's leaderboard.")
        Field(name, { name = it }, "Full name"); Spacer(Modifier.height(10.dp))
        Field(phone, { phone = it }, "Phone number", KeyboardType.Phone); Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(cls, { cls = it }, "Class", modifier = Modifier.weight(1f))
            Field(roll, { roll = it }, "Roll no.", KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Field(grade, { grade = it }, "Average grade (e.g. 3.4 GPA or 72%)"); Spacer(Modifier.height(10.dp))
        Field(school, { school = it }, "School name"); Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Government", type == "government", { type = "government" }, Modifier.weight(1f))
            ChoiceChip("Private", type == "private", { type = "private" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue", enabled = ok) {
            onDone(Profile(id = Repository.userId.orEmpty(), name = name.trim(), phone = phone.trim(), classLevel = cls.trim(),
                rollNo = roll.trim(), avgGrade = grade.trim(), schoolName = school.trim(), schoolType = type))
        }
    }
}

// ---------- 4. Goals ----------
private val GOAL_OPTIONS = listOf(
    "Prepare for exams", "Board / SEE exam", "Build a daily study habit", "Stop doom scrolling",
    "Cut down on gaming", "Finish my syllabus on time", "Improve weak subjects", "Get better sleep routine",
)

@Composable
fun GoalsScreen(onDone: (List<String>) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenHeader("What do you want from Aread?", "Pick everything that applies. You can change this later.")
        GOAL_OPTIONS.forEach { g ->
            ChoiceChip(g, g in picked, { if (g in picked) picked.remove(g) else picked.add(g) })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Continue", enabled = picked.isNotEmpty()) { onDone(picked.toList()) }
    }
}
