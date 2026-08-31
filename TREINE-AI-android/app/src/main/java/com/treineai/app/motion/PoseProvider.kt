package com.treineai.app.motion

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/* ============================================================
   CAMADA DE ABSTRAÇÃO DA VISÃO COMPUTACIONAL

        UI  →  MotionAnalysisService  →  PoseProvider

   Dois providers, exatamente como na versão web:
     • MediaPipeProvider  — pose real, 33 pontos, na GPU do aparelho
     • SimulationProvider — atleta sintético, para demonstrar a
                            interface quando o modelo não carrega

   Trocar de provider não exige mudança nenhuma nas telas.
   ============================================================ */
interface PoseProvider {
    val id: String
    val label: String
    val ready: Boolean
    fun estimate(frame: Bitmap?, timestampMs: Long): List<Landmark>?
    fun close()
}

/* ------------------------------------------------------------
   Pose real. O modelo .task vai empacotado nos assets do APK:
   nada é baixado em execução, então funciona sem internet.
   ------------------------------------------------------------ */
class MediaPipeProvider private constructor(private val landmarker: PoseLandmarker) : PoseProvider {
    override val id = "mediapipe"
    override val label = "IA ATIVA"
    override var ready = true; private set

    private var lastTs = 0L

    override fun estimate(frame: Bitmap?, timestampMs: Long): List<Landmark>? {
        if (!ready || frame == null) return null
        /* o MediaPipe exige carimbos de tempo estritamente crescentes */
        val ts = if (timestampMs <= lastTs) lastTs + 1 else timestampMs
        lastTs = ts
        val result: PoseLandmarkerResult = try {
            landmarker.detectForVideo(BitmapImageBuilder(frame).build(), ts)
        } catch (e: Exception) {
            return null
        }
        val poses = result.landmarks()
        if (poses.isEmpty()) return null
        return poses[0].map { Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble(), it.visibility().orElse(0.95f).toDouble()) }
    }

    override fun close() {
        ready = false
        try { landmarker.close() } catch (_: Exception) {}
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"

        /** Tenta a GPU e cai para a CPU: aparelhos antigos falham no delegate GPU. */
        fun create(context: Context): MediaPipeProvider? {
            for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
                try {
                    val base = BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(delegate)
                        .build()
                    val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.VIDEO)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(.5f)
                        .setMinPosePresenceConfidence(.5f)
                        .setMinTrackingConfidence(.5f)
                        .build()
                    return MediaPipeProvider(PoseLandmarker.createFromOptions(context, options))
                } catch (_: Throwable) {
                    /* tenta o próximo delegate */
                }
            }
            return null
        }
    }
}

/* ------------------------------------------------------------
   Atleta sintético: executa o exercício com tendências de erro
   que mudam a cada poucas repetições, permitindo exercitar toda
   a interface sem câmera.
   ------------------------------------------------------------ */
class SimulationProvider(
    private val pattern: String,
    private val cfg: com.treineai.app.data.RepRange?,
    private val rnd: Random = Random.Default,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) : PoseProvider {
    override val id = "simulation"
    override val label = "MODO DEMO"
    override var ready = true; private set

    private val t0 = clock()
    private var period = 3000.0 + rnd.nextDouble() * 900
    private var err = PoseError()
    private var next = 2

    private fun ease(t: Double) = if (t < .5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

    private fun roll() {
        err = if (rnd.nextDouble() < .62) {
            val v = .45 + rnd.nextDouble() * .55
            when (rnd.nextInt(4)) {
                0 -> PoseError(lean = v)
                1 -> PoseError(asym = v)
                2 -> PoseError(shallow = v)
                else -> PoseError(drift = v)
            }
        } else PoseError()
        period = 2600.0 + rnd.nextDouble() * 1500
    }

    override fun estimate(frame: Bitmap?, timestampMs: Long): List<Landmark> {
        val t = (clock() - t0) / period
        val cyc = floor(t).toInt()
        val u = t - cyc
        if (cyc >= next) { next = cyc + 1 + rnd.nextInt(2); roll() }
        /* ciclo: descida (0→.42), pausa curta, subida (.52→1) */
        val p = when {
            u < .42 -> ease(u / .42)
            u < .52 -> 1.0
            else -> 1 - ease(clamp((u - .52) / .40, 0.0, 1.0))
        }
        val jitter = .004
        return Kinematics.pose(pattern, p, err, cfg).map {
            Landmark(it.x + (rnd.nextDouble() - .5) * jitter, it.y + (rnd.nextDouble() - .5) * jitter, 0.0, it.visibility)
        }
    }

    override fun close() { ready = false }
}
