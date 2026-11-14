package com.aread.app.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aread.app.blocking.AreadAdminReceiver
import com.aread.app.data.ScreenTime
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.delay

private enum class Mock { NOTIF, RESTRICTED, ACCESS, OVERLAY, USAGE, BATTERY, REVOKE, CALL, CONTACTS, ADMIN, MIUI }

private class Step(
    val title: String,
    val why: String,
    val how: List<String>,
    val mock: Mock,
    val granted: ((Context) -> Boolean)?,
    val action: String,
    val run: () -> Unit,
    val tip: String? = null,
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

private fun restrictedAllowed(c: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return runCatching {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.unsafeCheckOpNoThrow("android:access_restricted_settings", android.os.Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

private fun adminComponent(c: Context) = ComponentName(c, AreadAdminReceiver::class.java)

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var notifTries by remember { mutableIntStateOf(0) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    val steps = remember {
        buildList {
            add(
                Step(
                    "Allow notifications",
                    "So Aread can remind you to study and keep your streak alive.",
                    listOf("Tap Allow notifications below.", "On the pop-up, tap Allow."),
                    Mock.NOTIF,
                    { NotificationManagerCompat.from(it).areNotificationsEnabled() },
                    "Allow notifications",
                    {
                        notifTries++
                        if (Build.VERSION.SDK_INT >= 33 && notifTries <= 2) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            launch(
                                ctx,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                    },
                    "If the pop-up doesn't appear, tap the button again. It will open the notification settings instead.",
                ),
            )
            add(
                Step(
                    "Unlock restricted settings",
                    "Android blocks some switches for apps installed from a file. Turn this on first, or the focus blocker switch stays greyed out.",
                    listOf(
                        "Tap Open app info below.",
                        "Scroll down to Advanced settings.",
                        "Switch on Allow restricted settings.",
                        "No such switch? Tap the three dots at the top right and choose Allow restricted settings.",
                    ),
                    Mock.RESTRICTED,
                    { restrictedAllowed(it) },
                    "Open app info",
                    { appInfo(ctx) },
                    "Still can't find it? Go to the next step, try to switch on the focus blocker once, then come back here.",
                ),
            )
            add(
                Step(
                    "Turn on the focus blocker",
                    "This lets Aread see which app just opened, so it can pause distracting ones. It never reads your screen, messages or passwords.",
                    listOf(
                        "Tap Open accessibility below.",
                        "Open Installed apps or Downloaded apps.",
                        "Tap Aread focus blocker.",
                        "Switch it on and tap Allow.",
                    ),
                    Mock.ACCESS,
                    { c ->
                        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                            ?.contains(c.packageName) == true
                    },
                    "Open accessibility",
                    { launch(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    "Greyed out? Go back one step and allow restricted settings.",
                ),
            )
            add(
                Step(
                    "Show over other apps",
                    "Lets the study screen appear on top of a game or social app during focus time.",
                    listOf("Tap Open settings below.", "Tap Aread in the list.", "Switch on Allow display over other apps."),
                    Mock.OVERLAY,
                    { Settings.canDrawOverlays(it) },
                    "Open settings",
                    { launch(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) },
                ),
            )
            add(
                Step(
                    "Usage access",
                    "Shows how much time you spend on your phone today and which apps took it. This stays on your phone.",
                    listOf("Tap Open settings below.", "Tap Aread in the list.", "Switch on Permit usage access."),
                    Mock.USAGE,
                    { ScreenTime.hasAccess(it) },
                    "Open settings",
                    { launch(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                ),
            )
            add(
                Step(
                    "Unrestricted battery",
                    "Stops your phone from putting Aread to sleep, so blocking and reminders keep working.",
                    listOf("Tap Allow below.", "On the pop-up, tap Allow."),
                    Mock.BATTERY,
                    { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) },
                    "Allow",
                    {
                        launch(
                            ctx,
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        )
                    },
                    "Xiaomi, Oppo, Vivo: also open Aread's battery settings and choose No restrictions.",
                    "Open app info",
                    { appInfo(ctx) },
                ),
            )
            add(
                Step(
                    "Keep permissions on",
                    "Android removes permissions from apps you don't open for a while. That would silently switch blocking off.",
                    listOf("Tap Open settings below.", "Find Pause app activity if unused.", "Switch it off."),
                    Mock.REVOKE,
                    { if (Build.VERSION.SDK_INT >= 30) it.packageManager.isAutoRevokeWhitelisted else true },
                    "Open settings",
                    {
                        if (Build.VERSION.SDK_INT >= 30) {
                            launch(ctx, Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:${ctx.packageName}")))
                        } else {
                            appInfo(ctx)
                        }
                    },
                ),
            )
            add(
                Step(
                    "Call blocking",
                    "Silences calls from unknown numbers while you study. Parents and starred contacts still ring.",
                    listOf("Tap Choose Aread below.", "Pick Aread as your Caller ID and spam app.", "Tap Set as default."),
                    Mock.CALL,
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
                                } else {
                                    error("unavailable")
                                }
                            }.isSuccess
                            if (!started) launch(ctx, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } else {
                            appInfo(ctx)
                        }
                    },
                ),
            )
            add(
                Step(
                    "Starred contacts (optional)",
                    "So the people you starred, like parents, can still reach you during focus.",
                    listOf("Tap Allow below.", "On the pop-up, tap Allow."),
                    Mock.CONTACTS,
                    { ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED },
                    "Allow",
                    { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                ),
            )
            add(
                Step(
                    "Uninstall protection",
                    "While this is on, Aread can't be removed in the middle of a study session. It stops you deleting the app when you feel like quitting.",
                    listOf("Tap Turn on protection below.", "Read the screen, then tap Activate this device admin app."),
                    Mock.ADMIN,
                    { c -> (c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent(c)) },
                    "Turn on protection",
                    {
                        launch(
                            ctx,
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
                                .putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Keeps Aread from being uninstalled during a study session.",
                                ),
                        )
                    },
                    "To remove Aread later, turn this off in Settings under Device admin apps, outside a focus session.",
                ),
            )
            if (isXiaomi()) {
                add(
                    Step(
                        "Allow pop-ups from background",
                        "Xiaomi, Redmi and Poco phones hide the study screen unless this is on.",
                        listOf(
                            "Tap Open permissions below.",
                            "Switch on Open new windows while running in the background.",
                            "Switch on Show on Lock screen.",
                        ),
                        Mock.MIUI,
                        null,
                        "Open permissions",
                        {
                            launch(
                                ctx,
                                Intent("miui.intent.action.APP_PERM_EDITOR")
                                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                    .putExtra("extra_pkgname", ctx.packageName),
                            )
                        },
                        "Also turn on Auto-start for Aread so it survives a restart.",
                        "Auto-start",
                        {
                            launch(
                                ctx,
                                Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                            )
                        },
                    ),
                )
            }
        }
    }

    val last = steps.lastIndex
    val step = steps[index.coerceIn(0, last)]
    val granted = remember(tick, index) { step.granted?.invoke(ctx) }
    val startedGranted = remember(index) { step.granted?.invoke(ctx) == true }

    LaunchedEffect(granted, index) {
        if (granted == true && !startedGranted) {
            delay(1000)
            if (index < last) index++ else onDone()
        }
    }

    BackHandler(enabled = index > 0) { index-- }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    IconButton(onClick = { index-- }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text("Step ${index + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge, color = AreadColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            val progress by animateFloatAsState((index + 1) / steps.size.toFloat(), tween(450), label = "progress")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = AreadColors.Accent,
                trackColor = AreadColors.AccentSoft,
            )
        }

        AnimatedContent(
            targetState = index,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "steps",
        ) { i ->
            val s = steps[i.coerceIn(0, last)]
            val ok = remember(tick, i) { s.granted?.invoke(ctx) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                MockScene(s.mock)
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (ok == true) StatusChip("Turned on")
                }
                Spacer(Modifier.height(8.dp))
                Text(s.why, style = MaterialTheme.typography.bodyLarge, color = AreadColors.Muted)
                Spacer(Modifier.height(18.dp))
                Text("How to turn it on", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                s.how.forEachIndexed { n, line ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(AreadColors.AccentSoft),
                            contentAlignment = Alignment.Center,
                        ) { Text("${n + 1}", style = MaterialTheme.typography.labelMedium, color = AreadColors.Accent) }
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                if (s.tip != null) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AreadColors.AccentSoft).padding(14.dp),
                    ) { Text(s.tip, style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (granted != true) {
                PrimaryButton(step.action) { step.run() }
                if (step.extraText != null && step.extraRun != null) {
                    TextButton(onClick = step.extraRun, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(step.extraText)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index > 0) {
                    Box(Modifier.weight(1f)) { SecondaryButton("Back") { index-- } }
                }
                Box(Modifier.weight(1f)) {
                    val next: () -> Unit = { if (index < last) index++ else onDone() }
                    if (granted == true) PrimaryButton("Continue", onClick = next) else SecondaryButton("Continue", onClick = next)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(AreadColors.AccentSoft).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = AreadColors.Accent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Pulse(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp), content: @Composable () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val a by inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.border(2.5.dp, AreadColors.Accent.copy(alpha = a), shape).padding(2.dp)) { content() }
}

@Composable
private fun MockToggle(on: Boolean) {
    Box(
        Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(50))
            .background(if (on) AreadColors.Accent else AreadColors.Line),
    ) {
        Box(
            Modifier.padding(2.dp).size(18.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
        )
    }
}

@Composable
private fun MockRow(label: String, sub: String? = null, toggle: Boolean? = null, chevron: Boolean = false, highlight: Boolean = false) {
    val row = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = AreadColors.Muted)
            }
            if (toggle != null) MockToggle(toggle)
            if (chevron) Text(">", color = AreadColors.Muted)
        }
    }
    if (highlight) Pulse(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { row() } else row()
}

@Composable
private fun MockFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, AreadColors.Line, RoundedCornerShape(20.dp))
            .background(AreadColors.Surface).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun MockDialog(title: String, left: String, right: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AreadColors.AccentSoft).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AreadColors.Surface).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(left, color = AreadColors.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Pulse(Modifier.weight(1f), RoundedCornerShape(50)) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(AreadColors.Accent).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(right, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MockScene(mock: Mock) {
    when (mock) {
        Mock.NOTIF -> MockDialog("Allow Aread to send you notifications?", "Don't allow", "Allow")
        Mock.BATTERY -> MockDialog("Let Aread always run in the background?", "Deny", "Allow")
        Mock.CONTACTS -> MockDialog("Allow Aread to access your contacts?", "Don't allow", "Allow")
        Mock.CALL -> MockDialog("Set Aread as your default Caller ID and spam app?", "Cancel", "Set as default")
        Mock.RESTRICTED -> MockFrame("App info  ·  Aread") {
            MockRow("Notifications", chevron = true)
            MockRow("Other permissions", chevron = true)
            Text("ADVANCED SETTINGS", style = MaterialTheme.typography.labelMedium, color = AreadColors.Muted, modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 2.dp))
            MockRow("Allow restricted settings", toggle = false, highlight = true)
        }
        Mock.ACCESS -> MockFrame("Accessibility") {
            MockRow("Installed apps", chevron = true)
            MockRow("Aread focus blocker", sub = "Off", chevron = true, highlight = true)
            MockRow("Other service", sub = "Off", chevron = true)
        }
        Mock.OVERLAY -> MockFrame("Display over other apps") {
            MockRow("Some other app", toggle = false)
            MockRow("Aread", sub = "Allow display over other apps", toggle = false, highlight = true)
        }
        Mock.USAGE -> MockFrame("Usage access") {
            MockRow("Some other app", sub = "Not allowed")
            MockRow("Aread", sub = "Permit usage access", toggle = false, highlight = true)
        }
        Mock.REVOKE -> MockFrame("App info  ·  Aread") {
            MockRow("Pause app activity if unused", sub = "Switch this off", toggle = true, highlight = true)
            MockRow("App permissions", chevron = true)
            MockRow("Notifications", chevron = true)
        }
        Mock.ADMIN -> MockFrame("Activate device admin app?") {
            MockRow("Aread", sub = "Uninstall protection")
            Spacer(Modifier.height(8.dp))
            Pulse(Modifier.fillMaxWidth(), RoundedCornerShape(50)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(AreadColors.Accent).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Activate this device admin app", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Mock.MIUI -> MockFrame("Other permissions") {
            MockRow("Open new windows while running in the background", toggle = false, highlight = true)
            MockRow("Show on Lock screen", toggle = false, highlight = true)
            MockRow("Home screen shortcuts", toggle = false)
        }
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
        "Device admin" to "Stops Aread being uninstalled during a study session. It gives Aread no access to your data.",
        "Photos you choose" to "Textbook pages and solution photos you pick are checked by our AI service to read the subject and give feedback. Nothing else on your phone is read.",
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
