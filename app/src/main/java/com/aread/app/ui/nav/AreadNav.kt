package com.aread.app.ui.nav

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
import kotlinx.coroutines.launch

private val TABS = listOf(
    Triple("home", "Home", Icons.Outlined.Home),
    Triple("tutor", "Tutor", Icons.Outlined.School),
    Triple("library", "Books", Icons.Outlined.MenuBook),
    Triple("board", "Ranks", Icons.Outlined.EmojiEvents),
    Triple("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun AreadNav() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var pending by remember { mutableStateOf<Profile?>(null) }
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Repository.restoreSession()
        start = when {
            !Prefs.introSeen(ctx) -> "intro"
            !Repository.isSignedIn -> "login"
            else -> {
                profile = runCatching { Repository.myProfile() }.getOrNull()
                if (profile == null || profile!!.name.isBlank()) "profile" else "home"
            }
        }
    }
    val startDest = start ?: return

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in TABS.map { it.first }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r, onClick = { nav.goTab(r) },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = startDest, modifier = Modifier.padding(pad)) {
            composable("intro") { IntroScreen { Prefs.setIntroSeen(ctx); nav.navigate("login") { popUpTo("intro") { inclusive = true } } } }
            composable("login") {
                LoginScreen {
                    scope.launch {
                        profile = runCatching { Repository.myProfile() }.getOrNull()
                        val next = if (profile == null || profile!!.name.isBlank()) "profile" else "home"
                        nav.navigate(next) { popUpTo("login") { inclusive = true } }
                    }
                }
            }
            composable("profile") { ProfileSetupScreen { pending = it; nav.navigate("goals") } }
            composable("goals") { GoalsScreen { goals -> pending = pending?.copy(goals = goals); nav.navigate("permissions") } }
            composable("permissions") { PermissionsScreen { nav.navigate("privacy") } }
            composable("privacy") {
                PrivacyScreen {
                    scope.launch {
                        pending?.let { runCatching { Repository.saveProfile(it) }; profile = it }
                        nav.navigate("home") { popUpTo(0) }
                    }
                }
            }
            composable("home") {
                HomeScreen(profile, onOpenTutor = { nav.goTab("tutor") }, onSessionDone = { m ->
                    scope.launch {
                        runCatching { Repository.logSession(m) }
                        profile = runCatching { Repository.myProfile() }.getOrNull() ?: profile
                    }
                })
            }
            composable("tutor") { TutorScreen() }
            composable("library") { LibraryScreen() }
            composable("board") { LeaderboardScreen() }
            composable("settings") {
                SettingsScreen(profile,
                    onSignOut = { scope.launch { Repository.signOut(); nav.navigate("login") { popUpTo(0) } } },
                    onAdminUnlocked = { nav.navigate("admin") })
            }
            composable("admin") { AdminScreen() }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
