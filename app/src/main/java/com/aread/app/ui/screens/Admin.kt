package com.aread.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aread.app.data.AdminCode
import com.aread.app.data.AdminUser
import com.aread.app.data.Repository
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.launch

private val tabs = listOf("Overview", "Users", "Codes", "Pricing")

@Composable
fun AdminScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text("Admin panel", style = MaterialTheme.typography.headlineSmall)
            Text("Everything about Aread in one place", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        }
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> OverviewTab()
            1 -> UsersTab()
            2 -> CodesTab()
            else -> PricingTab()
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AreadCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
    }
}

@Composable
private fun OverviewTab() {
    var s by remember { mutableStateOf<Map<String, Long>?>(null) }
    var err by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        err = false
        runCatching { Repository.adminOverview() }.onSuccess { s = it }.onFailure { err = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        if (err) {
            Text("Couldn't load the numbers.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { reload++ }) { Text("Try again") }
        }
        val m = s
        if (m == null && !err) CircularProgressIndicator()
        if (m != null) {
            fun v(k: String) = (m[k] ?: 0L).toString()
            @Composable fun row(a: String, ka: String, b: String, kb: String) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(a, v(ka), Modifier.weight(1f))
                    StatTile(b, v(kb), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("People", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            row("Students", "users", "Studied today", "active_today")
            row("Joined today", "new_today", "Joined this week", "new_week")
            Text("Study", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            row("Focus minutes this week", "focus_minutes_week", "Pages uploaded", "pages")
            Text("AI today", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            row("Tutor messages", "tutor_today", "Page analyses", "analyze_today")
            Text("Credits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            row("Held by students", "credits_held", "Redeemed from codes", "credits_sold")
            row("Codes made", "codes_total", "Codes unused", "codes_unused")
            TextButton(onClick = { reload++ }) { Text("Refresh") }
        }
    }
}

@Composable
private fun UsersTab() {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AdminUser?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, reload) {
        loading = true
        err = false
        kotlinx.coroutines.delay(300)
        runCatching { Repository.adminUsers(query) }.onSuccess { users = it }.onFailure { err = true }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search name, email or school") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (err) Text("Couldn't load users.", color = MaterialTheme.colorScheme.error)
        Text("${users.size} shown", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        LazyColumn(Modifier.fillMaxSize()) {
            items(users) { u ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    TextButton(onClick = { selected = u }, contentPadding = PaddingValues(0.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                u.name.ifBlank { "(no name yet)" } + if (u.isAdmin) "  · admin" else "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(u.email, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                            Text(
                                "Class ${u.classLevel.ifBlank { "-" }} · streak ${u.streakDays}d · ${u.focusMinutesWeek} min/week · ${u.balance} credits",
                                style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted,
                            )
                        }
                    }
                }
                HorizontalDivider(color = AreadColors.Line)
            }
        }
    }
    selected?.let { u ->
        var amount by remember(u.id) { mutableStateOf("10") }
        var msg by remember(u.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(u.name.ifBlank { u.email }) },
            text = {
                Column {
                    Text(u.email, color = AreadColors.Muted)
                    Text("${u.balance} credits now", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    DigitField(amount, { amount = it }, "Credits", 4)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { Repository.adminGrant(u.id, amount.toInt()) }
                                    .onSuccess { msg = "Added. Now $it credits."; reload++ }
                                    .onFailure { msg = "Couldn't add credits." }
                            }
                        }, enabled = (amount.toIntOrNull() ?: 0) > 0) { Text("Give") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { Repository.adminGrant(u.id, -amount.toInt()) }
                                    .onSuccess { msg = "Removed. Now $it credits."; reload++ }
                                    .onFailure { msg = "Couldn't remove credits." }
                            }
                        }, enabled = (amount.toIntOrNull() ?: 0) > 0) { Text("Take") }
                    }
                    msg?.let { Text(it, color = AreadColors.Muted) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { Repository.adminSetAdmin(u.id, !u.isAdmin) }
                                .onSuccess { selected = null; reload++ }
                                .onFailure { msg = "Couldn't change admin access." }
                        }
                    }) { Text(if (u.isAdmin) "Remove admin access" else "Make admin") }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun CodesTab() {
    var count by remember { mutableStateOf("5") }
    var credits by remember { mutableStateOf("25") }
    var codes by remember { mutableStateOf<List<AdminCode>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var fresh by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(reload) { runCatching { Repository.adminCodeList() }.onSuccess { codes = it } }

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            AreadCard {
                Text("Make new codes", style = MaterialTheme.typography.titleMedium)
                Text("Each code works once. Sell it, then send it to the student.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DigitField(count, { count = it }, "How many", 3, Modifier.weight(1f))
                    DigitField(credits, { credits = it }, "Credits each", 4, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Create", enabled = (count.toIntOrNull() ?: 0) > 0 && (credits.toIntOrNull() ?: 0) > 0) {
                    scope.launch {
                        err = null
                        runCatching { Repository.adminCodes(count.toInt(), credits.toInt()) }
                            .onSuccess { fresh = it; reload++ }
                            .onFailure { err = "Couldn't create codes." }
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (fresh.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    fresh.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
                    TextButton(onClick = { share(fresh.joinToString("\n")) }) { Text("Share these codes") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("All codes (latest 100)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }
        items(codes) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.code, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (c.used) "${c.credits} credits · used by ${c.usedByName?.ifBlank { null } ?: "a student"}" else "${c.credits} credits · not used",
                        style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted,
                    )
                }
                if (!c.used) {
                    TextButton(onClick = { share(c.code) }) { Text("Share") }
                    TextButton(onClick = { scope.launch { runCatching { Repository.adminDeleteCode(c.code) }; reload++ } }) { Text("Delete") }
                }
            }
            HorizontalDivider(color = AreadColors.Line)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PricingTab() {
    var note by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var packs by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { Repository.config() }.onSuccess {
            note = it["buy_note"].orEmpty(); url = it["buy_url"].orEmpty(); packs = it["packs"].orEmpty()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("What students see on the credits screen.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it }, label = { Text("How to buy (message)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Buy link (WhatsApp, eSewa page...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            packs, { packs = it }, label = { Text("Packs (JSON list)") }, modifier = Modifier.fillMaxWidth(), minLines = 4,
            supportingText = { Text("""Example: [{"name":"Starter","credits":25,"price":"NPR 40"}]""") },
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Save") {
            scope.launch {
                val valid = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(packs).let { it is kotlinx.serialization.json.JsonArray } }.getOrDefault(false)
                if (!valid) { status = "Packs must be a valid JSON list."; return@launch }
                runCatching {
                    Repository.adminSetConfig("buy_note", note)
                    Repository.adminSetConfig("buy_url", url.trim())
                    Repository.adminSetConfig("packs", packs)
                }.onSuccess { status = "Saved. Students see it next time they open credits." }
                    .onFailure { status = "Couldn't save." }
            }
        }
        status?.let { Text(it, color = AreadColors.Muted) }
    }
}
