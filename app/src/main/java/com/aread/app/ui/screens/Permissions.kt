package com.aread.app.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aread.app.ui.theme.AreadColors

private data class PermStep(
    val title: String, val why: String, val how: String,
    val granted: (Context) -> Boolean, val open: (Context) -> Intent,
)

private fun steps(): List<PermStep> = listOf(
    PermStep(
        "Show over other apps",
        "Lets Aread put the study screen on top of a game or social app when you open it during focus time.",
        "Find Aread in the list and switch on \"Allow display over other apps\".",
        { Settings.canDrawOverlays(it) },
        { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}")) },
    ),
    PermStep(
        "Focus blocker (Accessibility)",
        "Lets Aread know which app just opened so it can pause distracting ones. It does not read your screen or what you type.",
        "Open Installed apps, tap Aread focus blocker, and turn it on.",
        { c -> Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(c.packageName) == true },
        { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
    ),
    PermStep(
        "Unrestricted battery",
        "Stops your phone from putting Aread to sleep, so reminders and blocking keep working.",
        "Choose \"Allow\" on the next prompt.",
        { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) },
        { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}")) },
    ),
    PermStep(
        "Call blocking during focus",
        "Silences calls from unknown numbers while you study. Parents and starred contacts can still reach you.",
        "Choose Aread as your \"Caller ID & spam\" app.",
        { c -> if (Build.VERSION.SDK_INT >= 29) (c.getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) else false },
        { c -> if (Build.VERSION.SDK_INT >= 29) (c.getSystemService(Context.ROLE_SERVICE) as RoleManager).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) else Intent(Settings.ACTION_SETTINGS) },
    ),
    PermStep(
        "Auto-start (some phones)",
        "Xiaomi, Oppo, Vivo and Realme phones stop apps after a reboot. This keeps your reminders alive.",
        "Find Aread and switch on Auto-start. If you don't see this option, your phone doesn't need it.",
        { false },
        { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}")) },
    ),
)

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { steps() }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenHeader("Let Aread protect your study time", "Each permission has one job. Tap a row, follow the short instruction, then come back.")
        list.forEach { p ->
            val ok = remember(tick) { p.granted(ctx) }
            Surface(color = AreadColors.Surface, shape = MaterialTheme.shapes.medium, tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, AreadColors.Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (ok) Text("On", color = AreadColors.Accent, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(p.why, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    Text("How: ${p.how}", style = MaterialTheme.typography.bodyMedium)
                    if (!ok) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { runCatching { ctx.startActivity(p.open(ctx)) } }) { Text("Open settings") }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Continue", onClick = onDone)
    }
}

@Composable
fun PrivacyScreen(onAccept: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    val rows = listOf(
        "Show over other apps" to "Display the study screen on top of blocked apps.",
        "Accessibility service" to "Detect which app opened. We don't read screen text, passwords or messages.",
        "Battery unrestricted" to "Keep reminders and blocking running in the background.",
        "Call screening" to "Silence unknown callers during focus. Call history is never uploaded.",
        "Contacts (optional)" to "So starred contacts like parents can still ring through.",
        "Notifications" to "Study reminders and streak alerts.",
        "Photos you choose" to "Textbook pages and solution photos you pick are sent to your tutor. Nothing else is read.",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenHeader("Privacy, in plain words", "What Aread asks for and why.")
        rows.forEach { (t, d) ->
            Text(t, style = MaterialTheme.typography.titleMedium)
            Text(d, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "Your name, class, school and study time are stored securely and only your name, school and weekly focus time appear on the leaderboard. You can delete your account and data at any time from Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("I have read this and agree to the Terms and Privacy Policy.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Agree and continue", enabled = agreed, onClick = onAccept)
    }
}
