package com.aread.app.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aread.app.data.ScreenTime
import com.aread.app.ui.theme.AreadColors

private class Step(
    val title: String,
    val why: String,
    val how: String,
    val icon: ImageVector,
    val granted: ((Context) -> Boolean)?,
    val button: String,
    val run: () -> Unit,
    val extraText: String? = null,
    val extraRun: (() -> Unit)? = null,
)

private fun launch(ctx: Context, intent: Intent) {
    val ok = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) appInfo(ctx)
}

private fun appInfo(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun isXiaomi(): Boolean {
    val m = Build.MANUFACTURER.lowercase()
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var notifTries by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    val steps = remember {
        buildList {
            add(
                Step(
                    "Notifications", "Study reminders and streak alerts reach you.",
                    "Tap Allow on the pop-up.", Icons.Outlined.Notifications,
                    { NotificationManagerCompat.from(it).areNotificationsEnabled() }, "Allow",
                    {
                        notifTries++
                        if (Build.VERSION.SDK_INT >= 33 && notifTries <= 2) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            launch(
                                ctx,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                    },
                ),
            )
            add(
                Step(
                    "Usage access", "Shows how much time you spend on your phone today, and which apps took it.",
                    "Open the list, tap Aread, and switch on Permit usage access.", Icons.Outlined.QueryStats,
                    { ScreenTime.hasAccess(it) }, "Open settings",
                    { launch(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                ),
            )
            add(
                Step(
                    "Show over other apps", "Lets the study screen appear on top of a game or social app during focus time.",
                    "Find Aread and switch on Allow display over other apps.", Icons.Outlined.Layers,
                    { Settings.canDrawOverlays(it) }, "Open settings",
                    {
                        launch(
                            ctx,
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                        )
                    },
                ),
            )
            add(
                Step(
                    "Focus blocker", "Lets Aread notice which app just opened so it can pause distracting ones. It never reads your screen or what you type.",
                    "Open Installed apps, tap Aread focus blocker and turn it on. If the switch is greyed out, tap Allow restricted settings below: open the top-right menu on the app info page and choose Allow restricted settings, then try again.",
                    Icons.Outlined.Accessibility,
                    { c ->
                        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                            ?.contains(c.packageName) == true
                    },
                    "Open accessibility",
                    { launch(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    "Greyed out? Allow restricted settings",
                    { appInfo(ctx) },
                ),
            )
            add(
                Step(
                    "Unrestricted battery", "Stops your phone from putting Aread to sleep, so blocking and reminders keep working.",
                    "Tap Allow on the pop-up.", Icons.Outlined.BatteryChargingFull,
                    { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) },
                    "Allow",
                    {
                        launch(
                            ctx,
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        )
                    },
                ),
            )
            add(
                Step(
                    "Keep permissions", "Android removes permissions from apps you don't open for a while. That would switch blocking off.",
                    "Switch off Pause app activity if unused.", Icons.Outlined.Shield,
                    { if (Build.VERSION.SDK_INT >= 30) it.packageManager.isAutoRevokeWhitelisted() else true },
                    "Open settings",
                    {
                        if (Build.VERSION.SDK_INT >= 30) {
                            launch(ctx, Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:${ctx.packageName}")))
                        } else appInfo(ctx)
                    },
                ),
            )
            add(
                Step(
                    "Call blocking during focus", "Silences calls from unknown numbers while you study. Parents and starred contacts still ring.",
                    "Choose Aread as your Caller ID & spam app.", Icons.Outlined.Call,
                    { c ->
                        Build.VERSION.SDK_INT >= 29 &&
                            (c.getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                    },
                    "Choose Aread",
                    {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val rm = ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager
                            val started = runCatching {
                                if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                } else error("unavailable")
                            }.isSuccess
                            if (!started) launch(ctx, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } else appInfo(ctx)
                    },
                ),
            )
            add(
                Step(
                    "Starred contacts", "So the people you starred, like parents, can still reach you during focus.",
                    "Tap Allow on the pop-up. This is optional.", Icons.Outlined.Contacts,
                    { ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED },
                    "Allow",
                    { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                ),
            )
            if (isXiaomi()) {
                add(
                    Step(
                        "Pop-ups from background", "Xiaomi, Redmi and Poco phones block the study screen unless this is on.",
                        "Open Other permissions, then switch on Open new windows while running in the background and Show on Lock screen.",
                        Icons.Outlined.OpenInNew, null, "Open permissions",
                        {
                            launch(
                                ctx,
                                Intent("miui.intent.action.APP_PERM_EDITOR")
                                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                    .putExtra("extra_pkgname", ctx.packageName),
                            )
                        },
                        "Auto-start",
                        {
                            launch(
                                ctx,
                                Intent().setClassName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                                ),
                            )
                        },
                    ),
                )
            }
        }
    }

    val done = remember(tick) { steps.count { it.granted != null && it.granted.invoke(ctx) } }
    val total = steps.count { it.granted != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Let Aread protect your study time", "Each permission has one job. Tap a card, follow the short note, then come back.")
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = AreadColors.Accent,
            trackColor = AreadColors.AccentSoft,
        )
        Spacer(Modifier.height(6.dp))
        Text("$done of $total ready", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        Spacer(Modifier.height(16.dp))
        steps.forEach { s ->
            val ok = remember(tick) { s.granted?.invoke(ctx) }
            AreadCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(s.icon)
                    Spacer(Modifier.width(12.dp))
                    Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (ok == true) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(AreadColors.AccentSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("On", color = AreadColors.Accent, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(s.why, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                if (ok != true) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.how, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = s.run,
                            colors = ButtonDefaults.buttonColors(containerColor = AreadColors.Accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(s.button) }
                        if (s.extraText != null && s.extraRun != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = s.extraRun) { Text(s.extraText) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("You can finish any of these later in Settings.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Continue", onClick = onDone)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PrivacyScreen(saving: Boolean, error: String?, onAccept: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    val rows = listOf(
        "Notifications" to "Study reminders and streak alerts.",
        "Usage access" to "Shows your screen time on your own phone. This data is not uploaded.",
        "Show over other apps" to "Displays the study screen on top of blocked apps.",
        "Accessibility service" to "Detects which app opened. Aread does not read screen text, passwords or messages.",
        "Battery unrestricted" to "Keeps reminders and blocking running in the background.",
        "Call screening" to "Silences unknown callers during focus. Your call history is never uploaded.",
        "Contacts (optional)" to "So starred contacts like parents can still ring through.",
        "Photos you choose" to "Textbook pages and solution photos you pick are sent to your tutor. Nothing else is read.",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Privacy, in plain words", "What Aread asks for and why.")
        rows.forEach { (t, d) ->
            Text(t, style = MaterialTheme.typography.titleMedium)
            Text(d, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "Your name, class, school and study time are stored securely. Only your name, school and weekly focus time appear on the leaderboard. You can ask for your data to be deleted at any time.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("I have read this and agree to the Terms and Privacy Policy.", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Agree and continue", enabled = agreed, loading = saving, onClick = onAccept)
        Spacer(Modifier.height(24.dp))
    }
}
