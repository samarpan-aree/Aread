package com.aread.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.aread.app.BuildConfig
import com.aread.app.data.Countries
import com.aread.app.data.Errors
import com.aread.app.data.Prefs
import com.aread.app.data.Profile
import com.aread.app.data.Repository
import com.aread.app.tutor.Mouth
import com.aread.app.ui.theme.AreadColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IntroScreen(onDone: () -> Unit) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1900)
        splash = false
    }
    Crossfade(splash, animationSpec = tween(700), label = "intro") { showSplash ->
        if (showSplash) Splash() else IntroPager(onDone)
    }
}

@Composable
private fun Splash() {
    val scale = remember { Animatable(0.55f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 110f)) }
        launch {
            delay(550)
            fade.animateTo(1f, tween(650))
        }
    }
    val inf = rememberInfiniteTransition(label = "pulse")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "ring")
    val accent = AreadColors.Accent
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(2) { i ->
                        val t = (ring + i * 0.5f) % 1f
                        drawCircle(
                            accent.copy(alpha = (1f - t) * 0.4f),
                            radius = size.minDimension / 2 * (0.5f + t * 0.5f),
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                }
                Box(
                    Modifier.size(108.dp).scale(scale.value).clip(RoundedCornerShape(32.dp)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { BookGlyph(MaterialTheme.colorScheme.onPrimary, Modifier.size(68.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Aread", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(fade.value))
            Spacer(Modifier.height(4.dp))
            Text("Study with focus", color = AreadColors.Muted, modifier = Modifier.alpha(fade.value))
        }
    }
}

@Composable
private fun IntroPager(onDone: () -> Unit) {
    val pages = listOf(
        "Focus on what matters" to "Timers, Pomodoro and strict mode keep you studying until the session is done.",
        "Block the noise" to "Reels, games and unknown calls pause while you study, so nothing pulls you away.",
        "Learn with Medha" to "Stuck? Medha gives hints and finds the exact step where you went wrong, without handing you the answer.",
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Skip", color = AreadColors.Muted) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> FocusArt()
                        1 -> BlockArt()
                        else -> TutorArt()
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(pages[page].first, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    pages[page].second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AreadColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = pager.currentPage == i
                val width by animateDpAsState(if (selected) 28.dp else 8.dp, spring(stiffness = 300f), label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).height(8.dp).width(width).clip(RoundedCornerShape(50))
                        .background(if (selected) AreadColors.Accent else AreadColors.Line),
                )
            }
        }
        PrimaryButton(if (last) "Get started" else "Next") {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

@Composable
private fun FocusArt() {
    val inf = rememberInfiniteTransition(label = "focus")
    val sweep by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "sweep")
    val pulse by inf.animateFloat(0.93f, 1.03f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val accent = AreadColors.Accent
    val soft = AreadColors.AccentSoft
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 14.dp.toPx()
            drawCircle(soft, radius = size.minDimension / 2 * pulse)
            drawArc(
                accent.copy(alpha = 0.25f), 0f, 360f, false, Offset(s, s), Size(size.width - s * 2, size.height - s * 2),
                style = Stroke(s),
            )
            drawArc(
                accent, sweep - 90f, 70f, false, Offset(s, s), Size(size.width - s * 2, size.height - s * 2),
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        BookGlyph(accent, Modifier.size(130.dp).scale(pulse))
    }
}

@Composable
private fun BlockArt() {
    val inf = rememberInfiniteTransition(label = "block")
    val t by inf.animateFloat(0f, 11f, infiniteRepeatable(tween(7500, easing = LinearEasing)), label = "t")
    val paper = AreadColors.Surface
    val line = AreadColors.Line
    val muted = AreadColors.Muted
    val tints = listOf(
        AreadColors.Accent, AreadColors.Amber, AreadColors.Warn, Color(0xFF3F51B5), Color(0xFF7B3F8C),
        Color(0xFF1F6F9F), AreadColors.Amber, AreadColors.Accent, AreadColors.Warn,
    )
    Canvas(Modifier.fillMaxSize()) {
        val pw = size.width * 0.6f
        val ph = size.height * 0.94f
        val left = (size.width - pw) / 2
        val top = (size.height - ph) / 2
        drawRoundRect(line, Offset(left, top), Size(pw, ph), CornerRadius(36.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRoundRect(paper, Offset(left, top), Size(pw, ph), CornerRadius(36.dp.toPx()))
        val pad = 20.dp.toPx()
        val gap = 12.dp.toPx()
        val cell = (pw - pad * 2 - gap * 2) / 3
        for (i in 0 until 9) {
            val col = i % 3
            val row = i / 3
            val x = left + pad + col * (cell + gap)
            val y = top + 48.dp.toPx() + row * (cell + gap)
            val blocked = t.toInt() > i && t < 10f
            val color = if (blocked) line else tints[i]
            drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(12.dp.toPx()))
            if (blocked) {
                val m = cell * 0.28f
                val w = 3.dp.toPx()
                drawLine(muted, Offset(x + m, y + m), Offset(x + cell - m, y + cell - m), w, StrokeCap.Round)
                drawLine(muted, Offset(x + cell - m, y + m), Offset(x + m, y + cell - m), w, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TutorArt() {
    val inf = rememberInfiniteTransition(label = "tutor")
    val m by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(280), RepeatMode.Reverse), label = "mouth")
    val speaking by inf.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 3600
        1f at 0
        1f at 2200
        0f at 2300
        0f at 3600
    }), label = "speak")
    val open = if (speaking > 0.5f) m * 0.75f else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MedhaFace(Mouth(jawOpen = open, smile = 0.2f, closed = 1f - open), Modifier.size(190.dp))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(AreadColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("Check step 2. What happens to the sign?", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SocialButton(letter: String, text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AreadColors.Line),
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(AreadColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Text(letter, color = AreadColors.Accent, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = AreadColors.Ink, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun LoginScreen(onCreateAccount: () -> Unit, onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("choose") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(Countries.default) }
    var digits by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val emailOk = remember(email) { android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() }

    fun run(block: suspend () -> Unit) = scope.launch {
        if (busy) return@launch
        busy = true
        error = null
        notice = null
        runCatching { block() }.onFailure { error = Errors.friendly(it) }
        busy = false
        if (Repository.isSignedIn && !finished) {
            finished = true
            onSignedIn()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(AreadColors.Accent),
            contentAlignment = Alignment.Center,
        ) { BookGlyph(MaterialTheme.colorScheme.onPrimary, Modifier.size(40.dp)) }
        Spacer(Modifier.height(20.dp))
        ScreenHeader("Welcome to Aread", "Sign in to keep your streak and progress safe.")
        when (mode) {
            "choose" -> {
                SocialButton("G", "Continue with Google") { run { googleSignIn(ctx) } }
                Spacer(Modifier.height(10.dp))
                SocialButton("f", "Continue with Facebook") { run { Repository.signInFacebook() } }
                Spacer(Modifier.height(10.dp))
                SocialButton("#", "Use phone number") { mode = "phone" }
                Spacer(Modifier.height(10.dp))
                SocialButton("@", "Use email") { mode = "email" }
            }
            "email" -> {
                Field(email, { email = it; error = null }, "Email", KeyboardType.Email)
                if (email.isNotBlank() && !emailOk) {
                    Text("Enter a valid email like name@example.com", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Field(password, { password = it; error = null }, "Password (at least 6 characters)", KeyboardType.Password, secret = true)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Sign in", enabled = emailOk && password.length >= 6 && !busy, loading = busy) {
                    run { Repository.signInEmail(email.trim(), password) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreateAccount) { Text("New here? Create an account") }
                TextButton(onClick = { mode = "choose"; error = null; notice = null }) { Text("Other ways to sign in") }
            }
            "phone" -> {
                PhoneField(country, { country = it }, digits, { digits = it })
                if (codeSent) {
                    Spacer(Modifier.height(10.dp))
                    DigitField(code, { code = it }, "6-digit code", 6)
                }
                Spacer(Modifier.height(16.dp))
                if (!codeSent) {
                    PrimaryButton("Send code", enabled = digits.length >= 6, loading = busy) {
                        run {
                            Repository.sendPhoneOtp(Countries.e164(country, digits))
                            codeSent = true
                        }
                    }
                } else {
                    PrimaryButton("Verify", enabled = code.length == 6, loading = busy) {
                        run { Repository.verifyPhoneOtp(Countries.e164(country, digits), code) }
                    }
                }
                TextButton(onClick = { mode = "choose"; codeSent = false }) { Text("Other ways to sign in") }
            }
        }
        AnimatedVisibility(error != null || notice != null) {
            val isError = error != null
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else AreadColors.AccentSoft)
                    .padding(14.dp),
            ) {
                Text(
                    error ?: notice.orEmpty(),
                    color = if (isError) MaterialTheme.colorScheme.error else AreadColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun WelcomeScreen(name: String, streak: Int, todayMinutes: Int, onDone: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val fade = remember { Animatable(0f) }
    var skip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
        launch { fade.animateTo(1f, tween(700)) }
        delay(2300)
        if (!skip) onDone()
    }
    val first = name.substringBefore(' ').ifBlank { "there" }
    Box(
        Modifier.fillMaxSize().clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        ) {
            if (!skip) {
                skip = true
                onDone()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.size(96.dp).scale(scale.value).clip(RoundedCornerShape(28.dp)).background(AreadColors.Accent),
                contentAlignment = Alignment.Center,
            ) { BookGlyph(MaterialTheme.colorScheme.onPrimary, Modifier.size(60.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("Welcome back,", color = AreadColors.Muted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(fade.value))
            Text(first, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(fade.value))
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    streak > 1 -> "$streak day streak. Keep it going."
                    todayMinutes > 0 -> "$todayMinutes minutes focused today."
                    else -> "Ready for a focused session?"
                },
                color = AreadColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(fade.value),
            )
        }
    }
}

private suspend fun googleSignIn(context: Context) {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) error("Google sign-in is not set up yet.")
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val result = CredentialManager.create(context)
        .getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Repository.signInGoogle(GoogleIdTokenCredential.createFrom(cred.data).idToken)
    }
}

@Composable
fun ProfileSetupScreen(initial: Profile?, onDone: (Profile) -> Unit) {
    val parsed = remember(initial) { Countries.parse(initial?.phone.orEmpty()) }
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var country by remember(initial) { mutableStateOf(parsed.first) }
    var digits by remember(initial) { mutableStateOf(parsed.second) }
    var cls by remember(initial) { mutableStateOf(initial?.classLevel.orEmpty()) }
    var roll by remember(initial) { mutableStateOf(initial?.rollNo.orEmpty()) }
    var grade by remember(initial) { mutableStateOf(initial?.avgGrade.orEmpty()) }
    var school by remember(initial) { mutableStateOf(initial?.schoolName.orEmpty()) }
    var type by remember(initial) { mutableStateOf(initial?.schoolType ?: "government") }
    val ok = name.isNotBlank() && cls.isNotBlank() && school.isNotBlank()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("About you", "This matches your syllabus and your school's leaderboard.")
        Field(name, { name = it }, "Full name", keep = { it.isLetter() || it in " .'-" }, maxLen = 60)
        Spacer(Modifier.height(10.dp))
        PhoneField(country, { country = it }, digits, { digits = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DigitField(cls, { cls = it }, "Class", 2, Modifier.weight(1f))
            DigitField(roll, { roll = it }, "Roll no.", 5, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Field(
            grade, { grade = it }, "Average grade (e.g. 3.4 or 72%)", KeyboardType.Decimal,
            keep = { it.isDigit() || it == '.' || it == '%' }, maxLen = 6,
        )
        Spacer(Modifier.height(10.dp))
        Field(school, { school = it }, "School name", maxLen = 80)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Government", type == "government", { type = "government" }, Modifier.weight(1f))
            ChoiceChip("Private", type == "private", { type = "private" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue", enabled = ok) {
            onDone(
                Profile(
                    id = Repository.userId.orEmpty(),
                    name = name.trim(),
                    phone = if (digits.isBlank()) "" else Countries.format(country, digits),
                    classLevel = cls,
                    rollNo = roll,
                    avgGrade = grade,
                    schoolName = school.trim(),
                    schoolType = type,
                    goals = initial?.goals.orEmpty(),
                ),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val GOAL_OPTIONS = listOf(
    "Prepare for exams", "Board / SEE exam", "Build a daily study habit", "Stop doom scrolling",
    "Cut down on gaming", "Finish my syllabus on time", "Improve weak subjects", "Sleep on time",
)

@Composable
fun GoalsScreen(initial: List<String>, onDone: (List<String>) -> Unit) {
    val picked = remember(initial) { mutableStateListOf<String>().apply { addAll(initial) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("What do you want from Aread?", "Pick everything that applies. You can change this later.")
        GOAL_OPTIONS.forEach { g ->
            ChoiceChip(g, g in picked, { if (g in picked) picked.remove(g) else picked.add(g) })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Continue", enabled = picked.isNotEmpty()) { onDone(picked.toList()) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StyleScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var best by remember { mutableStateOf(Prefs.bestTime(ctx)) }
    var goal by remember { mutableStateOf(Prefs.dailyGoal(ctx).toString()) }
    val times = listOf("Morning" to "Early, before school", "Afternoon" to "After school", "Evening" to "After dinner", "Night" to "Late, when it is quiet")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Your study style", "Aread plans your sessions around this.")
        Text("When do you focus best?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        times.forEach { (key, hint) ->
            ChoiceChip("$key · $hint", best == key, { best = key })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Daily focus goal", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120).forEach { m -> Pill("${m}m", goal == m.toString(), { goal = m.toString() }) }
        }
        Spacer(Modifier.height(10.dp))
        DigitField(goal, { goal = it }, "Custom minutes per day", 3, suffix = "min")
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue", enabled = (goal.toIntOrNull() ?: 0) in 5..600) {
            Prefs.setBestTime(ctx, best)
            Prefs.setDailyGoal(ctx, goal.toInt())
            onDone()
        }
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun SignUpScreen(onBack: () -> Unit, onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val emailOk = remember(email) { android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() }
    val match = password == confirm
    val ok = name.trim().length >= 2 && emailOk && password.length >= 6 && match
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(8.dp))
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text(
            "It takes a minute. You'll add your school details next.",
            style = MaterialTheme.typography.bodyLarge,
            color = AreadColors.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )
        Field(name, { name = it.take(60); error = null }, "Full name")
        Spacer(Modifier.height(10.dp))
        Field(email, { email = it; error = null }, "Email", KeyboardType.Email)
        if (email.isNotBlank() && !emailOk) {
            Text("Enter a valid email like name@example.com", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Field(password, { password = it; error = null }, "Password (at least 6 characters)", KeyboardType.Password, secret = true)
        Spacer(Modifier.height(10.dp))
        Field(confirm, { confirm = it; error = null }, "Confirm password", KeyboardType.Password, secret = true)
        if (confirm.isNotEmpty() && !match) {
            Text("Passwords don't match", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "By creating an account you agree to the privacy policy shown on the next screens.",
            style = MaterialTheme.typography.bodyMedium,
            color = AreadColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Create account", enabled = ok && !busy, loading = busy) {
            scope.launch {
                busy = true
                error = null
                notice = null
                val signUp = runCatching { Repository.signUpEmail(email.trim(), password) }
                if (!Repository.isSignedIn) {
                    runCatching { Repository.signInEmail(email.trim(), password) }
                }
                busy = false
                if (Repository.isSignedIn) {
                    if (!finished) {
                        finished = true
                        Prefs.setSignupName(ctx, name.trim())
                        onSignedIn()
                    }
                } else if (signUp.isFailure) {
                    error = Errors.friendly(signUp.exceptionOrNull()!!)
                } else {
                    notice = "If this email is new, we sent a link to ${email.trim()}. Open it, then sign in. If you already have an account, for example with Google, go back and use Sign in or Continue with Google."
                }
            }
        }
        AnimatedVisibility(error != null || notice != null) {
            val isError = error != null
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else AreadColors.AccentSoft)
                    .padding(14.dp),
            ) {
                Text(
                    error ?: notice.orEmpty(),
                    color = if (isError) MaterialTheme.colorScheme.error else AreadColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
