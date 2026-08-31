package com.treineai.app.motion

import com.treineai.app.data.RepRange
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/* ============================================================
   CINEMÁTICA — modelo de corpo usado nas demonstrações animadas
   e no provider simulado. Porte direto da versão web: cada padrão
   percorre exatamente a amplitude declarada pelo exercício, de modo
   que a análise e a demonstração falem a mesma língua.
   ============================================================ */

const val DEG = PI / 180.0

fun clamp(v: Double, a: Double, b: Double): Double = if (v < a) a else if (v > b) b else v
fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

data class Pt(val x: Double, val y: Double)

data class Landmark(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val visibility: Double = 0.95
)

/** Erros injetados na simulação. */
data class PoseError(
    val lean: Double = 0.0,
    val asym: Double = 0.0,
    val shallow: Double = 0.0,
    val drift: Double = 0.0
)

/** Comprimentos dos segmentos, normalizados pela altura do quadro. */
object Seg {
    const val torso = .235
    const val neck = .052
    const val head = .048
    const val upper = .142
    const val fore = .132
    const val thigh = .205
    const val shin = .205
    const val foot = .072
    const val shW = .118
    const val hipW = .078
}

/** Ponto a `len` de `p`, na direção `deg` (0 = direita, 90 = para baixo). */
fun pAt(p: Pt, len: Double, deg: Double) = Pt(p.x + len * cos(deg * DEG), p.y + len * sin(deg * DEG))

fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

/** IK de dois elos: articulação entre root e end. side = ±1 */
fun ik2(root: Pt, end: Pt, l1: Double, l2: Double, side: Int): Pt {
    var d = dist(root, end)
    d = clamp(d, abs(l1 - l2) + 1e-4, l1 + l2 - 1e-4)
    val a = atan2(end.y - root.y, end.x - root.x)
    val c = clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1.0, 1.0)
    val t = a + side * acos(c)
    return Pt(root.x + l1 * cos(t), root.y + l1 * sin(t))
}

/** Ponto tal que o ângulo interno em `j` entre (j→A) e a saída seja `ang`. */
fun jp(j: Pt, dirA: Double, len: Double, ang: Double, side: Int) = pAt(j, len, dirA + side * ang)

class Rig(
    var face: String = "right",
    var head: Pt = Pt(0.5, 0.2),
    var nose: Pt? = null,
    var rsh: Pt = Pt(0.5, 0.3), var lsh: Pt = Pt(0.5, 0.3),
    var rel: Pt = Pt(0.5, 0.4), var lel: Pt = Pt(0.5, 0.4),
    var rwr: Pt = Pt(0.5, 0.5), var lwr: Pt = Pt(0.5, 0.5),
    var rhip: Pt = Pt(0.5, 0.55), var lhip: Pt = Pt(0.5, 0.55),
    var rkn: Pt = Pt(0.5, 0.75), var lkn: Pt = Pt(0.5, 0.75),
    var rank: Pt = Pt(0.5, 0.95), var lank: Pt = Pt(0.5, 0.95),
    var rtoe: Pt? = null, var ltoe: Pt? = null,
    var rheel: Pt? = null, var lheel: Pt? = null
) {
    fun map(f: (Pt) -> Pt): Rig = Rig(
        face, f(head), nose?.let(f),
        f(rsh), f(lsh), f(rel), f(lel), f(rwr), f(lwr),
        f(rhip), f(lhip), f(rkn), f(lkn), f(rank), f(lank),
        rtoe?.let(f), ltoe?.let(f), rheel?.let(f), lheel?.let(f)
    )

    fun shift(dx: Double, dy: Double): Rig = map { Pt(it.x + dx, it.y + dy) }

    /** Apoia o pé indicado na altura `y` (o chão). */
    fun plant(right: Boolean, y: Double): Rig {
        val ref = if (right) rank else lank
        return shift(0.0, y - ref.y)
    }
}

object Kinematics {

    /* Amplitude declarada pelo exercício; sem ela, valores padrão do padrão. */
    private fun t(cfg: RepRange?, p: Double, a: Double, b: Double): Double =
        lerp(cfg?.top ?: a, cfg?.bottom ?: b, clamp(p, 0.0, 1.0))

