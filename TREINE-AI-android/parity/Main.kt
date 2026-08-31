package com.treineai.parity

import com.treineai.app.motion.*
import kotlin.math.roundToInt

/* ============================================================
   Arnês de paridade: percorre os 50 exercícios em 21 fases e
   imprime, em JSON, tudo o que a versão web também imprime.
   As duas saídas são comparadas número a número.
   ============================================================ */

private fun r4(v: Double): Double = (v * 10000).roundToInt() / 10000.0
private fun esc(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun poseErrFor(step: Int): PoseError = when (step % 4) {
    0 -> PoseError()
    1 -> PoseError(lean = .6)
    2 -> PoseError(asym = .5, drift = .4)
    else -> PoseError(shallow = .5, lean = .2)
}

fun main() {
    val out = StringBuilder()
    out.append("[\n")
    var first = true

    for (ex in FIXTURES) {
        for (i in 0..20) {
            val p = i / 20.0
            val e = poseErrFor(i)
            val lm = Kinematics.pose(ex.pattern, p, e, ex.rep)
            val m = Metrics(lm)
            val jointVal = Joints.value(m, ex.rep.joint)
            val setup = checkSetup(lm, 0.5, ex)
            val full = bboxOf(lm, Kinematics.KEYPOINTS, clampToFrame = true)

            val ctx = RuleContext(m, p, ex.rep.joint, ex.rep.bottom < ex.rep.top)
            val issues = ex.checks.filter { it in Rules.LIVE }.mapNotNull { Rules.live(it, ctx) }
            val repIssues = ex.checks.filter { it in Rules.REP }
                .mapNotNull { Rules.rep(it, ctx, 0.7, 1.4, 0.9, 0.08) }

            if (!first) out.append(",\n")
            first = false
            out.append("{")
            out.append("\"ex\":").append(esc(ex.id)).append(",")
            out.append("\"p\":").append(r4(p)).append(",")
            out.append("\"joint\":").append(r4(jointVal)).append(",")
            out.append("\"knee\":").append(r4(m.knee)).append(",")
            out.append("\"elbow\":").append(r4(m.elbow)).append(",")
            out.append("\"hip\":").append(r4(m.hip)).append(",")
            out.append("\"shoulder\":").append(r4(m.shoulder)).append(",")
            out.append("\"ankle\":").append(r4(m.ankle)).append(",")
            out.append("\"trunk\":").append(r4(m.trunk)).append(",")
            out.append("\"torsoLean\":").append(r4(m.torsoLean)).append(",")
            out.append("\"shrug\":").append(r4(m.shrug)).append(",")
            out.append("\"hipAbd\":").append(r4(m.hipAbd)).append(",")
            out.append("\"headFwd\":").append(r4(m.headFwd)).append(",")
            out.append("\"elbowOff\":").append(r4(m.elbowOff)).append(",")
            out.append("\"hipDev\":").append(r4(m.hipDev)).append(",")
            out.append("\"valgus\":").append(r4(m.valgus)).append(",")
            out.append("\"size\":").append(r4(full.let { if (it.w > it.h) it.w else it.h })).append(",")
            out.append("\"hint\":").append(esc(setup.hint)).append(",")
            out.append("\"ready\":").append(setup.ready).append(",")
            out.append("\"live\":[").append(issues.joinToString(",") { esc(it.code) }).append("],")
            out.append("\"rep\":[").append(repIssues.joinToString(",") { esc(it.code) }).append("]")
            out.append("}")
        }
    }
    out.append("\n]\n")
    print(out)
}
