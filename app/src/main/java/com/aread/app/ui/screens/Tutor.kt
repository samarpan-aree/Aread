package com.aread.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.aread.app.data.Errors
import com.aread.app.data.Repository
import com.aread.app.data.ImageUtil
import com.aread.app.data.PreparedImage
import com.aread.app.data.TutorStore
import com.aread.app.tutor.AvatarView
import com.aread.app.tutor.LipSyncSpeaker
import com.aread.app.ui.theme.AreadColors
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TutorScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { LipSyncSpeaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    val has3d = remember { runCatching { ctx.assets.list("")?.contains("medha.glb") == true }.getOrDefault(false) }

    LaunchedEffect(Unit) {
        TutorStore.load(ctx, TUTOR_NAME)
        TutorStore.refreshCredits()
    }
    val msgs = TutorStore.msgs
    val busy = TutorStore.busy
    val needPhoto = TutorStore.needPhoto
    var input by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(true) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showRedeem by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = imeVisible()

    LaunchedEffect(msgs.size, busy, keyboard) {
        val last = msgs.size - 1 + if (busy) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    LaunchedEffect(TutorStore.toSpeak) {
        val t = TutorStore.toSpeak
        if (t != null) {
            TutorStore.toSpeak = null
            if (voice) speaker.speak(t)
        }
    }

    fun send(text: String, image: PreparedImage? = null) = TutorStore.send(ctx, TUTOR_NAME, text, image)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                photoError = null
                val img = withContext(Dispatchers.IO) { ImageUtil.prepare(ctx, uri) }
                if (img == null) {
                    photoError = "I couldn't open that photo. Try another one."
                } else {
                    val t = input.trim()
                    input = ""
                    send(if (t.isEmpty()) "Here is my attempt." else t, img)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(TUTOR_NAME, style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        busy -> "Thinking"
                        speaker.speaking -> "Speaking"
                        else -> "Your study tutor"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AreadColors.Muted,
                )
            }
            TextButton(onClick = { showHistory = true }) { Text("History") }
            TextButton(onClick = { speaker.stop(); TutorStore.newChat(ctx, TUTOR_NAME) }) { Text("New") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("$TUTOR_NAME", !TutorStore.pro, { TutorStore.pro = false })
            Pill(
                if (TutorStore.credits >= 0) "$TUTOR_NAME Pro · ${TutorStore.credits}" else "$TUTOR_NAME Pro",
                TutorStore.pro,
                { if (TutorStore.credits == 0) showRedeem = true else TutorStore.pro = true },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showRedeem = true }) { Text("Get credits") }
            Text("Voice", style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted)
            Spacer(Modifier.width(6.dp))
            Switch(voice, { voice = it; if (!it) speaker.stop() })
        }
        AnimatedVisibility(!keyboard) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                if (has3d) AvatarView(speaker.mouth, speaker.speaking, Modifier.fillMaxSize())
                else MedhaFace(speaker.mouth, Modifier.fillMaxHeight().padding(8.dp))
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(msgs.toList()) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromStudent) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(if (m.fromStudent) Modifier else Modifier.border(1.dp, AreadColors.Line, RoundedCornerShape(18.dp)))
                            .background(if (m.fromStudent) AreadColors.Accent else AreadColors.Surface)
                            .padding(14.dp),
                    ) {
                        Column {
                            m.photo?.let { path ->
                                AsyncImage(
                                    File(path), "Your photo",
                                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (m.fromStudent) MaterialTheme.colorScheme.onPrimary else AreadColors.Ink,
                            )
                        }
                    }
                }
            }
            if (busy) {
                item { Text("$TUTOR_NAME is thinking…", color = AreadColors.Muted, modifier = Modifier.padding(8.dp)) }
            }
        }
        photoError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (needPhoto) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton("Send a photo of your attempt") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        if (msgs.size <= 1 && !keyboard) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Explain a concept", "Help with a maths problem", "Check my working").forEach { s ->
                    Pill(s, false, { input = if (s.startsWith("Check")) "Please check my working: " else "$s: " })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask or share your attempt") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val t = input.trim()
                    if (t.isNotEmpty() && !busy) {
                        input = ""
                        send(t)
                    }
                }),
                trailingIcon = {
                    IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.Image, "Attach photo", tint = AreadColors.Muted)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AreadColors.Line,
                    focusedBorderColor = AreadColors.Accent,
                    unfocusedContainerColor = AreadColors.Surface,
                    focusedContainerColor = AreadColors.Surface,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        send(t)
                    }
                },
                enabled = !busy && input.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AreadColors.Accent, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text("Send") }
        }
    }
    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }, containerColor = AreadColors.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Past chats", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (TutorStore.archive.isEmpty()) {
                    Text("Your earlier chats will show up here.", color = AreadColors.Muted)
                }
                TutorStore.archive.toList().forEachIndexed { i, chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                speaker.stop()
                                TutorStore.openChat(ctx, TUTOR_NAME, i)
                                showHistory = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title.ifBlank { "Chat" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(chat.ts)) + " · ${chat.msgs.size} messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AreadColors.Muted,
                            )
                        }
                        TextButton(onClick = { TutorStore.deleteChat(ctx, i) }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (showRedeem) {
        var code by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        var working by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showRedeem = false },
            title = { Text("Pro credits") },
            text = {
                Column {
                    Text(
                        "$TUTOR_NAME Pro thinks harder and explains more deeply. Each Pro reply uses 1 credit. You start with 5 free.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Balance: ${if (TutorStore.credits >= 0) TutorStore.credits else "-"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        code, { code = it.uppercase().take(20); msg = null },
                        label = { Text("Enter a code") }, singleLine = true,
                    )
                    msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AreadColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
                    if (TutorStore.refCode.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Invite a friend", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "You both get 10 credits when they enter your code: ${TutorStore.refCode.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AreadColors.Muted,
                        )
                        TextButton(onClick = {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                                Intent.EXTRA_TEXT,
                                "Study without distractions with Aread. Use my code ${TutorStore.refCode.uppercase()} in the tutor's Get credits box and we both get 10 free Pro credits.",
                            )
                            runCatching { ctx.startActivity(Intent.createChooser(i, "Invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text("Share my code") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = code.length >= 4 && !working, onClick = {
                    scope.launch {
                        working = true
                        msg = runCatching { Repository.redeem(code) }
                            .fold({ TutorStore.credits = it; code = ""; "Done. You now have $it credits." }, { Errors.friendly(it) })
                        working = false
                    }
                }) { Text("Redeem") }
            },
            dismissButton = { TextButton(onClick = { showRedeem = false }) { Text("Close") } },
        )
    }
}
