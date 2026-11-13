package com.aread.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.aread.app.data.*
import com.aread.app.ui.theme.AccentChoices
import com.aread.app.ui.theme.AccentNames
import com.aread.app.ui.theme.AreadColors
import com.aread.app.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun copyToFiles(ctx: Context, uri: Uri, name: String): String? = runCatching {
    val f = File(ctx.filesDir, "$name-${System.currentTimeMillis()}.jpg")
    ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { out -> input.copyTo(out) } }
    f.absolutePath
}.getOrNull()

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        }
        trailing()
    }
}

@Composable
fun SettingsScreen(
    profile: Profile?,
    onOpenProfile: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSetup: () -> Unit,
    onSignOut: () -> Unit,
    onAdminUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    var blockCalls by remember { mutableStateOf(Prefs.blockCalls(ctx)) }
    var strict by remember { mutableStateOf(Prefs.strictDefault(ctx)) }
    var goalDialog by remember { mutableStateOf(false) }
    var goal by remember { mutableIntStateOf(Prefs.dailyGoal(ctx)) }
    var taps by remember { mutableIntStateOf(0) }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val path = copyToFiles(ctx, uri, "bg")
            if (path != null) {
                ThemeState.bgPath?.let { File(it).delete() }
                ThemeState.bgPath = path
                Prefs.setBgPath(ctx, path)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        AreadCard(onClick = onOpenProfile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.name.orEmpty(), 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.name.orEmpty().ifBlank { "Your profile" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(profile?.classLevel?.takeIf { it.isNotBlank() }?.let { "Class $it" }, profile?.schoolName?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AreadColors.Muted,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Muted)
            }
        }

        SectionTitle("Appearance")
        AreadCard {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                    Pill(label, ThemeState.mode == i, { ThemeState.mode = i; Prefs.setThemeMode(ctx, i) })
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentChoices.forEachIndexed { i, color ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (ThemeState.accent == i) Modifier.border(3.dp, AreadColors.Ink, CircleShape) else Modifier)
                            .clickable { ThemeState.accent = i; Prefs.setAccent(ctx, i) },
                    )
                }
            }
            Text(AccentNames[ThemeState.accent], style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))
            Text("Home photo", style = MaterialTheme.typography.titleMedium)
            Text("Shows behind your greeting on the home screen.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Choose photo", false, { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                if (ThemeState.bgPath != null) {
                    Pill("Remove", false, {
                        ThemeState.bgPath?.let { File(it).delete() }
                        ThemeState.bgPath = null
                        Prefs.setBgPath(ctx, null)
                    })
                }
            }
        }

        SectionTitle("Focus")
        AreadCard {
            SettingRow("Strict mode by default", "Sessions can't be ended early.") {
                Switch(strict, { strict = it; Prefs.setStrictDefault(ctx, it) })
            }
            HorizontalDivider(color = AreadColors.Line)
            SettingRow("Silence calls while focusing", "Starred contacts still ring.") {
                Switch(blockCalls, { blockCalls = it; Prefs.setBlockCalls(ctx, it) })
            }
            HorizontalDivider(color = AreadColors.Line)
            SettingRow("Apps paused during focus", "${Prefs.blockedPackages(ctx).size} apps", onOpenApps) {
                Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Muted)
            }
            HorizontalDivider(color = AreadColors.Line)
            SettingRow("Daily goal", "$goal minutes", { goalDialog = true }) {
                Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Muted)
            }
        }

        SectionTitle("Setup")
        AreadCard {
            SettingRow("Permissions and setup", "Check blocking, notifications and battery", onOpenSetup) {
                Icon(Icons.Outlined.ChevronRight, null, tint = AreadColors.Muted)
            }
        }

        Spacer(Modifier.height(24.dp))
        SecondaryButton("Sign out", onClick = onSignOut)
        Spacer(Modifier.height(20.dp))
        Text(
            "Aread 1.0.0",
            color = AreadColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                taps++
                if (taps >= 7 && profile?.isAdmin == true) {
                    taps = 0
                    onAdminUnlocked()
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }

    if (goalDialog) {
        var text by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { goalDialog = false },
            title = { Text("Daily goal") },
            text = { DigitField(text, { text = it }, "Minutes per day", 3, suffix = "min") },
            confirmButton = {
                TextButton(onClick = {
                    val v = text.toIntOrNull()
                    if (v != null && v in 5..600) {
                        goal = v
                        Prefs.setDailyGoal(ctx, v)
                        goalDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { goalDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ProfileScreen(profile: Profile?, onSaved: (Profile) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val parsed = remember(profile) { Countries.parse(profile?.phone.orEmpty()) }
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var country by remember(profile) { mutableStateOf(parsed.first) }
    var digits by remember(profile) { mutableStateOf(parsed.second) }
    var cls by remember(profile) { mutableStateOf(profile?.classLevel.orEmpty()) }
    var roll by remember(profile) { mutableStateOf(profile?.rollNo.orEmpty()) }
    var grade by remember(profile) { mutableStateOf(profile?.avgGrade.orEmpty()) }
    var school by remember(profile) { mutableStateOf(profile?.schoolName.orEmpty()) }
    var type by remember(profile) { mutableStateOf(profile?.schoolType ?: "government") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val path = copyToFiles(ctx, uri, "avatar")
            if (path != null) {
                ThemeState.avatarPath?.let { File(it).delete() }
                ThemeState.avatarPath = path
                Prefs.setAvatarPath(ctx, path)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(Modifier.clip(CircleShape).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(name, 104.dp)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(AreadColors.Surface).border(1.dp, AreadColors.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhotoCamera, null, tint = AreadColors.Accent, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Streak", "${profile?.streakDays ?: 0}d", Modifier.weight(1f))
            StatBox("Best", "${profile?.bestStreak ?: 0}d", Modifier.weight(1f))
            StatBox("This week", "${profile?.focusMinutesWeek ?: 0}m", Modifier.weight(1f))
        }
        SectionTitle("Details")
        Field(name, { name = it }, "Full name", keep = { it.isLetter() || it in " .'-" }, maxLen = 60)
        Spacer(Modifier.height(10.dp))
        PhoneField(country, { country = it }, digits, { digits = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DigitField(cls, { cls = it }, "Class", 2, Modifier.weight(1f))
            DigitField(roll, { roll = it }, "Roll no.", 5, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Field(grade, { grade = it }, "Average grade", KeyboardType.Decimal, keep = { it.isDigit() || it == '.' || it == '%' }, maxLen = 6)
        Spacer(Modifier.height(10.dp))
        Field(school, { school = it }, "School name", maxLen = 80)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Government", type == "government", { type = "government" }, Modifier.weight(1f))
            ChoiceChip("Private", type == "private", { type = "private" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save changes", enabled = name.isNotBlank() && cls.isNotBlank() && school.isNotBlank(), loading = busy) {
            val updated = (profile ?: Profile(id = Repository.userId.orEmpty())).copy(
                name = name.trim(),
                phone = if (digits.isBlank()) "" else Countries.format(country, digits),
                classLevel = cls,
                rollNo = roll,
                avgGrade = grade,
                schoolName = school.trim(),
                schoolType = type,
            )
            scope.launch {
                busy = true
                val ok = runCatching { Repository.saveProfile(updated) }.isSuccess
                status = if (ok) "Saved." else "Saved on this phone. It will sync when you're online."
                if (!ok) Prefs.setPendingSync(ctx, true)
                onSaved(updated)
                busy = false
            }
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(AreadColors.AccentSoft).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = AreadColors.Accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = AreadColors.Muted)
    }
}

private data class AppItem(val pkg: String, val label: String)

@Composable
fun AppPickerScreen() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val blocked = remember { mutableStateListOf<String>().apply { addAll(Prefs.blockedPackages(ctx)) } }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedWith(compareByDescending<AppItem> { it.pkg in blocked }.thenBy { it.label.lowercase() })
        }
    }

    val shown = remember(apps, query) { apps.filter { it.label.contains(query.trim(), true) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Apps paused during focus", style = MaterialTheme.typography.headlineSmall)
        Text("${blocked.size} selected", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AreadColors.Accent)
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.pkg }) { app ->
                val icon = remember(app.pkg) {
                    runCatching { ctx.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    else Box(Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Switch(app.pkg in blocked, { on ->
                        if (on) blocked.add(app.pkg) else blocked.remove(app.pkg)
                        Prefs.setBlockedPackages(ctx, blocked.toSet())
                    })
                }
            }
        }
    }
}

@Composable
fun AdminScreen() {
    var stats by remember { mutableStateOf<Map<String, Int>?>(null) }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            stats = Repository.adminStats()
            users = Repository.adminAllProfiles()
        }.onFailure { err = "Not authorised." }
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Admin", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
        }
        err?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        stats?.let { s ->
            item {
                AreadCard {
                    s.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(k.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text("$v", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        items(users) { u ->
            Text("${u.name} · class ${u.classLevel} · ${u.schoolName}", style = MaterialTheme.typography.bodyMedium)
            Text("streak ${u.streakDays}d · ${u.focusMinutesWeek} min/week", color = AreadColors.Muted, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AreadColors.Line)
        }
    }
}
