package com.aread.app.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aread.app.data.Errors
import com.aread.app.data.ImageUtil
import com.aread.app.data.Repository
import com.aread.app.data.TextbookPage
import com.aread.app.ui.theme.AreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class UploadResult(
    val thumb: ImageBitmap?,
    val ok: Boolean,
    val title: String,
    val detail: String,
)

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf<List<TextbookPage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<UploadResult>() }

    LaunchedEffect(refresh) {
        pages = runCatching { Repository.myPages() }.getOrDefault(pages)
        loaded = true
    }

    suspend fun handle(uri: Uri) {
        val img = withContext(Dispatchers.IO) { ImageUtil.prepare(ctx, uri) }
        if (img == null) {
            results.add(0, UploadResult(null, false, "Couldn't open this photo", "Try a different photo."))
            return
        }
        val thumb = img.bitmap.asImageBitmap()
        val b64 = Base64.encodeToString(img.bytes, Base64.NO_WRAP)
        val a = runCatching { Repository.analyzePage(b64, subject.trim()) }.getOrElse {
            results.add(0, UploadResult(thumb, false, "Couldn't check this page", Errors.friendly(it)))
            return
        }
        if (!a.isStudyMaterial) {
            results.add(0, UploadResult(thumb, false, "Not added: this isn't study material", a.reason.ifBlank { "Upload a textbook page, notes or a worksheet." }))
            return
        }
        if (!a.readable) {
            results.add(0, UploadResult(thumb, false, "Not added: too hard to read", "Retake it in good light, with the whole page in view."))
            return
        }
        runCatching { Repository.saveTextbookPage(img.bytes, a, subject.trim()) }
            .onSuccess {
                val name = subject.trim().ifBlank { a.subject }
                results.add(
                    0,
                    UploadResult(
                        thumb, true,
                        listOf(name, a.chapter).filter { it.isNotBlank() }.joinToString(" · "),
                        "About ${a.estMinutes} min to study · ${a.difficulty}" +
                            if (a.topics.isNotEmpty()) "\n" + a.topics.take(4).joinToString(", ") else "",
                    ),
                )
            }
            .onFailure { results.add(0, UploadResult(thumb, false, "Couldn't save this page", Errors.friendly(it))) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val list = uris.take(10)
            list.forEachIndexed { i, u ->
                progress = "Reading page ${i + 1} of ${list.size}"
                handle(u)
            }
            if (uris.size > 10) results.add(0, UploadResult(null, false, "Only 10 pages at a time", "Upload the rest in another batch."))
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
                Text("Teach Aread what you're studying.", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            }
        }
        Spacer(Modifier.height(18.dp))
        AreadCard {
            Text("How this works", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Take a clear photo of a textbook page or your notes.",
                "Aread checks it's study material and reads the subject, chapter and topics.",
                "Your daily plan is built from this, with study time matched to how hard each page is.",
            ).forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}", color = AreadColors.Accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(22.dp))
                    Text(t, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Selfies and unrelated photos are rejected automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = AreadColors.Muted,
            )
        }
        Spacer(Modifier.height(14.dp))
        AreadCard(Modifier.animateContentSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, AreadColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .background(AreadColors.AccentSoft)
                    .clickable(enabled = !busy) { picker.launch("image/*") }
                    .padding(vertical = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudUpload, null, tint = AreadColors.Accent, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (busy) "Please wait" else "Choose pages", style = MaterialTheme.typography.titleMedium, color = AreadColors.Accent)
                    Text("Up to 10 photos at a time", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(14.dp))
            Field(subject, { subject = it }, "Subject (optional, e.g. Science)", maxLen = 40)
            AnimatedVisibility(busy) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AreadColors.Accent, trackColor = AreadColors.AccentSoft)
                    Spacer(Modifier.height(8.dp))
                    Text(progress, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                }
            }
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SectionTitle("Results")
            results.take(12).forEach { r ->
                AreadCard(Modifier.padding(bottom = 10.dp).animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.thumb != null) {
                            Image(
                                r.thumb, null,
                                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                                    tint = if (r.ok) AreadColors.Accent else AreadColors.Warn,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
                        }
                    }
                }
            }
        }
        SectionTitle("Saved")
        if (loaded && grouped.isEmpty()) {
            Text("Nothing here yet. Add your first page above.", color = AreadColors.Muted)
        }
        grouped.forEach { (name, list) ->
            AreadCard(Modifier.padding(bottom = 10.dp).animateContentSize()) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${list.size} page${if (list.size > 1) "s" else ""} · about ${list.sumOf { it.estMinutes }} min of study",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AreadColors.Muted,
                )
                val chapters = list.map { it.chapter }.filter { it.isNotBlank() }.distinct().take(4)
                if (chapters.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(chapters.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
