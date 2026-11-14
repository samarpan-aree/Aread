package com.aread.app.ui.nav

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.aread.app.data.TutorStore
import io.github.jan.supabase.auth.status.SessionStatus
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.aread.app.data.Prefs
import com.aread.app.data.Profile
import com.aread.app.data.Repository
import com.aread.app.ui.screens.*
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val TABS = listOf(
    Triple("home", "Home", Icons.Outlined.Home),
    Triple("tutor", "Tutor", Icons.Outlined.School),
    Triple("library", "Books", Icons.Outlined.MenuBook),
    Triple("board", "Ranks", Icons.Outlined.EmojiEvents),
    Triple("settings", "Settings", Icons.Outlined.Settings),
)

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun AreadNav() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var pending by remember { mutableStateOf<Profile?>(null) }
    var start by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun cache(p: Profile) {
        runCatching { Prefs.setCachedProfile(ctx, json.encodeToString(p)) }
    }

    suspend fun loadProfile(): Profile? {
        val remote = runCatching { Repository.myProfile() }.getOrNull()
        val cached = Prefs.cachedProfile(ctx)?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
        if (Prefs.pendingSync(ctx) && cached != null && cached.name.isNotBlank()) {
            val ok = runCatching { Repository.saveProfile(cached) }.isSuccess
            if (ok) Prefs.setPendingSync(ctx, false)
            return cached.copy(
                streakDays = remote?.streakDays ?: cached.streakDays,
                bestStreak = remote?.bestStreak ?: cached.bestStreak,
                focusMinutesWeek = remote?.focusMinutesWeek ?: cached.focusMinutesWeek,
                isAdmin = remote?.isAdmin ?: cached.isAdmin,
            ).also { cache(it) }
        }
        val best = remote?.takeIf { it.name.isNotBlank() } ?: cached ?: remote
        if (remote != null && remote.name.isNotBlank()) cache(remote)
        return best
    }

    LaunchedEffect(Unit) {
        Repository.restoreSession()
        val status = Repository.sessionStatus.value
        val authed = Repository.isSignedIn ||
            (Prefs.signedInFlag(ctx) && status !is SessionStatus.NotAuthenticated)
        start = when {
            !Prefs.introSeen(ctx) -> "intro"
            !authed -> "login"
            else -> {
                Prefs.setSignedInFlag(ctx, true)
                profile = loadProfile()
                if (profile?.name.isNullOrBlank()) "profile" else "welcome"
            }
        }
    }
    val startDest = start
    if (startDest == null) {
        Box(Modifier.fillMaxSize().background(AreadColors.Paper), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(AreadColors.Accent),
                contentAlignment = Alignment.Center,
            ) { BookGlyph(MaterialTheme.colorScheme.onPrimary, Modifier.size(60.dp)) }
        }
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in TABS.map { it.first }
    val keyboard = imeVisible()

    Scaffold(
        containerColor = AreadColors.Paper,
        bottomBar = {
            if (showBar && !keyboard) {
                NavigationBar(containerColor = AreadColors.Surface) {
                    TABS.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { nav.goTab(r) },
                            icon = { androidx.compose.material3.Icon(icon, null) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AreadColors.Accent,
                                selectedTextColor = AreadColors.Accent,
                                indicatorColor = AreadColors.AccentSoft,
                                unselectedIconColor = AreadColors.Muted,
                                unselectedTextColor = AreadColors.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav,
            startDestination = startDest,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding(),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 14 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { -it / 14 } },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 14 } },
        ) {
            composable("welcome") {
                WelcomeScreen(
                    profile?.name.orEmpty(),
                    profile?.streakDays ?: 0,
                    Prefs.todayMinutes(ctx),
                ) {
                    val next = if (Prefs.permsSeen(ctx)) "home" else "perms_first"
                    nav.navigate(next) { popUpTo("welcome") { inclusive = true } }
                }
            }
            composable("intro") {
                IntroScreen {
                    Prefs.setIntroSeen(ctx)
                    nav.navigate("login") { popUpTo("intro") { inclusive = true } }
                }
            }
            composable("login") {
                LoginScreen {
                    Prefs.setSignedInFlag(ctx, true)
                    scope.launch {
                        profile = loadProfile()
                        val next = when {
                            profile?.name.isNullOrBlank() -> "profile"
                            !Prefs.permsSeen(ctx) -> "perms_first"
                            else -> "home"
                        }
                        nav.navigate(next) { popUpTo("login") { inclusive = true } }
                    }
                }
            }
            composable("profile") {
                ProfileSetupScreen(profile) { p ->
                    pending = p
                    nav.navigate("goals")
                }
            }
            composable("goals") {
                GoalsScreen(pending?.goals ?: profile?.goals.orEmpty()) { goals ->
                    pending = pending?.copy(goals = goals)
                    nav.navigate("style")
                }
            }
            composable("style") { StyleScreen { nav.navigate("permissions") } }
            composable("permissions") {
                PermissionsScreen {
                    Prefs.setPermsSeen(ctx)
                    nav.navigate("privacy")
                }
            }
            composable("perms_first") {
                PermissionsScreen {
                    Prefs.setPermsSeen(ctx)
                    nav.navigate("home") { popUpTo(0) }
                }
            }
            composable("privacy") {
                PrivacyScreen(saving, saveError) {
                    val p = pending ?: profile
                    if (p == null) {
                        nav.navigate("profile")
                    } else {
                        scope.launch {
                            saving = true
                            saveError = null
                            cache(p)
                            val ok = runCatching { Repository.saveProfile(p) }.isSuccess
                            if (!ok) {
                                Prefs.setPendingSync(ctx, true)
                                Toast.makeText(ctx, "Saved on this phone. It will sync when you're online.", Toast.LENGTH_LONG).show()
                            }
                            profile = (if (ok) runCatching { Repository.myProfile() }.getOrNull() else null) ?: p
                            saving = false
                            nav.navigate("home") { popUpTo(0) }
                        }
                    }
                }
            }
            composable("home") {
                HomeScreen(
                    profile,
                    onOpenTutor = { nav.goTab("tutor") },
                    onOpenProfile = { nav.navigate("profile_edit") },
                    onOpenSetup = { nav.navigate("setup") },
                    onSessionDone = { m ->
                        scope.launch {
                            runCatching { Repository.logSession(m) }
                            val fresh = runCatching { Repository.myProfile() }.getOrNull()
                            if (fresh != null && fresh.name.isNotBlank()) {
                                profile = fresh
                                cache(fresh)
                            }
                        }
                    },
                )
            }
            composable("tutor") { TutorScreen() }
            composable("library") { LibraryScreen() }
            composable("board") { LeaderboardScreen(profile?.name.orEmpty()) }
            composable("settings") {
                SettingsScreen(
                    profile,
                    onOpenProfile = { nav.navigate("profile_edit") },
                    onOpenApps = { nav.navigate("apps") },
                    onOpenSetup = { nav.navigate("setup") },
                    onSignOut = {
                        scope.launch {
                            runCatching { Repository.signOut() }
                            Prefs.setCachedProfile(ctx, null)
                            Prefs.setSignedInFlag(ctx, false)
                            TutorStore.forgetOnSignOut(ctx)
                            profile = null
                            pending = null
                            nav.navigate("login") { popUpTo(0) }
                        }
                    },
                    onAdminUnlocked = { nav.navigate("admin") },
                )
            }
            composable("profile_edit") {
                ProfileScreen(profile) { updated ->
                    profile = updated
                    cache(updated)
                }
            }
            composable("apps") { AppPickerScreen() }
            composable("setup") { PermissionsScreen { nav.popBackStack() } }
            composable("admin") { AdminScreen() }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
