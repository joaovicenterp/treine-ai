package com.treineai.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.treineai.app.data.Catalog
import com.treineai.app.data.Repo
import com.treineai.app.data.Settings
import com.treineai.app.data.UserData
import com.treineai.app.motion.MotionAnalysisService
import com.treineai.app.voice.Feedback
import com.treineai.app.voice.VoiceCommands
import kotlin.math.roundToInt

/**
 * Composição do score do treino, idêntica à da versão web:
 * técnica 40%, consistência 20%, amplitude 22%, controle 18%.
 * A consistência penaliza o desvio-padrão entre exercícios — treinar
 * bem em todos vale mais do que acertar um e errar os outros.
 */
fun scoreBreakdown(results: List<com.treineai.app.data.ExerciseSession>): com.treineai.app.data.ScoreBreakdown {
    if (results.isEmpty()) return com.treineai.app.data.ScoreBreakdown()
    val scores = results.map { it.score.toDouble() }
    val mean = scores.average()
    val sd = kotlin.math.sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size)
    fun clamp(v: Double) = v.coerceIn(0.0, 100.0)

    val tecnica = mean.roundToInt()
    val consistencia = clamp(100 - sd * 2.2).roundToInt()
    val amplitude = clamp(results.map { (if (it.avgDepth > 0) it.avgDepth else .8) * 100 }.average()).roundToInt()
    val controle = clamp(results.map {
        val t = if (it.avgTempo > 0) it.avgTempo else 1.0
        (40 + (t / 1.6) * 60).coerceIn(0.0, 100.0)
    }.average()).roundToInt()
    val total = (tecnica * .40 + consistencia * .20 + amplitude * .22 + controle * .18).roundToInt()
    return com.treineai.app.data.ScoreBreakdown(tecnica, consistencia, amplitude, controle, total)
}

/* ============================================================
   ESTADO DA APLICAÇÃO
   Uma pilha de telas, o repositório local, a voz e o serviço de
   análise. As telas só leem daqui — nenhuma delas conhece câmera,
   MediaPipe ou armazenamento.
   ============================================================ */
class AppState(app: Application) : AndroidViewModel(app) {

    val catalog: Catalog = Catalog.parse(
        app.assets.open("catalog.json").bufferedReader().use { it.readText() }
    )

    val repo = Repo(app, catalog)
    val feedback = Feedback(app)
    val voice = VoiceCommands(app, feedback)
    val motion = MotionAnalysisService(app)

    /* ---------------- pilha de navegação ---------------- */
    private val stack = mutableStateListOf<Route>(Route.Splash)
    val route: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** Redesenha as telas quando os dados mudam, como o `data:change` da web. */
    var revision by mutableStateOf(0); private set
    fun touch() { revision++ }

    /** Mensagem passageira mostrada no rodapé — erros de voz, avisos de plano. */
    var toast by mutableStateOf<String?>(null)

    /** Conquistas recém-desbloqueadas, exibidas em folha de comemoração. */
    val celebrating = mutableStateListOf<com.treineai.app.data.AchievementDef>()

    /** Exercícios já analisados no treino em andamento. */
    val sessionResults = mutableStateListOf<com.treineai.app.data.ExerciseSession>()

    /** XP creditado no último treino, mostrado na tela de resultado. */
    var lastXp by mutableStateOf(0); private set

    init {
        feedback.init()
        if (repo.restore()) syncVoiceSettings()
    }

    val settings: Settings get() = repo.data().settings
    val user: UserData get() = repo.data()

    fun go(r: Route) {
        feedback.silence()
        stack.add(r)
    }

    fun replace(r: Route) {
        feedback.silence()
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(r)
    }

    fun resetTo(r: Route) {
        feedback.silence()
        stack.clear()
        stack.add(r)
    }

    fun back(fallback: Route = Route.Home) {
        feedback.silence()
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else resetTo(fallback)
    }

    fun tab(r: Route) {
        if (route.name == r.name) return
        resetTo(r)
    }

    /* ---------------- ajustes que afetam a voz ---------------- */
    fun patchSettings(block: (Settings) -> Settings) {
        repo.patchSettings(block(repo.data().settings))
        syncVoiceSettings()
        touch()
    }

    fun syncVoiceSettings() {
        val s = repo.data().settings
        feedback.settings = s
        if (s.voiceCommands && voice.available() && voice.hasMicPermission()) voice.enable()
        else voice.disable()
    }

    /** Confere conquistas e guarda as novas para a folha de comemoração. */
    fun checkAchievements() {
        val won = repo.checkAchievements()
        if (won.isNotEmpty()) {
            celebrating.addAll(won)
            feedback.success()
        }
        touch()
    }

    /**
     * Decide se um exercício pode ser analisado agora: o plano gratuito
     * tem um limite diário e a biblioteca é parcial.
     * @return null se pode; caso contrário, o motivo para a tela de planos.
     */
    fun blockReason(exId: String): String? {
        val ex = catalog.exercise(exId) ?: return null
        if (repo.exerciseLocked(ex)) return "Este exercício faz parte do TREINE AI PRO."
        val left = repo.analysesLeft()
        if (left != null && left <= 0)
            return "Você usou as ${catalog.d.config.free.analysesPerDay} análises de hoje."
        return null
    }

    /**
     * Fecha o treino: monta o registro, credita XP e confere conquistas.
     * Mesma composição de score da versão web — técnica 40%, consistência 20%,
     * amplitude 22%, controle 18%.
     * @return o id do registro salvo, ou null se nada foi analisado.
     */
    fun finishWorkout(plan: SessionPlan): String? {
        motion.detach()
        voice.suspend()
        if (sessionResults.isEmpty()) {
            resetTo(Route.Home)
            toast = "Treino encerrado sem exercícios analisados."
            return null
        }
        val results = sessionResults.toList()
        val bd = scoreBreakdown(results)
        val prev = repo.data().workouts.firstOrNull()
        val startedAt = if (plan.startedAt > 0) plan.startedAt else System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance()
            .apply { timeInMillis = startedAt }
            .get(java.util.Calendar.HOUR_OF_DAY)

        val rec = com.treineai.app.data.WorkoutRecord(
            id = com.treineai.app.data.uid(),
            date = com.treineai.app.data.dayKey(),
            ts = System.currentTimeMillis(),
            workoutId = plan.workoutId.orEmpty(),
            name = plan.workoutName.ifEmpty { "Treino livre" },
            focus = plan.workoutId?.let { catalog.workout(it)?.focus }.orEmpty(),
            duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt(),
            score = bd.total,
            breakdown = bd,
            startedHour = hour,
            sessions = results,
            prevScore = prev?.score
        )
        repo.addWorkout(rec)

        var xp = 100 + results.size * 10
        if (bd.total >= 90) xp += 25
        repo.addXp(xp)
        lastXp = xp
        sessionResults.clear()
        checkAchievements()
        return rec.id
    }

    override fun onCleared() {
        motion.detach()
        voice.release()
        feedback.release()
        super.onCleared()
    }
}
