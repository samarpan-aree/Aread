package com.aread.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ChatMsg(val fromStudent: Boolean, val text: String, val photo: String? = null)

@Serializable
data class StoredMsg(val s: Boolean, val t: String, val p: String? = null)

@Serializable
data class StoredChat(
    val msgs: List<StoredMsg>,
    val attempts: Int = 0,
    val needPhoto: Boolean = false,
    val title: String = "",
    val ts: Long = 0L,
)

object TutorStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private var loaded = false
    private var studying: List<String>? = null

    val msgs = mutableStateListOf<ChatMsg>()
    val archive = mutableStateListOf<StoredChat>()
    var attempts by mutableIntStateOf(0)
    var needPhoto by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var toSpeak by mutableStateOf<String?>(null)
    var pro by mutableStateOf(false)
    var credits by mutableIntStateOf(-1)
    var refCode by mutableStateOf("")

    private fun greeting(name: String) =
        ChatMsg(false, "Hi, I'm $name. Send me a question and try it first. I'll guide you, and if you get stuck I'll check your working.")

    fun load(c: Context, name: String) {
        if (loaded) return
        loaded = true
        val stored = Prefs.tutorChat(c)?.let { runCatching { json.decodeFromString<StoredChat>(it) }.getOrNull() }
        if (stored != null && stored.msgs.isNotEmpty()) {
            msgs.addAll(stored.msgs.map { ChatMsg(it.s, it.t, it.p?.takeIf { p -> File(p).exists() }) })
            attempts = stored.attempts
            needPhoto = stored.needPhoto
        } else {
            msgs.add(greeting(name))
        }
        Prefs.tutorArchive(c)?.let {
            runCatching { json.decodeFromString(ListSerializer(StoredChat.serializer()), it) }.getOrNull()
        }?.let { archive.clear(); archive.addAll(it) }
    }

    suspend fun refreshCredits() {
        runCatching { Repository.credits() }.onSuccess {
            credits = it.balance
            refCode = it.refCode
        }
    }

    private fun snapshot(): StoredChat {
        val trimmed = msgs.takeLast(80)
        val firstQ = trimmed.firstOrNull { it.fromStudent }?.text.orEmpty()
        return StoredChat(
            trimmed.map { StoredMsg(it.fromStudent, it.text, it.photo) },
            attempts, needPhoto, firstQ.take(60), System.currentTimeMillis(),
        )
    }

    private fun save(c: Context) {
        runCatching { Prefs.setTutorChat(c, json.encodeToString(snapshot())) }
    }

    private fun saveArchive(c: Context) {
        runCatching { Prefs.setTutorArchive(c, json.encodeToString(ListSerializer(StoredChat.serializer()), archive.toList())) }
    }

    private fun archiveCurrent() {
        if (msgs.any { it.fromStudent }) {
            archive.add(0, snapshot())
            while (archive.size > 30) {
                val old = archive.removeAt(archive.lastIndex)
                old.msgs.forEach { m -> m.p?.let { runCatching { File(it).delete() } } }
            }
        }
    }

    private fun resetCurrent(name: String) {
        msgs.clear()
        msgs.add(greeting(name))
        attempts = 0
        needPhoto = false
        busy = false
        toSpeak = null
    }

    fun newChat(c: Context, name: String) {
        archiveCurrent()
        resetCurrent(name)
        save(c)
        saveArchive(c)
    }

    fun openChat(c: Context, name: String, index: Int) {
        val target = archive.getOrNull(index) ?: return
        archive.removeAt(index)
        archiveCurrent()
        msgs.clear()
        msgs.addAll(target.msgs.map { ChatMsg(it.s, it.t, it.p?.takeIf { p -> File(p).exists() }) })
        attempts = target.attempts
        needPhoto = target.needPhoto
        busy = false
        save(c)
        saveArchive(c)
    }

    fun deleteChat(c: Context, index: Int) {
        val gone = archive.getOrNull(index) ?: return
        archive.removeAt(index)
        gone.msgs.forEach { m -> m.p?.let { runCatching { File(it).delete() } } }
        saveArchive(c)
    }

    fun send(c: Context, name: String, text: String, photo: PreparedImage? = null) {
        if (busy) return
        val app = c.applicationContext
        var photoPath: String? = null
        if (photo != null) {
            val dir = File(app.filesDir, "chat").apply { mkdirs() }
            val f = File(dir, "${System.currentTimeMillis()}.jpg")
            runCatching { f.writeBytes(photo.bytes) }.onSuccess { photoPath = f.absolutePath }
        }
        msgs.add(ChatMsg(true, text, photoPath))
        attempts++
        needPhoto = false
        busy = true
        save(app)
        val history = msgs.dropLast(1).takeLast(8).map { TutorTurn(if (it.fromStudent) "student" else "tutor", it.text) }
        val attemptNo = attempts
        val usePro = pro
        scope.launch {
            val reply = runCatching {
                val ctxList = studying ?: withContext(Dispatchers.IO) {
                    runCatching {
                        Repository.myPages().map { if (it.chapter.isBlank()) it.subject else "${it.subject}: ${it.chapter}" }.distinct().take(5)
                    }.getOrDefault(emptyList())
                }.also { studying = it }
                val b64 = photo?.let { android.util.Base64.encodeToString(it.bytes, android.util.Base64.NO_WRAP) }
                Repository.askTutor(
                    TutorRequest(message = text, attempts = attemptNo, imageBase64 = b64, history = history, studying = ctxList, pro = usePro),
                )
            }.getOrElse { TutorReply(Errors.tutor(it), "error") }
            msgs.add(ChatMsg(false, reply.reply))
            needPhoto = reply.mode == "ask_photo"
            reply.credits?.let { credits = it }
            if (reply.mode == "no_credits") pro = false
            toSpeak = reply.reply
            busy = false
            save(app)
        }
    }

    fun forgetOnSignOut(c: Context) {
        loaded = false
        studying = null
        msgs.clear()
        archive.clear()
        attempts = 0
        needPhoto = false
        busy = false
        pro = false
        credits = -1
        refCode = ""
        Prefs.setTutorChat(c, null)
        Prefs.setTutorArchive(c, null)
        runCatching { File(c.filesDir, "chat").deleteRecursively() }
    }
}
