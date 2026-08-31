package com.treineai.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.treineai.app.data.Settings
import com.treineai.app.motion.Issue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/* ============================================================
   FEEDBACK MULTIMODAL — voz, vibração e som.

   O app é feito para ser usado SEM olhar para a tela: a voz é o
   canal principal. A fila abaixo garante que uma correção urgente
   interrompa a contagem de repetições, e que nada se atropele.

   Prioridades:
     0  ambiente (elogio, contagem)  — descartável
     1  informativo (repetição, dica)
     2  correção                     — fura a fila
     3  crítico / instrução de setup — interrompe tudo
   ============================================================ */
class Feedback(private val context: Context) {

    private data class Entry(val text: String, val p: Int, val id: String)

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Entry>()
    private val lastSaid = HashMap<String, Long>()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingFlush = false

    @Volatile var speaking = false; private set
    @Volatile var lastSpokeAt = 0L; private set

    /** Preferências vivas: a interface troca este valor quando o usuário muda os ajustes. */
    @Volatile var settings: Settings = Settings()

    private val volumes = mapOf("baixo" to .45f, "medio" to .8f, "alto" to 1f)
    private val rate = 1.08f

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun init(onReady: (Boolean) -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.let { engine ->
                    val ptBr = Locale("pt", "BR")
                    val r = engine.setLanguage(ptBr)
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale("pt"))
                    }
                    engine.setSpeechRate(rate)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        /* corpo de bloco, não de expressão: main.post devolve Boolean
                           e a interface exige Unit — daí voltamos a fala à thread principal
                           sem propagar o retorno */
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { main.post { done() } }
                        @Deprecated("substituído pela sobrecarga com errorCode")
                        override fun onError(utteranceId: String?) { main.post { done() } }
                        override fun onError(utteranceId: String?, errorCode: Int) { main.post { done() } }
                    })
                }
                if (pendingFlush) { pendingFlush = false; pump() }
            }
            onReady(ttsReady)
        }
    }

    /* ---------------- fila de voz ---------------- */

    /**
     * @param p prioridade 0–3
     * @param id chave de anti-repetição
     * @param gap intervalo mínimo, em ms, entre duas falas com o mesmo id
     * @param always fala mesmo com o modo silencioso ligado
     */
    fun say(text: String?, p: Int = 1, id: String? = null, gap: Long = 0, always: Boolean = false) {
        val s = settings
        if (!s.voice || text.isNullOrBlank()) return
        /* no modo silencioso só passam correções e instruções */
        if (!s.audioFirst && p < 2 && !always) return

        val key = id ?: text
        val now = System.currentTimeMillis()
        if (gap > 0) {
            val last = lastSaid[key]
            if (last != null && now - last < gap) return
        }
        lastSaid[key] = now

        val entry = Entry(text, p, key)
        when {
            /* crítico: interrompe o que estiver falando */
            p >= 3 -> {
                queue.retainAll { it.p >= 3 }
                stopSpeaking()
                queue.addLast(entry)
            }
            /* correção: fura a fila, mas deixa terminar a palavra atual
               (cortar "três" no meio confunde mais do que ajuda) */
            p == 2 -> {
                queue.retainAll { it.p >= 2 }
                queue.addFirst(entry)
            }
            else -> queue.addLast(entry)
        }
        while (queue.size > 5) queue.removeLast()
        pump()
    }

    /** Interrompe tudo — usado ao trocar de tela. */
    fun silence() { queue.clear(); stopSpeaking() }

    private fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
        speaking = false
    }

    private fun done() {
        speaking = false
        lastSpokeAt = System.currentTimeMillis()
        pump()
    }

    private fun pump() {
        if (speaking || queue.isEmpty()) return
        if (!ttsReady) { pendingFlush = true; return }
        val item = queue.removeFirst()
        val vol = volumes[settings.voiceVolume] ?: .8f
        speaking = true
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol) }
        val r = try {
            tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, item.id) ?: TextToSpeech.ERROR
        } catch (_: Exception) { TextToSpeech.ERROR }
        if (r != TextToSpeech.SUCCESS) done()
    }

    /* ---------------- vibração ---------------- */
    fun vibrate(vararg pattern: Long) {
        if (!settings.haptics) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
                else VibrationEffect.createWaveform(longArrayOf(0, *pattern), -1)
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                if (pattern.size == 1) v.vibrate(pattern[0]) else v.vibrate(longArrayOf(0, *pattern), -1)
            }
        } catch (_: Exception) {}
    }

    /* ---------------- som ---------------- */
    /** Senoide curta com envelope, equivalente ao oscilador da versão web. */
    fun tone(freq: Double, ms: Int, gain: Double = .12) {
        if (!settings.sounds) return
        val vol = (volumes[settings.voiceVolume] ?: .8f) * gain
        Thread {
            try {
                val sr = 22050
                val n = sr * ms / 1000
                val buf = ShortArray(n)
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    /* ataque de 12 ms e queda exponencial, como na web */
                    val env = (if (t < .012) t / .012 else Math.exp(-6.0 * (t - .012) / (ms / 1000.0)))
                    buf[i] = (sin(2 * PI * freq * t) * env * vol * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.setNotificationMarkerPosition(n)
                track.play()
                Thread.sleep(ms.toLong() + 60)
                track.release()
            } catch (_: Throwable) {}
        }.start()
    }

    /* ---------------- durante o treino ---------------- */
    fun coach(issue: Issue) {
        when {
            issue.level >= 4 -> {
                say(issue.msg, p = 3, id = issue.code, gap = 4000)
                vibrate(90, 70, 90, 70, 140); tone(220.0, 260, .16)
            }
            issue.level == 3 -> {
                say(issue.msg, p = 2, id = issue.code, gap = 3500)
                vibrate(50, 60, 50); tone(320.0, 160, .10)
            }
            issue.level == 2 -> {
                tone(420.0, 120, .09); vibrate(35)
                say(issue.msg, p = 2, id = issue.code, gap = 4500)
            }
            issue.level == 1 -> say(issue.msg, p = 0, id = issue.code, gap = 7000)
            else -> {
                vibrate(18); tone(760.0, 90, .07)
                say(issue.msg, p = 0, id = "good", gap = 9000)
            }
        }
    }

    /** Contagem falada de repetições. */
    fun rep(n: Int, valid: Boolean) {
        if (valid) { tone(660.0, 70, .08); vibrate(14) }
        else { tone(300.0, 90, .07); vibrate(20, 40, 20) }
        if (settings.audioFirst) {
            say(if (valid) n.toString() else "$n, incompleta", p = 1, id = "rep", gap = 250)
        }
    }

    fun countdown(n: Int) {
        tone(if (n == 0) 880.0 else 520.0, if (n == 0) 220 else 110, .12)
        vibrate(if (n == 0) 60 else 22)
        say(if (n == 0) "Vai!" else n.toString(), p = 3, always = true, id = "cd$n")
    }

    fun success() {
        listOf(523.0, 659.0, 784.0).forEachIndexed { i, f ->
            main.postDelayed({ tone(f, 180, .10) }, i * 110L)
        }
        vibrate(30, 50, 30, 50, 80)
    }

    fun tap() = vibrate(8)

    fun release() {
        silence()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null; ttsReady = false
    }
}
