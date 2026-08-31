package com.treineai.parity

import com.treineai.app.data.Exercise
import com.treineai.app.motion.*
import kotlin.math.roundToInt

/* ============================================================
   Arnês de paridade, etapa 2
   a) checkSetup sob entradas degradadas — todos os avisos
   b) motor de repetição ao longo do tempo — contagem, score,
      qualidade e regras de estabilidade
   ============================================================ */

private fun r4(v: Double): Double = (v * 10000).roundToInt() / 10000.0
private fun esc(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/* --- transformações que degradam a captura --- */
private fun scaleShift(lm: List<Landmark>, k: Double, dx: Double, dy: Double): List<Landmark> =
    lm.map { Landmark(.5 + (it.x - .5) * k + dx, .5 + (it.y - .5) * k + dy, it.z, it.visibility) }

private fun hide(lm: List<Landmark>, idx: Set<Int>): List<Landmark> =
    lm.mapIndexed { i, p -> if (i in idx) Landmark(p.x, p.y, p.z, 0.05) else p }

private data class Case(val name: String, val lm: List<Landmark>?, val bright: Double?)

private fun cases(base: List<Landmark>): List<Case> = listOf(
    Case("normal", base, .5),
    Case("escuro", base, .05),
    Case("semPose", null, .5),
    Case("longe", scaleShift(base, .35, 0.0, 0.0), .5),
    Case("muitoLonge", scaleShift(base, .2, 0.0, 0.0), .5),
    Case("limiteDistancia", scaleShift(base, .62, 0.0, 0.0), .5),
    Case("perto", scaleShift(base, 2.4, 0.0, 0.0), .5),
    Case("cortadoEmbaixo", scaleShift(base, 1.6, 0.0, .45), .5),
    Case("cortadoEmCima", scaleShift(base, 1.6, 0.0, -.55), .5),
    Case("esquerda", scaleShift(base, 1.0, -.35, 0.0), .5),
    Case("direita", scaleShift(base, 1.0, .35, 0.0), .5),
    Case("pernasOcultas", hide(base, setOf(25, 26, 27, 28, 29, 30, 31, 32)), .5),
    Case("bracosOcultos", hide(base, setOf(13, 14, 15, 16, 17, 18, 19, 20, 21, 22)), .5),
    Case("troncoOculto", hide(base, setOf(11, 12, 23, 24)), .5),
    Case("escuroELonge", scaleShift(base, .2, 0.0, 0.0), .05)
)

/* --- gerador de série: sobe e desce entre repouso e pico --- */
private fun series(ex: Exercise, reps: Int, frames: Int, shallow: Double, jitter: Double): List<Pair<Long, List<Landmark>>> {
    val out = ArrayList<Pair<Long, List<Landmark>>>()
    var t = 0L
    var seed = 12345L
    fun rnd(): Double { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33).toDouble() / (1L shl 31).toDouble()) % 1.0 }
    for (r in 0 until reps) {
        for (f in 0 until frames) {
            val phase = f.toDouble() / (frames - 1)
            /* triângulo: 0 → 1 → 0 */
            val tri = if (phase < .5) phase * 2 else (1 - phase) * 2
            val amp = 1.0 - shallow * (r % 2)
            val p = clamp(tri * amp + (rnd() - .5) * jitter, 0.0, 1.0)
            val e = PoseError(lean = (r % 3) * .25, asym = if (r % 2 == 0) .3 else 0.0)
            out.add(t to Kinematics.pose(ex.pattern, p, e, ex.rep))
            t += 33
        }
        t += 400
    }
    return out
}

fun main() {
    val out = StringBuilder()
    out.append("{\n\"setup\":[\n")
    var first = true
    for (ex in FIXTURES) {
        val base = Kinematics.pose(ex.pattern, .25, PoseError(), ex.rep)
        for (c in cases(base)) {
            val s = checkSetup(c.lm, c.bright, ex)
            if (!first) out.append(",\n"); first = false
            out.append("{\"ex\":").append(esc(ex.id))
                .append(",\"case\":").append(esc(c.name))
                .append(",\"hint\":").append(esc(s.hint))
                .append(",\"body\":").append(s.body)
                .append(",\"light\":").append(s.light)
                .append(",\"distance\":").append(s.distance)
                .append(",\"full\":").append(s.full)
                .append(",\"framing\":").append(s.framing)
                .append(",\"ready\":").append(s.ready)
                .append(",\"size\":").append(r4(s.size))
                .append("}")
        }
    }

    out.append("\n],\n\"engine\":[\n")
    first = true
    for (ex in FIXTURES) {
        val frames = series(ex, reps = 6, frames = 26, shallow = .3, jitter = .04)
        var clock = 0L
        val az = Analyzer(ex, targetReps = 5, now = { clock })
        val fb = ArrayList<String>()
        val qualities = ArrayList<Int>()
        var blockedTimes = 0
        var targetAt = -1
        az.onFeedback = { fb.add(it.code) }
        az.onQuality = { qualities.add(it) }
        az.onBlocked = { if (it) blockedTimes++ }
        az.onTarget = { targetAt = az.repCount }
        clock = 0
        az.start()
        for ((ts, lm) in frames) { clock = ts; az.onFrame(lm) }
        clock = frames.last().first + 100
        val sum = az.summary()

        if (!first) out.append(",\n"); first = false
        out.append("{\"ex\":").append(esc(ex.id))
            .append(",\"reps\":").append(sum.reps)
            .append(",\"valid\":").append(sum.validReps)
            .append(",\"score\":").append(sum.score)
            .append(",\"best\":").append(sum.best)
            .append(",\"worst\":").append(sum.worst)
            .append(",\"avgDepth\":").append(r4(sum.avgDepth))
            .append(",\"avgTempo\":").append(r4(sum.avgTempo))
            .append(",\"mainError\":").append(sum.mainError?.let { esc(it) } ?: "null")
            .append(",\"errors\":{").append(sum.errors.entries.sortedBy { it.key }.joinToString(",") { esc(it.key) + ":" + it.value }).append("}")
            .append(",\"blocked\":").append(blockedTimes)
            .append(",\"targetAt\":").append(targetAt)
            .append(",\"quality\":").append(qualities.lastOrNull() ?: -1)
            .append(",\"feedback\":[").append(fb.joinToString(",") { esc(it) }).append("]")
            .append(",\"repScores\":[").append(sum.repDetail.joinToString(",") { it.score.toString() }).append("]")
            .append(",\"repDepth\":[").append(sum.repDetail.joinToString(",") { r4(it.depth).toString() }).append("]")
            .append("}")
    }
    out.append("\n]\n}\n")
    print(out)
}