    private fun off(p: Pt) = Pt(p.x - .012, p.y - .004)

    private fun sideRig(
        sh: Pt, hip: Pt, kn: Pt, ank: Pt, el: Pt, wr: Pt, head: Pt,
        face: String = "right", nose: Pt? = null,
        el2: Pt? = null, wr2: Pt? = null, kn2: Pt? = null, ank2: Pt? = null,
        toe: Pt? = null, heel: Pt? = null, asym: Double = 0.0
    ): Rig = Rig(
        face = face, head = head, nose = nose,
        rsh = sh, lsh = off(sh),
        rel = el, lel = off(el2 ?: el),
        rwr = wr, lwr = off(wr2 ?: wr),
        rhip = hip, lhip = off(hip),
        rkn = kn, lkn = off(kn2 ?: Pt(kn.x, kn.y + asym * .02)),
        rank = ank, lank = off(ank2 ?: ank),
        rtoe = toe, ltoe = toe?.let { off(it) },
        rheel = heel, lheel = heel?.let { off(it) }
    )

    /* ---------------- vista frontal ---------------- */
    private fun stand(): Rig {
        val cx = .5
        val shY = .30
        val hipY = .545
        val rsh = Pt(cx + Seg.shW / 2, shY)
        val lsh = Pt(cx - Seg.shW / 2, shY)
        val rhip = Pt(cx + Seg.hipW / 2, hipY)
        val lhip = Pt(cx - Seg.hipW / 2, hipY)
        val rel = pAt(rsh, Seg.upper, 84.0)
        val lel = pAt(lsh, Seg.upper, 96.0)
        return Rig(
            face = "front",
            head = Pt(cx, shY - Seg.neck - Seg.head),
            nose = Pt(cx, shY - Seg.neck - Seg.head * .1),
            rsh = rsh, lsh = lsh, rhip = rhip, lhip = lhip, rel = rel, lel = lel,
            rwr = pAt(rel, Seg.fore, 88.0), lwr = pAt(lel, Seg.fore, 92.0),
            rkn = Pt(rhip.x + .004, hipY + Seg.thigh), lkn = Pt(lhip.x - .004, hipY + Seg.thigh),
            rank = Pt(rhip.x + .008, hipY + Seg.thigh + Seg.shin),
            lank = Pt(lhip.x - .008, hipY + Seg.thigh + Seg.shin),
            rtoe = Pt(rhip.x + .03, hipY + Seg.thigh + Seg.shin + .03),
            ltoe = Pt(lhip.x - .03, hipY + Seg.thigh + Seg.shin + .03)
        )
    }

    /** Dobra os braços mantendo o ângulo de cotovelo exato. */
    private fun arm(s: Rig, upperR: Double, e: Double, extraL: Double) {
        val el = pAt(s.rsh, Seg.upper, upperR)
        s.rel = el
        s.rwr = pAt(el, Seg.fore, upperR - (180 - e))
        val uL = 180 - upperR
        val eL = clamp(e + extraL, 12.0, 178.0)
        val el2 = pAt(s.lsh, Seg.upper, uL)
        s.lel = el2
        s.lwr = pAt(el2, Seg.fore, uL + (180 - eL))
    }

    /** Ângulo de ombro exato (abdução / flexão). */
    private fun shoulder(s: Rig, a: Double, extraL: Double) {
        val dirR = atan2(s.rhip.y - s.rsh.y, s.rhip.x - s.rsh.x) / DEG
        val dirL = atan2(s.lhip.y - s.lsh.y, s.lhip.x - s.lsh.x) / DEG
        val aR = dirR - a
        val aL = dirL + a + extraL
        s.rel = pAt(s.rsh, Seg.upper, aR); s.rwr = pAt(s.rel, Seg.fore, aR + 5)
        s.lel = pAt(s.lsh, Seg.upper, aL); s.lwr = pAt(s.lel, Seg.fore, aL - 5)
    }

