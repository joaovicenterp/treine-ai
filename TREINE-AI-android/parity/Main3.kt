package com.treineai.parity

import com.treineai.app.motion.*
import kotlin.math.roundToInt
import kotlin.math.sin

/* ============================================================
   Arnês de paridade, etapa 3
   Exercita TODAS as 18 regras — inclusive as de estabilidade,
   que dependem de histórico — quadro a quadro, comparando
   código, nível e peso de cada alerta.
   ============================================================ */

private fun esc(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private val ALL_RULES = listOf(
    "depth", "rom", "tempo", "lockout", "symmetry", "torsoLean", "backNeutral",
    "kneeValgus", "hipSag", "headPos", "torsoStable", "momentum", "elbowDrift",
    "hipShoot", "hipLockout", "kneeLock", "hipStable", "shoulderDepth"
)

/* Deslocamento determinístico e agressivo: força o desvio-padrão
   das regras de estabilidade a cruzar todos os limiares. */
private fun wobble(lm: List<Landmark>, f: Int): List<Landmark> {
    val a = sin(f * 0.7) * 0.05
    val b = sin(f * 0.41 + 1.3) * 0.06
    val c = sin(f * 1.13) * 0.04
    return lm.mapIndexed { i, p ->
        when (i) {
            11, 12 -> Landmark(p.x + a, p.y + b * 0.6, p.z, p.visibility)
            13, 14 -> Landmark(p.x + b * 1.4, p.y + c, p.z, p.visibility)
            23, 24 -> Landmark(p.x + c, p.y + a * 1.2, p.z, p.visibility)
            25, 26 -> Landmark(p.x + b, p.y + c * 1.5, p.z, p.visibility)
            27, 28 -> Landmark(p.x + c * .5, p.y + a * .4, p.z, p.visibility)
            0 -> Landmark(p.x + b * 2, p.y + a, p.z, p.visibility)
            else -> p
        }
    }
}

fun main() {
    val out = StringBuilder("[\n")
    var first = true
    /* alguns padrões representativos cobrem todas as articulações */
    val picks = listOf("squat", "benchPress", "curl", "row", "plank", "hinge", "pullup", "lunge", "pushup", "overheadPress", "hipThrust", "legPress")
        .mapNotNull { pat -> FIXTURES.firstOrNull { it.pattern == pat } } +
        FIXTURES.filter { it.hold }.take(2)

    for (ex in picks.distinctBy { it.id }) {
        val ctx = RuleContext(Metrics(Kinematics.pose(ex.pattern, 0.0, PoseError(), ex.rep)), 0.0, ex.rep.joint, ex.rep.bottom < ex.rep.top)
        for (f in 0 until 45) {
            val phase = (f % 15) / 14.0
            val lm = wobble(Kinematics.pose(ex.pattern, phase, PoseError(lean = .3, asym = .4), ex.rep), f)
            ctx.m = Metrics(lm)
            /* varre p muito além de 1 para atingir shoulderDepth */
            ctx.p = phase * 1.4
            val live = ALL_RULES.filter { it in Rules.LIVE }.mapNotNull { Rules.live(it, ctx) }
            val depth = .95 - (f % 5) * .06
            val rep = ALL_RULES.filter { it in Rules.REP }.mapNotNull {
                Rules.rep(it, ctx, depth, .3 + (f % 4) * .5, .2 + (f % 3) * .4, (f % 6) * .05)
            }
            if (!first) out.append(",\n"); first = false
            out.append("{\"ex\":").append(esc(ex.id)).append(",\"f\":").append(f)
                .append(",\"issues\":[")
                .append((live + rep).joinToString(",") {
                    "{\"c\":" + esc(it.code) + ",\"l\":" + it.level + ",\"w\":" + it.weight + ",\"m\":" + esc(it.msg) + "}"
                })
                .append("]}")
        }
    }
    /* hipShoot exige o quadril subindo mais rápido que os ombros:
       um deslocamento vertical isolado, quadro a quadro. */
    val hs = FIXTURES.first { it.pattern == "squat" }
    val ctxH = RuleContext(Metrics(Kinematics.pose("squat", 0.0, PoseError(), hs.rep)), 0.0, "knee", true)
    for (f in 0 until 14) {
        val base = Kinematics.pose("squat", .5, PoseError(), hs.rep)
        val dHip = -0.12 * f
        val dSh = -0.02 * f
        val lm = base.mapIndexed { i, p ->
            when (i) {
                23, 24 -> Landmark(p.x, p.y + dHip, p.z, p.visibility)
                11, 12 -> Landmark(p.x, p.y + dSh, p.z, p.visibility)
                else -> p
            }
        }
        ctxH.m = Metrics(lm); ctxH.p = .5
        val live = ALL_RULES.filter { it in Rules.LIVE }.mapNotNull { Rules.live(it, ctxH) }
        out.append(",\n{\"ex\":\"hipShootCase\",\"f\":").append(f).append(",\"issues\":[")
            .append(live.joinToString(",") {
                "{\"c\":" + esc(it.code) + ",\"l\":" + it.level + ",\"w\":" + it.weight + ",\"m\":" + esc(it.msg) + "}"
            })
            .append("]}")
    }

    out.append("\n]\n")
    print(out)
}
