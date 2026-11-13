package com.aread.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.aread.app.blocking.StudyPlanner
import com.aread.app.data.Prefs
import com.aread.app.data.Profile
import com.aread.app.data.ScreenTime
import com.aread.app.data.ScreenTimeSummary
import com.aread.app.tutor.Mouth
import com.aread.app.ui.theme.AreadColors
import com.aread.app.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

const val TUTOR_NAME = "Medha"

@Composable
fun HomeScreen(
    profile: Profile?,
    onOpenTutor: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSetup: () -> Unit,
    onSessionDone: (Int) -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(profile, onOpenProfile)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FocusCard(onOpenSetup) { minutes ->
                onSessionDone(minutes)
                version++
            }
            WeekCard(version)
            ScreenTimeCard()
            PlanCard()
            TutorCard(onOpenTutor)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Header(profile: Profile?, onOpenProfile: () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = profile?.name?.substringBefore(' ').orEmpty().ifBlank { "there" }
    val bg = ThemeState.bgPath?.takeIf { File(it).exists() }
    val onBg = if (bg != null) Color.White else AreadColors.Ink
    val onBgMuted = if (bg != null) Color.White.copy(alpha = 0.85f) else AreadColors.Muted
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (bg != null) {
            AsyncImage(File(bg), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        Row(Modifier.fillMaxWidth().padding(20.dp, 28.dp, 20.dp, 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greet, color = onBgMuted, style = MaterialTheme.typography.bodyLarge)
                Text(first, color = onBg, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                StreakChip(profile?.streakDays ?: 0)
            }
            Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenProfile)) {
                Avatar(profile?.name.orEmpty(), 54.dp)
            }
        }
    }
}

@Composable
private fun StreakChip(days: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, AreadColors.Line, RoundedCornerShape(50))
            .background(AreadColors.Surface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocalFireDepartment, null, tint = AreadColors.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (days == 1) "1 day streak" else "$days day streak",
            style = MaterialTheme.typography.labelLarge,
            color = AreadColors.Ink,
        )
    }
}

