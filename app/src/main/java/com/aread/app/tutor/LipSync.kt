package com.aread.app.tutor

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

data class Mouth(val jawOpen: Float = 0f, val funnel: Float = 0f, val pucker: Float = 0f, val smile: Float = 0f, val closed: Float = 1f)

class LipSyncSpeaker(context: Context) {
    var mouth by mutableStateOf(Mouth())
        private set
    var speaking by mutableStateOf(false)
        private set

    private var ready = false
    private var currentText = ""
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = Locale.US
            tts.setSpeechRate(0.95f)
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) { speaking = false; mouth = Mouth() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { speaking = false; mouth = Mouth() }
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                val ch = currentText.getOrNull(start)?.lowercaseChar() ?: return
                mouth = visemeFor(ch)
            }
        })
    }

    fun speak(text: String) {
        if (!ready) return
        currentText = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aread-${System.nanoTime()}")
    }

    fun stop() { tts.stop(); speaking = false; mouth = Mouth() }
    fun release() { tts.stop(); tts.shutdown() }

    private fun visemeFor(c: Char): Mouth = when (c) {
        'a' -> Mouth(jawOpen = 0.85f, closed = 0f)
        'e' -> Mouth(jawOpen = 0.5f, smile = 0.4f, closed = 0f)
        'i', 'y' -> Mouth(jawOpen = 0.3f, smile = 0.7f, closed = 0f)
        'o' -> Mouth(jawOpen = 0.55f, funnel = 0.7f, closed = 0f)
        'u', 'w' -> Mouth(jawOpen = 0.2f, pucker = 0.9f, closed = 0f)
        'b', 'm', 'p' -> Mouth(closed = 1f)
        'f', 'v' -> Mouth(jawOpen = 0.15f, closed = 0.3f)
        's', 'z', 't', 'd', 'n', 'l', 'r', 'c', 'k', 'g', 'j', 'h', 'q', 'x' -> Mouth(jawOpen = 0.25f, smile = 0.2f, closed = 0f)
        else -> Mouth()
    }
}
