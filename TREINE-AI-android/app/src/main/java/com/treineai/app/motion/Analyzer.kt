package com.treineai.app.motion

import com.treineai.app.data.Exercise
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Arredonda para duas casas exatamente como o `toFixed(2)` da versão web. */
internal fun fix2(v: Double): Double =
    if (v.isNaN() || v.isInfinite()) v
    else BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toDouble()

/* ============================================================
   ANALISADOR — máquina de estados da repetição e do score.
   Puro Kotlin: não conhece câmera, Android nem interface, o que
   permite testá-lo isoladamente.
   ============================================================ */
class Analyzer(
    val exercise: Exercise,
    val targetReps: Int,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    var onRep: ((RepData) -> Unit)? = null
    var onQuality: ((Int) -> Unit)? = null
    var onFeedback: ((Issue) -> Unit)? = null
    var onBlocked: ((Boolean) -> Unit)? = null
    var onTarget: (() -> Unit)? = null
    var onHold: ((Double) -> Unit)? = null

    private val cfg = exercise.rep
    private val isHold = exercise.hold
    private val checks = exercise.checks

    /* uma única instância: as regras de estabilidade dependem do histórico */
    private val ctx = RuleContext(Metrics(Kinematics.pose("squat", 0.0)), 0.0, cfg.joint, cfg.bottom < cfg.top)

    var angle = 0.0; private set
    private var sm = 0.0
    var p = 0.0; private set
    private var phase = "rest"
    private var maxP = 0.0
    private var lastRest = 0.0
    private var peakAt = 0L
    private var tPhase = 0L
    private var tStart = 0L
    private var qEma = 100.0

    var quality = 100; private set
    var repCount = 0; private set
    var validCount = 0; private set
    var holdSeconds = 0.0; private set
    var blocked = false; private set
    var running = false; private set
    var paused = false; private set

    val reps = mutableListOf<RepData>()
    private var live: List<Issue> = emptyList()
    private val cooldown = HashMap<String, Long>()
    private var lastFeedback = 0L
    private var positiveAt = 0L
    private var targetFired = false
    private var positiveIdx = 0

    fun start() {
        running = true; paused = false
        tStart = now(); tPhase = tStart
        reps.clear(); repCount = 0; validCount = 0
        phase = "rest"; maxP = 0.0; sm = 0.0
        quality = 100; qEma = 100.0
        holdSeconds = 0.0; blocked = false; targetFired = false
        cooldown.clear(); lastFeedback = 0; positiveAt = 0
        ctx.reset()
    }

    fun pause() { paused = true }
    fun resume() { paused = false; tPhase = now() }
    fun stop() { running = false }

    /** Chamado a cada quadro com os pontos detectados. */
    fun onFrame(lm: List<Landmark>) {
        if (!running || paused) return
        val m = Metrics(lm)
        if (isHold) { analyseHold(m); return }

        val raw = Joints.value(m, cfg.joint)
        angle = raw
        sm = if (sm == 0.0) raw else sm * .68 + raw * .32
        val span = (cfg.bottom - cfg.top).let { if (abs(it) < 1e-6) 1.0 else it }
        p = (sm - cfg.top) / span

        ctx.m = m; ctx.p = p; ctx.joint = cfg.joint; ctx.down = cfg.bottom < cfg.top

        val active = checks.filter { it in Rules.LIVE }.mapNotNull { Rules.live(it, ctx) }
        val pen = active.sumOf { it.weight }
        val inst = clamp(100.0 - pen, 0.0, 100.0)
        qEma = qEma * .90 + inst * .10
        quality = qEma.roundToInt()
        onQuality?.invoke(quality)

        val crit = active.firstOrNull { it.level >= 4 }
        if (crit != null && !blocked) { blocked = true; say(crit, force = true); onBlocked?.invoke(true) }
        else if (crit == null && blocked) { blocked = false; onBlocked?.invoke(false) }

        active.filter { it.level < 4 }.forEach { say(it) }
        live = active
        if (blocked) return

        val t = now()
        if (phase == "rest") {
            if (p > .55) { phase = "work"; tPhase = t; maxP = p }
            else lastRest = p
        } else {
            maxP = max(maxP, p)
            if (p >= .96 && peakAt == 0L) peakAt = t
            if (p < .25) {
                /* Sem pico registrado não dá para separar descida de subida:
                   ambas medem a repetição inteira, como na versão web — zerar
                   a subida dispararia "não acelere" em toda repetição curta. */
                val tDown = ((if (peakAt == 0L) t else peakAt) - tPhase) / 1000.0
                val tUp = (t - (if (peakAt == 0L) tPhase else peakAt)) / 1000.0
                peakAt = 0L
                completeRep(maxP, tDown, tUp, abs(lastRest))
                phase = "rest"; maxP = 0.0; tPhase = t
            }
        }
    }

    private fun analyseHold(m: Metrics) {
        ctx.m = m; ctx.p = 0.0; ctx.joint = "hold"; ctx.down = true
        val active = checks.filter { it in Rules.LIVE }.mapNotNull { Rules.live(it, ctx) }
        val pen = active.sumOf { it.weight }
        qEma = qEma * .93 + clamp(100.0 - pen, 0.0, 100.0) * .07
        quality = qEma.roundToInt()
        onQuality?.invoke(quality)
        active.forEach { say(it) }
        live = active
        holdSeconds = (now() - tStart) / 1000.0
        onHold?.invoke(holdSeconds)
    }

    private fun completeRep(depth: Double, tDown: Double, tUp: Double, restP: Double) {
        val issues = mutableListOf<Issue>()
        checks.filter { it in Rules.REP }.forEach { name ->
            Rules.rep(name, ctx, depth, tDown, tUp, restP)?.let { issues.add(it) }
        }
        live.forEach { l -> if (issues.none { it.code == l.code }) issues.add(l) }

        val penalty = issues.sumOf { it.weight }
        val score = clamp(100.0 - penalty, 0.0, 100.0).roundToInt()
        val valid = depth >= .82 && issues.none { it.level >= 3 }

        repCount++
        if (valid) validCount++
        val rec = RepData(
            index = repCount, valid = valid,
            depth = fix2(depth), tDown = fix2(tDown), tUp = fix2(tUp),
            restP = restP, score = score, issues = issues.map { it.code }
        )
        reps.add(rec)
        onRep?.invoke(rec)

        val worst = issues.sortedWith(compareByDescending<Issue> { it.level }.thenByDescending { it.weight }).firstOrNull()
        if (worst != null) say(worst)
        else if (now() - positiveAt > 9000) {
            positiveAt = now()
            val msg = POSITIVES[positiveIdx % POSITIVES.size]; positiveIdx++
            say(Issue("good", 0, msg, 0))
        }

        if (repCount >= targetReps && !targetFired) { targetFired = true; onTarget?.invoke() }
    }

    /** Alerta inteligente: prioridade + anti-repetição. */
    private fun say(issue: Issue, force: Boolean = false) {
        val t = now()
        val cd = when (issue.level) {
            0 -> 9000L; 1 -> 7000L; 2 -> 5000L; 3 -> 4000L; else -> 6000L
        }
        if (!force) {
            val last = cooldown[issue.code]
            if (last != null && t - last < cd) return
            if (t - lastFeedback < 1200) return
        }
        cooldown[issue.code] = t
        lastFeedback = t
        onFeedback?.invoke(issue)
    }

    fun summary(): ExerciseSummary {
        val dur = ((now() - tStart) / 1000.0).roundToInt()
        val scores = reps.map { it.score }
        /* ordem de inserção preservada: em caso de empate vence o erro
           encontrado primeiro, como na ordenação estável da versão web */
        val errCount = LinkedHashMap<String, Int>()
        reps.forEach { r -> r.issues.forEach { errCount[it] = (errCount[it] ?: 0) + 1 } }
        val top = errCount.entries.maxByOrNull { it.value }?.key
        return ExerciseSummary(
            reps = repCount,
            validReps = validCount,
            invalid = repCount - validCount,
            duration = dur,
            holdSeconds = holdSeconds.roundToInt(),
            score = if (scores.isNotEmpty()) scores.average().roundToInt() else qEma.roundToInt(),
            best = scores.maxOrNull() ?: qEma.roundToInt(),
            worst = scores.minOrNull() ?: qEma.roundToInt(),
            avgDepth = if (reps.isNotEmpty()) fix2(reps.sumOf { it.depth } / reps.size) else 0.0,
            avgTempo = if (reps.isNotEmpty()) fix2(reps.sumOf { it.tDown } / reps.size) else 0.0,
            errors = errCount,
            mainError = top,
            repDetail = reps.toList()
        )
    }
}
