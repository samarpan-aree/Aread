package com.aread.app.ui.nav

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        start = when {
            !Prefs.introSeen(ctx) -> "intro"
            !Repository.isSignedIn -> "login"
            else -> {
                profile = loadProfile()
                if (profile?.name.isNullOrBlank()) "profile" else "home"
            }
        }
    }
    val startDest = start ?: return

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
        ) {
            composable("intro") {
                IntroScreen {
                    Prefs.setIntroSeen(ctx)
                    nav.navigate("login") { popUpTo("intro") { inclusive = true } }
                }
            }
            composable("login") {
                LoginScreen {
                    scope.launch {
                        profile = loadProfile()
                        val next = if (profile?.name.isNullOrBlank()) "profile" else "home"
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
            composable("permissions") { PermissionsScreen { nav.navigate("privacy") } }
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