private fun fmtClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun fmtDuration(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

@Composable
private fun FocusCard(onOpenSetup: () -> Unit, onFinished: (Int) -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pomodoro by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(25) }
    var customSet by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var strict by remember { mutableStateOf(Prefs.strictDefault(ctx)) }
    var today by remember { mutableIntStateOf(Prefs.todayMinutes(ctx)) }
    var blockerOn by remember { mutableStateOf(true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        blockerOn = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(ctx.packageName) == true
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val phase = Prefs.phase(ctx)
            if (phase != 0 && now >= Prefs.phaseEnd(ctx)) {
                val len = Prefs.focusLen(ctx)
                if (phase == 1) {
                    Prefs.addMinutes(ctx, len)
                    today = Prefs.todayMinutes(ctx)
                    onFinished(len)
                    if (Prefs.pomodoro(ctx)) Prefs.startPhase(ctx, 2, Prefs.BREAK_MIN) else Prefs.endSession(ctx)
                } else {
                    Prefs.startPhase(ctx, 1, len)
                }
            }
            delay(500)
        }
    }

    val phase = Prefs.phase(ctx)
    val goal = Prefs.dailyGoal(ctx)
    val remainingSec = (Prefs.phaseEnd(ctx) - now) / 1000

    AreadCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (phase) {
                    1 -> "Focusing"
                    2 -> "Break time"
                    else -> "Focus"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (phase == 0) {
                Pill("Timer", !pomodoro, { pomodoro = false })
                Spacer(Modifier.width(6.dp))
                Pill("Pomodoro", pomodoro, { pomodoro = true })
            }
        }
        Spacer(Modifier.height(18.dp))
        val total = when (phase) {
            1 -> Prefs.focusLen(ctx) * 60f
            2 -> Prefs.BREAK_MIN * 60f
            else -> 1f
        }
        ProgressRing(
            progress = if (phase == 0) today / goal.toFloat() else remainingSec / total,
            modifier = Modifier.size(210.dp).align(Alignment.CenterHorizontally),
            stroke = 16.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (phase == 0) {
                    Text("$today", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text("of $goal min today", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                } else {
                    Text(fmtClock(remainingSec), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (phase == 1) "Apps and calls paused" else "Apps unlocked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AreadColors.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (phase == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60).forEach { m ->
                    Pill("${m}m", !customSet && minutes == m, { minutes = m; customSet = false }, Modifier.weight(1f))
                }
                Pill(if (customSet) "${minutes}m" else "Custom", customSet, { showCustom = true }, Modifier.weight(1.3f))
            }
            if (pomodoro) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "$minutes min focus, ${Prefs.BREAK_MIN} min break, repeating until you stop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AreadColors.Muted,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                    Text("You can't end the session early.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                }
                Switch(strict, { strict = it })
            }
            if (!blockerOn) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AreadColors.AccentSoft)
                        .clickable(onClick = onOpenSetup)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Blocking is off. Finish setup to pause apps.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Accent)
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Start focus session") {
                Prefs.beginSession(ctx, minutes, pomodoro, strict)
                now = System.currentTimeMillis()
            }
        } else {
            if (Prefs.strict(ctx) && phase == 1) {
                Text(
                    "Strict mode is on. You can leave when the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AreadColors.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                SecondaryButton(if (phase == 1) "End session" else "Stop pomodoro") {
                    if (phase == 1) {
                        val len = Prefs.focusLen(ctx)
                        val start = Prefs.phaseEnd(ctx) - len * 60_000L
                        val done = ((System.currentTimeMillis() - start) / 60_000L).toInt()
                        if (done >= 1) {
                            Prefs.addMinutes(ctx, done)
                            today = Prefs.todayMinutes(ctx)
                            onFinished(done)
                        }
                    }
                    Prefs.endSession(ctx)
                    now = System.currentTimeMillis()
                }
            }
        }
    }

    if (showCustom) {
        var text by remember { mutableStateOf(minutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Custom duration") },
            text = { DigitField(text, { text = it }, "Minutes (1 to 240)", 3, suffix = "min") },
            confirmButton = {
                TextButton(onClick = {
                    val v = text.toIntOrNull()
                    if (v != null && v in 1..240) {
                        minutes = v
                        customSet = true
                        showCustom = false
                    }
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WeekCard(version: Int) {
    val ctx = LocalContext.current
    val data = remember(version) { Prefs.weekMinutes(ctx) }
    val total = data.sumOf { it.second }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val k by animateFloatAsState(if (go) 1f else 0f, tween(900), label = "bars")
    val accent = AreadColors.Accent
    val soft = AreadColors.AccentSoft
    val maxV = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)

    AreadCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(fmtDuration(total), style = MaterialTheme.typography.titleMedium, color = AreadColors.Accent)
        }
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 12.dp.toPx()
            val bw = (size.width - gap * 6) / 7
            data.forEachIndexed { i, (_, v) ->
                val h = (size.height * (v / maxV.toFloat()) * k).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    if (i == data.lastIndex) accent else soft,
                    Offset(i * (bw + gap), size.height - h),
                    Size(bw, h),
                    CornerRadius(8.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (label, _) ->
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = AreadColors.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScreenTimeCard() {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(ScreenTime.hasAccess(ctx)) }
    var summary by remember { mutableStateOf<ScreenTimeSummary?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = ScreenTime.hasAccess(ctx) }
    LaunchedEffect(access) {
        summary = if (access) withContext(Dispatchers.IO) { runCatching { ScreenTime.today(ctx) }.getOrNull() } else null
    }
    AreadCard {
        Text("Screen time today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        val s = summary
        if (!access) {
            Text(
                "See how much time your phone takes, and which apps took it. Aread reads this on your phone only.",
                style = MaterialTheme.typography.bodyMedium,
                color = AreadColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Allow usage access") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } else if (s == null) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AreadColors.Accent)
        } else {
            Text(fmtDuration(s.totalMinutes), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                "${fmtDuration(s.distractingMinutes)} on distracting apps",
                style = MaterialTheme.typography.bodyMedium,
                color = if (s.distractingMinutes > 0) AreadColors.Warn else AreadColors.Muted,
            )
            Spacer(Modifier.height(14.dp))
            val maxM = (s.top.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
            s.top.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth(app.minutes / maxM.toFloat()).height(6.dp).clip(RoundedCornerShape(50))
                                .background(if (app.distracting) AreadColors.Warn else AreadColors.Accent),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(fmtDuration(app.minutes), style = MaterialTheme.typography.labelLarge, color = AreadColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun PlanCard() {
    val ctx = LocalContext.current
    val preferred = remember {
        when (Prefs.bestTime(ctx)) {
            "Morning" -> LocalTime.of(6, 30)
            "Afternoon" -> LocalTime.of(15, 0)
            "Night" -> LocalTime.of(20, 30)
            else -> LocalTime.of(18, 30)
        }
    }
    val slots = remember { StudyPlanner.planToday(LocalDateTime.now(), listOf("Maths", "Science"), preferred) }
    AreadCard {
        Text("Today's plan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (slots.isEmpty()) {
            Text("No more good slots today. Tomorrow's plan appears in the morning.", color = AreadColors.Muted)
        }
        slots.forEach {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Timer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("%02d:%02d · %d min".format(it.start.hour, it.start.minute, it.minutes), style = MaterialTheme.typography.titleMedium)
                    Text(it.reason, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun TutorCard(onOpen: () -> Unit) {
    AreadCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedhaFace(Mouth(smile = 0.3f), Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Stuck on a question?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$TUTOR_NAME guides you without giving the answer away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AreadColors.Muted,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Muted)
        }
    }
}
