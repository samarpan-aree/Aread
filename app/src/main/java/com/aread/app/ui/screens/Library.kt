package com.aread.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aread.app.data.Repository
import com.aread.app.data.TextbookPage
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pages by remember { mutableStateOf<List<TextbookPage>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) { pages = runCatching { Repository.myPages() }.getOrDefault(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Uploading ${uris.size} page${if (uris.size > 1) "s" else ""}..."
            val result = runCatching {
                uris.forEach { Repository.uploadTextbookPage(ctx, it, subject.trim().ifBlank { "General" }) }
            }
            status = if (result.isSuccess) "Done. Aread will plan sessions around these pages." else "Upload failed. Check your internet and try again."
            busy = false
            refresh++
        }
    }

    val grouped = remember(pages) { pages.groupBy { it.subject }.entries.toList() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.MenuBook, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Your textbooks", style = MaterialTheme.typography.headlineSmall)
                Text("Upload the pages you're studying.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            }
        }
        Spacer(Modifier.height(20.dp))
        AreadCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, AreadColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .background(AreadColors.AccentSoft)
                    .clickable(enabled = !busy) { picker.launch("image/*") }
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudUpload, null, tint = AreadColors.Accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to choose pages", style = MaterialTheme.typography.titleMedium, color = AreadColors.Accent)
                    Text("Photos of textbook pages, one or many", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(14.dp))
            Field(subject, { subject = it }, "Subject or chapter (e.g. Science, Chapter 4)", maxLen = 40)
            if (busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AreadColors.Accent, trackColor = AreadColors.AccentSoft)
            }
            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            }
        }
        SectionTitle("Uploaded")
        if (grouped.isEmpty()) {
            Text("Nothing here yet.", color = AreadColors.Muted)
        }
        grouped.forEach { (name, list) ->
            AreadCard(Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${list.size} page${if (list.size > 1) "s" else ""} · added ${list.first().createdAt.take(10)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AreadColors.Muted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