    /* ---------------- padrões ---------------- */
    fun rig(pattern: String, pRaw: Double, e: PoseError = PoseError(), cfg: RepRange? = null): Rig {
        val p = clamp(pRaw, 0.0, 1.0)
        return when (pattern) {
            "squat", "legPress" -> {
                val q = clamp(p * (1 - e.shallow * .35), 0.0, 1.0)
                val k = t(cfg, q, 170.0, 90.0)
                val prog = clamp((170 - k) / 80, 0.0, 1.0)
                val hip = Pt(.44, .46)
                val thighDir = 90 + 22 * prog
                val kn = pAt(hip, Seg.thigh, thighDir)
                val ank = jp(kn, thighDir + 180, Seg.shin, k, -1)
                val lean = (34 + 26 * e.lean) * prog
                val sh = pAt(hip, Seg.torso, -90 + lean)
                val head = pAt(sh, Seg.neck + Seg.head, -90 + lean * .55)
                val el = pAt(sh, Seg.upper, 30 - 55 * prog)
                val wr = pAt(el, Seg.fore, 5 - 40 * prog)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    toe = pAt(ank, Seg.foot, 0.0), heel = pAt(ank, .03, 180.0),
                    kn2 = Pt(kn.x, kn.y + e.asym * .022), asym = e.asym
                ).plant(true, .855)
            }

            "lunge" -> {
                val q = clamp(p * (1 - e.shallow * .3), 0.0, 1.0)
                val k = t(cfg, q, 165.0, 92.0)
                val prog = clamp((165 - k) / 73, 0.0, 1.0)
                val hip = Pt(.44, .44)
                val thighDir = 90 - 84 * prog
                val kn = pAt(hip, Seg.thigh, thighDir)
                val ank = jp(kn, thighDir + 180, Seg.shin, k, -1)
                val dirB = 120 - 30 * prog
                val knB = pAt(hip, Seg.thigh, dirB)
                val ankB = jp(knB, dirB + 180, Seg.shin, k, -1)
                val lean = (8 + 22 * e.lean) * prog
                val sh = pAt(hip, Seg.torso, -90 + lean)
                val head = pAt(sh, Seg.neck + Seg.head, -90 + lean * .6)
                val el = pAt(sh, Seg.upper, 88.0)
                val wr = pAt(el, Seg.fore, 90.0)
                Rig(
                    face = "right", head = head,
                    rsh = sh, lsh = Pt(sh.x - .014, sh.y + .004),
                    rel = el, lel = Pt(el.x - .016, el.y),
                    rwr = wr, lwr = Pt(wr.x - .016, wr.y),
                    rhip = hip, lhip = Pt(hip.x - .014, hip.y + .004),
                    rkn = kn, lkn = knB, rank = ank, lank = ankB,
                    rtoe = pAt(ank, Seg.foot, 0.0), ltoe = pAt(ankB, Seg.foot, 0.0)
                ).plant(true, .855)
            }

            "hinge" -> hingeRig(p, e, cfg).plant(true, .855)

            "kickback" -> {
                val h = t(cfg, p, 118.0, 172.0)
                val hip = Pt(.42, .50)
                val dirSh = -18.0
                val sh = pAt(hip, Seg.torso, dirSh)
                val head = pAt(sh, Seg.neck + Seg.head, dirSh - 8)
                val dirKn = dirSh + h
                val kn = pAt(hip, Seg.thigh, dirKn)
                val ank = jp(kn, dirKn + 180, Seg.shin, 168.0, -1)
                val knS = pAt(hip, Seg.thigh, 94.0)
                val ankS = jp(knS, 274.0, Seg.shin, 174.0, -1)
                val el = pAt(sh, Seg.upper, 84.0)
                val wr = pAt(el, Seg.fore, 88.0)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    kn2 = knS, ank2 = ankS, toe = pAt(ank, Seg.foot, 0.0), asym = e.asym
                ).plant(false, .855)
            }

