package com.aread.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aread.app.data.LeaderRow
import com.aread.app.data.Repository
import com.aread.app.ui.theme.AreadColors

@Composable
fun LeaderboardScreen(myName: String) {
    var rows by remember { mutableStateOf<List<LeaderRow>?>(null) }
    LaunchedEffect(Unit) { rows = runCatching { Repository.leaderboard() }.getOrDefault(emptyList()) }
    val list = rows

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Leaderboard", style = MaterialTheme.typography.headlineSmall)
        Text("This week, by focused minutes.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
        Spacer(Modifier.height(20.dp))
        when {
            list == null -> CircularProgressIndicator(color = AreadColors.Accent)
            list.isEmpty() -> Text("No one has studied yet this week. Be the first.", color = AreadColors.Muted)
            else -> {
                if (list.size >= 3) {
                    Podium(list.take(3))
                    Spacer(Modifier.height(16.dp))
                }
                val rest = if (list.size >= 3) list.drop(3) else list
                val offset = if (list.size >= 3) 3 else 0
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(rest) { i, r ->
                        val me = r.name.equals(myName, true)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (me) AreadColors.AccentSoft else AreadColors.Surface)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1 + offset}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                            Column(Modifier.weight(1f)) {
                                Text(if (me) "${r.name} (you)" else r.name, style = MaterialTheme.typography.bodyLarge)
                                Text(r.schoolName, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                            }
                            Text("${r.focusMinutesWeek} min", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Podium(top: List<LeaderRow>) {
    val order = listOf(1, 0, 2)
    val heights = listOf(110.dp, 140.dp, 90.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        order.forEachIndexed { slot, idx ->
            val r = top[idx]
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.name.substringBefore(' '), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${r.focusMinutesWeek} min", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heights[slot])
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(if (idx == 0) AreadColors.Accent else AreadColors.AccentSoft),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "${idx + 1}",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (idx == 0) MaterialTheme.colorScheme.onPrimary else AreadColors.Accent,
                    )
                }
            }
        }
    }
}
