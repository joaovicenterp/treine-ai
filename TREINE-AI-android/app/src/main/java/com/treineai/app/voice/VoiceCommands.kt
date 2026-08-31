package com.treineai.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/* ============================================================
   Escuta contínua: liga o reconhecedor do Android, religa a cada
   frase e traduz o que ouviu num comando da tabela acima.
   ============================================================ */
class VoiceCommands(
    private val context: Context,
    private val feedback: Feedback
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    var listening = false; private set
    private var wanted = false
    var lastHeard: String = ""; private set
    var lastError: String? = null; private set

    var onCommand: ((cmd: String, text: String) -> Unit)? = null
    var onState: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun enable() { wanted = true; start() }

    fun disable() { wanted = false; stop() }

    /** Liga/desliga só a escuta, sem mudar a preferência do usuário. */
    fun suspend() { wanted = false; stop() }
    fun resumeListening() { wanted = true; start() }

    private fun start() {
        if (!wanted || listening) return
        if (!available() || !hasMicPermission()) return
        main.post {
            try {
                val r = SpeechRecognizer.createSpeechRecognizer(context)
                r.setRecognitionListener(listener)
                recognizer = r
                r.startListening(intent())
            } catch (_: Exception) {
                listening = false
            }
        }
    }

    private fun stop() {
        main.post {
            val r = recognizer
            recognizer = null
            listening = false
            try { r?.stopListening(); r?.destroy() } catch (_: Exception) {}
            onState?.invoke(false)
        }
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    /** O reconhecedor do Android encerra a cada frase: religamos enquanto o treino durar. */
    private fun restartSoon(delay: Long = 350) {
        listening = false
        onState?.invoke(false)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        if (wanted) main.postDelayed({ start() }, delay)
    }

    private fun handle(results: Bundle?) {
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        /* ignora a própria voz do app saindo pelo alto-falante */
        if (feedback.speaking || System.currentTimeMillis() - feedback.lastSpokeAt < 700) return
        for (text in list) {
            val cmd = Commands.match(text) ?: continue
            lastHeard = text
            feedback.tap()
            onCommand?.invoke(cmd, text)
            return
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listening = true; onState?.invoke(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onResults(results: Bundle?) {
            handle(results)
            restartSoon()
        }

        override fun onError(error: Int) {
            lastError = errorName(error)
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    wanted = false
                    onError?.invoke("Microfone bloqueado. Comandos de voz desativados.")
                    stop()
                }
                /* silêncio e "não entendi" são normais: só reinicia */
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartSoon(200)
                /* o reconhecedor ainda está ocupado: espera um pouco mais */
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(900)
                else -> restartSoon(1200)
            }
        }
    }

    private fun errorName(e: Int) = when (e) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network-timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
        else -> "unknown"
    }

    fun release() { disable() }
}
