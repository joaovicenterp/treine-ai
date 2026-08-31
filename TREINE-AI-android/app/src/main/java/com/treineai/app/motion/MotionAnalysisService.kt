package com.treineai.app.motion

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.treineai.app.data.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/* ============================================================
   MOTION ANALYSIS SERVICE

        Interface  →  MotionAnalysisService  →  PoseProvider

   A interface nunca fala com a câmera nem com o MediaPipe: ela
   observa este estado. Trocar a engine de visão computacional
   não muda uma linha das telas.
   ============================================================ */

@Immutable
data class MotionState(
    val providerId: String = "",
    val providerLabel: String = "",
    val landmarks: List<Landmark>? = null,
    val setup: SetupState = SetupState(),
    val quality: Int = 100,
    val reps: Int = 0,
    val validReps: Int = 0,
    val progress: Double = 0.0,
    val angle: Double = 0.0,
    val holdSeconds: Double = 0.0,
    val blocked: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false
)

class MotionAnalysisService(private val context: Context) {

    private val _state = MutableStateFlow(MotionState())
    val state: StateFlow<MotionState> = _state.asStateFlow()

    /* eventos pontuais — o equivalente aos `emit` da versão web */
    var onRep: ((RepData) -> Unit)? = null
    var onFeedback: ((Issue) -> Unit)? = null
    var onTarget: (() -> Unit)? = null

    private var provider: PoseProvider? = null
    private var analyzer: Analyzer? = null
    private var exercise: Exercise? = null

    /** Escolhe a pose real; cai para a simulação se o modelo não abrir. */
    fun attach(exercise: Exercise, targetReps: Int, forceSimulation: Boolean = false) {
        detach()
        this.exercise = exercise
        val p: PoseProvider = (if (forceSimulation) null else MediaPipeProvider.create(context))
            ?: SimulationProvider(exercise.pattern, exercise.rep)
        provider = p

        analyzer = Analyzer(exercise, targetReps).also { a ->
            a.onQuality = { q -> _state.value = _state.value.copy(quality = q) }
            a.onBlocked = { b -> _state.value = _state.value.copy(blocked = b) }
            a.onHold = { s -> _state.value = _state.value.copy(holdSeconds = s) }
            a.onRep = { r ->
                _state.value = _state.value.copy(reps = a.repCount, validReps = a.validCount)
                onRep?.invoke(r)
            }
            a.onFeedback = { i -> onFeedback?.invoke(i) }
            a.onTarget = { onTarget?.invoke() }
        }
        _state.value = MotionState(providerId = p.id, providerLabel = p.label)
    }

    fun start() { analyzer?.start(); _state.value = _state.value.copy(running = true, paused = false) }
    fun pause() { analyzer?.pause(); _state.value = _state.value.copy(paused = true) }
    fun resume() { analyzer?.resume(); _state.value = _state.value.copy(paused = false) }
    fun stop() { analyzer?.stop(); _state.value = _state.value.copy(running = false) }

    fun detach() {
        analyzer?.stop(); analyzer = null
        provider?.close(); provider = null
    }

    /** Um quadro da câmera: estima a pose, confere o posicionamento e analisa. */
    fun onFrame(frame: Bitmap?, brightness: Double?, timestampMs: Long) {
        val p = provider ?: return
        val lm = p.estimate(frame, timestampMs)
        val setup = checkSetup(lm, brightness, exercise)
        val a = analyzer
        if (lm != null && a != null && a.running && !a.paused) a.onFrame(lm)
        _state.value = _state.value.copy(
            landmarks = lm,
            setup = setup,
            progress = a?.p ?: 0.0,
            angle = a?.angle ?: 0.0
        )
    }

    fun summary(): ExerciseSummary? = analyzer?.summary()
    val repDetail: List<RepData> get() = analyzer?.reps.orEmpty()
}
