package com.aread.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aread.app.data.Errors
import com.aread.app.data.Pack
import com.aread.app.data.Repository
import com.aread.app.data.TutorStore
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val DEFAULT_PACKS = listOf(
    Pack("Starter", 25, "NPR 40"),
    Pack("Student", 80, "NPR 99", "Most popular"),
    Pack("Exam season", 250, "NPR 249", "Best value"),
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf(DEFAULT_PACKS) }
    var buyNote by remember { mutableStateOf("") }
    var buyUrl by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeMsg by remember { mutableStateOf<String?>(null) }
    var dailyMsg by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<Pack?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        TutorStore.refreshCredits()
        runCatching { Repository.config() }.onSuccess { cfg ->
            cfg["packs"]?.let { raw ->
                runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Pack.serializer()), raw) }
                    .onSuccess { if (it.isNotEmpty()) packs = it }
            }
            buyNote = cfg["buy_note"].orEmpty()
            buyUrl = cfg["buy_url"].orEmpty()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Pro credits", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        AreadCard {
            Text("You have", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Text(
                if (TutorStore.credits >= 0) "${TutorStore.credits}" else "-",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = AreadColors.Accent,
            )
            Text(
                if (TutorStore.credits == 1) "Pro reply left" else "Pro replies left",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Medha Pro thinks deeper, explains more clearly and checks your working in more detail. Each Pro reply uses 1 credit. Normal Medha is always free.",
                style = MaterialTheme.typography.bodyMedium,
                color = AreadColors.Muted,
            )
        }

        SectionTitle("Free credits")
        AreadCard {
            Text("Daily gift", style = MaterialTheme.typography.titleMedium)
            Text("Open Aread every day and claim 1 free Pro reply.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Claim today's reply", enabled = !working) {
                scope.launch {
                    working = true
                    dailyMsg = runCatching { Repository.claimDaily() }.fold(
                        { TutorStore.credits = it; "Added. You now have $it." },
                        {
                            val m = it.message.orEmpty()
                            when {
                                m.contains("already_claimed") -> "You already claimed today's reply. Come back tomorrow."
                                m.contains("balance_full") -> "Use some of your credits first. You can hold up to 30."
                                else -> Errors.friendly(it)
                            }
                        },
                    )
                    working = false
                }
            }
            dailyMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        AreadCard {
            Text("Invite a friend", style = MaterialTheme.typography.titleMedium)
            Text(
                "When a friend enters your code, you both get 10 credits.",
                style = MaterialTheme.typography.bodyMedium,
                color = AreadColors.Muted,
            )
            if (TutorStore.refCode.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(TutorStore.refCode.uppercase(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                        Intent.EXTRA_TEXT,
                        "Study without distractions with Aread. Enter my code ${TutorStore.refCode.uppercase()} in Pro credits and we both get 10 free Pro replies.",
                    )
                    runCatching { ctx.startActivity(Intent.createChooser(i, "Invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Share my code") }
            }
        }
        Spacer(Modifier.height(12.dp))
        AreadCard {
            Text("Have a code?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enter a code from a friend or one you bought.",
                style = MaterialTheme.typography.bodyMedium,
                color = AreadColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(20); codeMsg = null }, "Code")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Redeem", enabled = code.length >= 4, loading = working) {
                scope.launch {
                    working = true
                    codeMsg = runCatching { Repository.redeem(code) }.fold(
                        { TutorStore.credits = it; code = ""; "Done. You now have $it credits." },
                        { Errors.friendly(it) },
                    )
                    working = false
                }
            }
            codeMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
        }

        SectionTitle("Get more")
        packs.forEach { p ->
            AreadCard(Modifier.padding(bottom = 10.dp), onClick = { buying = p }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            if (p.note.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Pill(p.note, true, {})
                            }
                        }
                        Text("${p.credits} Pro replies", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                    }
                    Text(p.price, style = MaterialTheme.typography.titleLarge, color = AreadColors.Accent)
                }
            }
        }
        Text(
            "Credits never expire. No subscription, no ads.",
            style = MaterialTheme.typography.bodyMedium,
            color = AreadColors.Muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    buying?.let { p ->
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text("${p.name} · ${p.price}") },
            text = {
                Text(
                    buyNote.ifBlank { "Message Ar Studio to buy this pack. You will get a code to enter on this screen." },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                if (buyUrl.isNotBlank()) {
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buyUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        buying = null
                    }) { Text("Message to buy") }
                } else {
                    TextButton(onClick = { buying = null }) { Text("OK") }
                }
            },
            dismissButton = if (buyUrl.isNotBlank()) {
                { TextButton(onClick = { buying = null }) { Text("Close") } }
            } else null,
        )
    }
}
