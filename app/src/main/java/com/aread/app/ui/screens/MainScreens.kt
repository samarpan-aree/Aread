package com.aread.app.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aread.app.blocking.StudyPlanner
import com.aread.app.data.*
import com.aread.app.tutor.AvatarView
import com.aread.app.tutor.LipSyncSpeaker
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

const val TUTOR_NAME = "Medha"

@Composable
fun HomeScreen(profile: Profile?, onOpenTutor: () -> Unit, onSessionDone: (Int) -> Unit) {
    val ctx = LocalContext.current
    var focusing by remember { mutableStateOf(Prefs.isFocusing(ctx)) }
    var minutesLeft by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableIntStateOf(45) }

    LaunchedEffect(focusing) {
        while (focusing) {
            val left = ((Prefs.focusUntilMs(ctx) - System.currentTimeMillis()) / 60_000L).toInt() + 1
            minutesLeft = left.coerceAtLeast(0)
            if (!Prefs.isFocusing(ctx)) { focusing = false; onSessionDone(chosen) }
            delay(1000)
        }
    }
    val slots = remember { StudyPlanner.planToday(LocalDateTime.now(), listOf("Maths", "Science"), null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Hi ${profile?.name?.substringBefore(' ').orEmpty().ifBlank { "there" }}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("${profile?.streakDays ?: 0}-day streak · ${profile?.focusMinutesWeek ?: 0} min focused this week",
            style = MaterialTheme.typography.bodyLarge, color = AreadColors.Muted)
        Spacer(Modifier.height(24.dp))

        Card {
            if (focusing) {
                Text("Focus session", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("$minutesLeft min left", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text("Distracting apps and unknown calls are paused.", color = AreadColors.Muted)
                Spacer(Modifier.height(16.dp))
                SecondaryButton("End early") {
                    val done = chosen - minutesLeft
                    Prefs.stopFocus(ctx); focusing = false
                    if (done > 0) onSessionDone(done)
                }
            } else {
                Text("Start studying", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60).forEach { m ->
                        ChoiceChip("$m min", chosen == m, { chosen = m }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Begin focus session") { Prefs.startFocus(ctx, chosen); focusing = true }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Text("Today's plan", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (slots.isEmpty()) Text("No more good slots today. Tomorrow's plan appears in the morning.", color = AreadColors.Muted)
            slots.forEach {
                Text("%02d:%02d · %d min".format(it.start.hour, it.start.minute, it.minutes), style = MaterialTheme.typography.bodyLarge)
                Text(it.reason, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(onClick = onOpenTutor) {
            Text("Stuck on a question?", style = MaterialTheme.typography.titleMedium)
            Text("$TUTOR_NAME will guide you without giving the answer away.", color = AreadColors.Muted)
        }
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Surface(m, color = AreadColors.Surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, AreadColors.Line)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

private data class Msg(val fromStudent: Boolean, val text: String)

@Composable
fun TutorScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { LipSyncSpeaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }

    val msgs = remember { mutableStateListOf(Msg(false, "I'm $TUTOR_NAME. Send me a question and I'll help you work it out. Try it first, and I'll check your thinking.")) }
    var input by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var needPhoto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    fun send(imageB64: String? = null, text: String) {
        msgs.add(Msg(true, if (imageB64 != null) "$text\n[photo of my attempt]" else text))
        attempts++
        scope.launch {
            busy = true
            val history = msgs.takeLast(8).map { TutorTurn(if (it.fromStudent) "student" else "tutor", it.text) }
            val reply = runCatching {
                Repository.askTutor(TutorRequest(message = text, attempts = attempts, imageBase64 = imageB64, history = history))
            }.getOrElse { TutorReply("I couldn't reach the server. Check your internet and try again.", "error") }
            msgs.add(Msg(false, reply.reply))
            needPhoto = reply.mode == "ask_photo"
            if (voice) speaker.speak(reply.reply)
            busy = false
            listState.animateScrollToItem(msgs.lastIndex)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            send(Base64.encodeToString(bytes, Base64.NO_WRAP), "Here is my attempt.")
            needPhoto = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            runCatching { AvatarView(speaker.mouth, speaker.speaking, Modifier.fillMaxSize()) }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Voice", style = MaterialTheme.typography.bodyMedium)
                Switch(voice, { voice = it; if (!it) speaker.stop() })
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromStudent) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (m.fromStudent) AreadColors.AccentSoft else AreadColors.Surface,
                        shape = MaterialTheme.shapes.medium,
                        border = if (m.fromStudent) null else BorderStroke(1.dp, AreadColors.Line),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) { Text(m.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (busy) item { Text("$TUTOR_NAME is thinking…", color = AreadColors.Muted, modifier = Modifier.padding(8.dp)) }
        }
        if (needPhoto) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton("Send a photo of your attempt") {
                    picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(input, { input = it }, "Ask or share your attempt", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { val t = input.trim(); if (t.isNotEmpty()) { input = ""; send(text = t) } }, enabled = !busy && input.isNotBlank()) { Text("Send") }
        }
    }
}

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Uploading ${uris.size} page(s)…"
            val result = runCatching { uris.forEach { Repository.uploadTextbookPage(ctx, it, subject.ifBlank { "General" }) } }
            status = if (result.isSuccess) "Uploaded. Aread will plan your sessions around these pages." else "Upload failed. Try again."
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Your textbooks", "Upload the pages you're studying. Aread plans sessions around them.")
        Field(subject, { subject = it }, "Subject (e.g. Science, Chapter 4)")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Choose pages") { picker.launch("image/*") }
        status?.let { Spacer(Modifier.height(12.dp)); Text(it, color = AreadColors.Muted) }
    }
}

@Composable
fun LeaderboardScreen() {
    var rows by remember { mutableStateOf<List<LeaderRow>?>(null) }
    LaunchedEffect(Unit) { rows = runCatching { Repository.leaderboard() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("This week", "Ranked by focused minutes.")
        val list = rows
        if (list == null) CircularProgressIndicator()
        else if (list.isEmpty()) Text("No one has studied yet this week. Be first.", color = AreadColors.Muted)
        else LazyColumn {
            items(list.withIndex().toList()) { (i, r) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        Text(r.schoolName, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                    }
                    Text("${r.focusMinutesWeek} min", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color = AreadColors.Line)
            }
        }
    }
}

@Composable
fun SettingsScreen(profile: Profile?, onSignOut: () -> Unit, onAdminUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var blockCalls by remember { mutableStateOf(Prefs.blockCalls(ctx)) }
    var taps by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader("Settings")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Silence calls while focusing", style = MaterialTheme.typography.titleMedium)
                Text("Starred contacts still ring.", color = AreadColors.Muted)
            }
            Switch(blockCalls, { blockCalls = it; Prefs.setBlockCalls(ctx, it) })
        }
        Spacer(Modifier.height(24.dp))
        Text("Apps paused during focus", style = MaterialTheme.typography.titleMedium)
        Text("${Prefs.blockedPackages(ctx).size} apps (social media, short video, games).", color = AreadColors.Muted)
        Spacer(Modifier.height(32.dp))
        SecondaryButton("Sign out") { onSignOut() }
        Spacer(Modifier.height(24.dp))

        Text("Aread 1.0.0", color = AreadColors.Muted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { taps++; if (taps >= 7 && profile?.isAdmin == true) onAdminUnlocked() })
    }
}

@Composable
fun AdminScreen() {
    var stats by remember { mutableStateOf<Map<String, Int>?>(null) }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { stats = Repository.adminStats(); users = Repository.adminAllProfiles() }
            .onFailure { err = "Not authorised." }
    }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) {
        item { ScreenHeader("Admin") }
        err?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        stats?.let { s -> item {
            s.forEach { (k, v) -> Text("${k.replace('_', ' ')}: $v", style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(20.dp))
        } }
        items(users) { u ->
            Text("${u.name} · class ${u.classLevel} · ${u.schoolName}", style = MaterialTheme.typography.bodyMedium)
            Text("streak ${u.streakDays}d · ${u.focusMinutesWeek} min/week", color = AreadColors.Muted, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AreadColors.Line)
        }
    }
}