            "hipThrust", "gluteBridge" -> {
                val h = t(cfg, p, 96.0, 172.0)
                val prog = clamp((h - 96) / 76, 0.0, 1.0)
                val hip = Pt(.56, .78 - .10 * prog)
                val dirKn = 178.0
                val kn = pAt(hip, Seg.thigh, dirKn)
                val ank = jp(kn, dirKn + 180, Seg.shin, 92.0, 1)
                val dirSh = dirKn + h - 360
                val sh = pAt(hip, Seg.torso, dirSh)
                val head = pAt(sh, Seg.neck + Seg.head, dirSh - 26)
                val el = pAt(sh, Seg.upper, 120.0)
                val wr = pAt(el, Seg.fore, 150.0)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    face = "right", toe = pAt(ank, Seg.foot, 0.0), asym = e.asym
                ).plant(true, .855)
            }

            "pushup" -> {
                val ea = t(cfg, p, 165.0, 82.0)
                val prog = clamp((165 - ea) / 83, 0.0, 1.0)
                val d = sqrt(
                    Seg.upper * Seg.upper + Seg.fore * Seg.fore -
                        2 * Seg.upper * Seg.fore * cos(ea * DEG)
                )
                val hand = Pt(.74, .80)
                val sh = pAt(hand, d, -118 + 16 * prog)
                val el = ik2(sh, hand, Seg.upper, Seg.fore, 1)
                val sag = e.lean * .05 * prog
                val hip = Pt(sh.x - .245, sh.y + .052 + sag)
                val kn = Pt(hip.x - .19, hip.y + .035)
                val ank = Pt(kn.x - .185, kn.y + .04)
                val head = pAt(sh, Seg.neck + Seg.head, -12 + e.drift * 16)
                sideRig(
                    sh, hip, kn, ank, el, hand, head,
                    face = "right", toe = pAt(ank, Seg.foot, 200.0), asym = e.asym
                )
            }

            "plank" -> {
                val tt = sin(p * PI * 2) * .004
                val sh = Pt(.70, .66)
                val hand = Pt(.72, .78)
                val sag = e.lean * .05 + tt
                val hip = Pt(.45, .695 + sag)
                val kn = Pt(.28, .73 + sag * .5)
                val ank = Pt(.14, .77)
                val head = pAt(sh, Seg.neck + Seg.head, -10 + e.drift * 16)
                sideRig(
                    sh, hip, kn, ank, Pt(.71, .72), hand, head,
                    face = "right", toe = pAt(ank, Seg.foot, 200.0), asym = e.asym
                )
            }

            "mountain" -> {
                val h = t(cfg, p, 168.0, 108.0)
                val sh = Pt(.72, .60)
                val hand = Pt(.74, .80)
                val hip = Pt(.46, .68 + e.lean * .04)
                val dirSh = atan2(sh.y - hip.y, sh.x - hip.x) / DEG
                val dirKn = dirSh - h
                val kn = pAt(hip, Seg.thigh, dirKn)
                val ank = jp(kn, dirKn + 180, Seg.shin, 128.0, -1)
                val knB = pAt(hip, Seg.thigh, 168.0)
                val ankB = jp(knB, 348.0, Seg.shin, 172.0, -1)
                val head = pAt(sh, Seg.neck + Seg.head, -6.0)
                sideRig(
                    sh, hip, kn, ank, Pt(.73, .70), hand, head,
                    face = "right", kn2 = knB, ank2 = ankB,
                    toe = pAt(ank, Seg.foot, 200.0), asym = e.asym
                )
            }

            "benchPress" -> {
                val ea = t(cfg, p, 168.0, 78.0)
                val d = sqrt(
                    Seg.upper * Seg.upper + Seg.fore * Seg.fore -
                        2 * Seg.upper * Seg.fore * cos(ea * DEG)
                )
                val sh = Pt(.36, .615)
                val hip = Pt(.59, .625)
                val kn = Pt(.755, .70)
                val ank = Pt(.79, .845)
                val wr = pAt(sh, d, -86.0)
                val el = ik2(sh, wr, Seg.upper, Seg.fore, 1)
                val wr2 = Pt(wr.x - .02 - e.asym * .03, wr.y + e.asym * .045)
                val el2 = ik2(Pt(sh.x - .014, sh.y + .004), wr2, Seg.upper, Seg.fore, 1)
                sideRig(
                    sh, hip, kn, ank, el, wr, Pt(.295, .60),
                    face = "left", el2 = el2, wr2 = wr2,
                    toe = pAt(ank, Seg.foot, 160.0), asym = e.asym
                )
            }

            "tricepsExt" -> {
                val ea = t(cfg, p, 168.0, 62.0)
                val sh = Pt(.36, .615)
                val hip = Pt(.59, .625)
                val kn = Pt(.755, .70)
                val ank = Pt(.79, .845)
                val el = Pt(.345, .41 + e.drift * .04 * p)
                val dirElSh = atan2(sh.y - el.y, sh.x - el.x) / DEG
                val wr = pAt(el, Seg.fore, dirElSh - ea)
                sideRig(
                    sh, hip, kn, ank, el, wr, Pt(.295, .60),
                    face = "left", toe = pAt(ank, Seg.foot, 160.0), asym = e.asym
                )
            }

            "row" -> {
                val ea = t(cfg, p, 162.0, 68.0)
                val prog = clamp((162 - ea) / 94, 0.0, 1.0)
                /* o padrão "hinge" completo já apoia o pé no chão: sem isso
                   a remada ficava alguns centímetros acima do restante */
                val base = hingeRig(1.0, PoseError(lean = e.lean * .5), RepRange("hip", 170.0, 104.0))
                    .plant(true, .855)
                var sh = base.rsh
                if (e.lean != 0.0) sh = Pt(sh.x, sh.y - e.lean * .03 * prog)
                val upperDir = 94 - 62 * prog
                val el = pAt(sh, Seg.upper, upperDir)
                val wr = pAt(el, Seg.fore, upperDir + (180 - ea))
                base.rsh = sh; base.lsh = Pt(sh.x - .014, sh.y + .004)
                base.rel = el; base.lel = Pt(el.x - .014, el.y + .004)
                base.rwr = wr; base.lwr = Pt(wr.x - .014 - e.asym * .02, wr.y + .004)
                base
            }

            "tricepsPushdown" -> {
                val ea = t(cfg, p, 78.0, 172.0)
                val s = stand()
                val upperDir = 86 + e.drift * 20 * p
                val el = pAt(s.rsh, Seg.upper, upperDir)
                val wr = pAt(el, Seg.fore, upperDir - (180 - ea))
                sideRig(
                    s.rsh, s.rhip, s.rkn, s.rank, el, wr, s.head,
                    toe = pAt(s.rank, Seg.foot, 0.0), asym = e.asym
                )
            }

            "dip" -> {
                val ea = t(cfg, p, 168.0, 80.0)
                val d = sqrt(
                    Seg.upper * Seg.upper + Seg.fore * Seg.fore -
                        2 * Seg.upper * Seg.fore * cos(ea * DEG)
                )
                val hand = Pt(.66, .54)
                val sh = pAt(hand, d, -104.0)
                val el = ik2(sh, hand, Seg.upper, Seg.fore, -1)
                val hip = pAt(sh, Seg.torso, 86.0)
                val kn2 = pAt(hip, Seg.thigh, 104.0)
                val ank2 = jp(kn2, 284.0, Seg.shin, 158.0, -1)
                val head = pAt(sh, Seg.neck + Seg.head, -80.0)
                sideRig(
                    sh, hip, kn2, ank2, el, hand, head,
                    face = "right", toe = pAt(ank2, Seg.foot, 20.0), asym = e.asym
                )
            }

            "calfRaise" -> {
                val a = t(cfg, p, 104.0, 132.0)
                val toe = Pt(.58, .875)
                val ank = pAt(toe, Seg.foot, a - 88 + 180)
                val kn = pAt(ank, Seg.shin, -88.0)
                val hip = pAt(kn, Seg.thigh, -91.0)
                val sh = pAt(hip, Seg.torso, -90.0)
                val head = pAt(sh, Seg.neck + Seg.head, -89.0)
                val el = pAt(sh, Seg.upper, 88.0)
                val wr = pAt(el, Seg.fore, 90.0)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    toe = toe, heel = pAt(ank, .032, 178.0), asym = e.asym
                )
            }

            "crunch" -> {
                val a = t(cfg, p, 155.0, 122.0)
                val hip = Pt(.50, .79)
                val kn = Pt(.70, .71)
                val ank = jp(Pt(.70, .71), 158.0, Seg.shin, 118.0, -1)
                val dirKn = atan2(kn.y - hip.y, kn.x - hip.x) / DEG
                val dirSh = dirKn - a
                val sh = pAt(hip, Seg.torso, dirSh)
                val head = pAt(sh, Seg.neck + Seg.head, dirSh - 8 + e.drift * 20 * p)
                val el = pAt(sh, Seg.upper, dirSh + 26)
                val wr = pAt(head, .045, dirSh + 60)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    face = "left", toe = pAt(ank, Seg.foot, 200.0), asym = e.asym
                )
            }

            "legRaise" -> {
                val h = t(cfg, p, 168.0, 96.0)
                val hip = Pt(.62, .78)
                val sh = Pt(.86, .745)
                val dirSh = atan2(sh.y - hip.y, sh.x - hip.x) / DEG
                val dirKn = dirSh - h
                val kn = pAt(hip, Seg.thigh, dirKn)
                val ank = jp(kn, dirKn + 180, Seg.shin, 172.0, -1)
                val head = pAt(sh, Seg.neck + Seg.head, -6.0)
                sideRig(
                    sh, hip, kn, ank, Pt(.80, .80), Pt(.74, .82), head,
                    face = "left", toe = pAt(ank, Seg.foot, dirKn + 90), asym = e.asym
                )
            }

            "legExtension" -> {
                val k = t(cfg, p, 88.0, 172.0)
                val hip = Pt(.42, .60)
                val kn = Pt(.62, .615)
                val dirKnHip = atan2(hip.y - kn.y, hip.x - kn.x) / DEG
                val ank = pAt(kn, Seg.shin, dirKnHip - k)
                val sh = pAt(hip, Seg.torso, -92.0)
                val head = pAt(sh, Seg.neck + Seg.head, -88.0)
                val el = pAt(sh, Seg.upper, 84.0)
                val wr = pAt(el, Seg.fore, 56.0)
                sideRig(
                    sh, hip, kn, ank, el, wr, head,
                    toe = pAt(ank, Seg.foot, dirKnHip - k + 84), asym = e.asym
                )
            }

            "legCurl" -> {
                val k = t(cfg, p, 168.0, 52.0)
                val hip = Pt(.56, .72)
                val kn = Pt(.36, .735)
                val dirKnHip = atan2(hip.y - kn.y, hip.x - kn.x) / DEG
                val ank = pAt(kn, Seg.shin, dirKnHip + k)
                val sh = pAt(hip, Seg.torso, 4.0)
                val head = pAt(sh, Seg.neck + Seg.head, 2.0)
                sideRig(
                    sh, hip, kn, ank, Pt(.74, .77), Pt(.66, .79), head,
                    face = "left", toe = pAt(ank, Seg.foot, dirKnHip + k + 90), asym = e.asym
                )
            }

            "overheadPress" -> {
                val s = stand()
                val q = clamp(p * (1 - e.shallow * .3), 0.0, 1.0)
                val ea = t(cfg, q, 76.0, 168.0)
                val prog = clamp((ea - 76) / 92, 0.0, 1.0)
                arm(s, 26 - 106 * prog, ea, e.asym * -26)
                if (e.lean != 0.0) s.head = Pt(s.head.x, s.head.y + e.lean * .012 * prog)
                s
            }

            "pulldown" -> {
                val s = stand()
                val ea = t(cfg, p, 168.0, 62.0)
                val prog = clamp((168 - ea) / 106, 0.0, 1.0)
                arm(s, -66 + 96 * prog, ea, e.asym * -24)
                s
            }

            "pullup" -> {
                val s = stand()
                val ea = t(cfg, p, 170.0, 55.0)
                arm(s, -74 + 30 * clamp((170 - ea) / 115, 0.0, 1.0), ea, e.asym * -20)
                s.shift(.5 + .105 - s.rwr.x, .085 - s.rwr.y)
            }

            "facePull" -> {
                val s = stand()
                val ea = t(cfg, p, 160.0, 62.0)
                arm(s, -12 - 6 * clamp((160 - ea) / 98, 0.0, 1.0), ea, e.asym * -22)
                s
            }

            "curl" -> {
                val s = stand()
                val ea = t(cfg, p, 165.0, 46.0)
                val drift = e.drift * 14 * clamp((165 - ea) / 119, 0.0, 1.0)
                arm(s, 84 - drift, ea, e.asym * 30)
                if (e.lean != 0.0) {
                    val d = e.lean * .016 * sin(p * PI)
                    s.rsh = Pt(s.rsh.x, s.rsh.y - d); s.lsh = Pt(s.lsh.x, s.lsh.y - d)
                    s.head = Pt(s.head.x, s.head.y - d)
                    s.rel = Pt(s.rel.x, s.rel.y - d); s.lel = Pt(s.lel.x, s.lel.y - d)
                }
                s
            }

            "lateralRaise" -> {
                val s = stand()
                val a = t(cfg, p, 14.0, 92.0)
                shoulder(s, a, e.asym * 20)
                if (e.lean != 0.0) {
                    val d = e.lean * .018 * clamp((a - 14) / 78, 0.0, 1.0)
                    s.rsh = Pt(s.rsh.x, s.rsh.y - d); s.lsh = Pt(s.lsh.x, s.lsh.y - d)
                    s.head = Pt(s.head.x, s.head.y - d)
                }
                s
            }

            "fly" -> {
                val s = stand()
                shoulder(s, t(cfg, p, 96.0, 24.0), e.asym * 18)
                s
            }

            "hipAbd" -> {
                val s = stand()
                val a = t(cfg, p, 8.0, 40.0)
                val hipC = Pt((s.rhip.x + s.lhip.x) / 2, (s.rhip.y + s.lhip.y) / 2)
                val dx = Seg.thigh * sin(a / 2 * DEG)
                val dy = Seg.thigh * cos(a / 2 * DEG)
                s.rkn = Pt(hipC.x + dx, hipC.y + dy); s.lkn = Pt(hipC.x - dx, hipC.y + dy)
                s.rank = Pt(s.rkn.x + dx * .9, s.rkn.y + Seg.shin)
                s.lank = Pt(s.lkn.x - dx * .9, s.lkn.y + Seg.shin)
                s.rtoe = Pt(s.rank.x + .03, s.rank.y + .03)
                s.ltoe = Pt(s.lank.x - .03, s.lank.y + .03)
                s
            }

            "shrug" -> {
                val s = stand()
                val v = t(cfg, p, 8.0, 26.0)
                val earY = s.head.y
                val ratio = .9448 - v / 81.8
                val shY = earY + ratio * Seg.shW
                s.rsh = Pt(s.rsh.x, shY)
                s.lsh = Pt(s.lsh.x, earY + (ratio + e.asym * .06) * Seg.shW)
                s.rel = pAt(s.rsh, Seg.upper, 86.0); s.rwr = pAt(s.rel, Seg.fore, 89.0)
                s.lel = pAt(s.lsh, Seg.upper, 94.0); s.lwr = pAt(s.lel, Seg.fore, 91.0)
                s
            }

            "frontRaise" -> {
                val a = t(cfg, p, 12.0, 90.0)
                val st = stand()
                val sh = st.rsh
                val hip = st.rhip
                val dirHip = atan2(hip.y - sh.y, hip.x - sh.x) / DEG
                val ang = dirHip - a
                val el = pAt(sh, Seg.upper, ang)
                val wr = pAt(el, Seg.fore, ang + 4)
                val rig = sideRig(
                    sh, hip, st.rkn, st.rank, el, wr, st.head,
                    toe = pAt(st.rank, Seg.foot, 0.0), asym = e.asym
                )
                if (e.lean != 0.0) {
                    val d = e.lean * .015 * clamp((a - 12) / 78, 0.0, 1.0)
                    rig.rsh = Pt(rig.rsh.x, rig.rsh.y - d)
                    rig.lsh = Pt(rig.lsh.x, rig.lsh.y - d)
                    rig.head = Pt(rig.head.x, rig.head.y - d)
                }
                rig
            }

            else -> rig("squat", p, e, cfg)
        }
    }

    private fun hingeRig(p: Double, e: PoseError, cfg: RepRange?): Rig {
        val q = clamp(p * (1 - e.shallow * .3), 0.0, 1.0)
        val h = t(cfg, q, 170.0, 92.0)
        val prog = clamp((170 - h) / 78, 0.0, 1.0)
        val hip = Pt(.44, .50)
        val thighDir = 90.0
        val kn = pAt(hip, Seg.thigh, thighDir)
        val ank = jp(kn, thighDir + 180, Seg.shin, 166.0, -1)
        val dirSh = thighDir - h
        val sh = pAt(hip, Seg.torso, dirSh)
        val round = e.lean * 12 * prog
        val head = pAt(sh, Seg.neck + Seg.head, dirSh - 90 + round)
        val el = pAt(sh, Seg.upper, 90 + prog * 6)
        val wr = pAt(el, Seg.fore, 90 + prog * 4)
        return sideRig(
            sh, hip, kn, ank, el, wr, head,
            toe = pAt(ank, Seg.foot, 0.0), heel = pAt(ank, .03, 180.0), asym = e.asym
        )
    }

    /** Converte o rig nos 33 pontos no formato MediaPipe. */
    fun toLandmarks(r: Rig): List<Landmark> {
        val lm = arrayOfNulls<Landmark>(33)
        fun put(i: Int, p: Pt) { lm[i] = Landmark(p.x, p.y) }
        val nose = r.nose ?: pAt(r.head, Seg.head * .85, if (r.face == "left") 180.0 else 0.0)
        put(0, nose)
        put(1, pAt(nose, .014, 200.0)); put(2, pAt(nose, .02, 200.0)); put(3, pAt(nose, .026, 200.0))
        put(4, pAt(nose, .014, -20.0)); put(5, pAt(nose, .02, -20.0)); put(6, pAt(nose, .026, -20.0))
        put(7, Pt(r.head.x - Seg.head * .55, r.head.y))
        put(8, Pt(r.head.x + Seg.head * .55, r.head.y))
        put(9, pAt(nose, .02, 110.0)); put(10, pAt(nose, .02, 70.0))
        put(11, r.lsh); put(12, r.rsh)
        put(13, r.lel); put(14, r.rel)
        put(15, r.lwr); put(16, r.rwr)
        put(17, pAt(r.lwr, .03, 100.0)); put(18, pAt(r.rwr, .03, 100.0))
        put(19, pAt(r.lwr, .04, 95.0)); put(20, pAt(r.rwr, .04, 95.0))
        put(21, pAt(r.lwr, .025, 80.0)); put(22, pAt(r.rwr, .025, 80.0))
        put(23, r.lhip); put(24, r.rhip)
        put(25, r.lkn); put(26, r.rkn)
        put(27, r.lank); put(28, r.rank)
        val back = if (r.face == "left") 0.0 else 180.0
        val fwd = if (r.face == "left") 180.0 else 0.0
        put(29, r.lheel ?: pAt(r.lank, .03, back))
        put(30, r.rheel ?: pAt(r.rank, .03, back))
        put(31, r.ltoe ?: pAt(r.lank, Seg.foot, fwd))
        put(32, r.rtoe ?: pAt(r.rank, Seg.foot, fwd))
        return lm.map { it ?: Landmark(0.0, 0.0, 0.0, 0.0) }
    }

    fun pose(pattern: String, p: Double, e: PoseError = PoseError(), cfg: RepRange? = null): List<Landmark> =
        toLandmarks(rig(pattern, p, e, cfg))

    /** Ossos desenhados no esqueleto. */
    val BONES = listOf(
        11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27,
        24 to 26, 26 to 28, 27 to 31, 28 to 32, 0 to 11, 0 to 12
    )
    val KEYPOINTS = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
}
