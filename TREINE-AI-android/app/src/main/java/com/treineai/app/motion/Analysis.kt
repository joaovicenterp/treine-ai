package com.treineai.app.motion

import com.treineai.app.data.Exercise
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* ============================================================
   MÉTRICAS ARTICULARES
   Ângulos e derivados calculados a partir dos 33 pontos.
   ============================================================ */

fun ang3(a: Landmark, b: Landmark, c: Landmark): Double {
    val v1x = a.x - b.x; val v1y = a.y - b.y
    val v2x = c.x - b.x; val v2y = c.y - b.y
    val den = (hypot(v1x, v1y) * hypot(v2x, v2y)).let { if (it == 0.0) 1e-6 else it }
    return acos(clamp((v1x * v2x + v1y * v2y) / den, -1.0, 1.0)) / DEG
}

/** Inclinação do segmento a→b em relação à vertical. */
fun vertAngle(a: Landmark, b: Landmark): Double = abs(atan2(b.x - a.x, -(b.y - a.y)) / DEG)

data class BBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    val w get() = x1 - x0
    val h get() = y1 - y0
    val cx get() = (x0 + x1) / 2
}

fun bboxOf(lm: List<Landmark>, points: List<Int>, clampToFrame: Boolean = false): BBox {
    var x0 = 1.0; var y0 = 1.0; var x1 = 0.0; var y1 = 0.0
    val cl = { v: Double -> if (clampToFrame) clamp(v, -.05, 1.05) else v }
    for (i in points) {
        val p = lm.getOrNull(i) ?: continue
        val x = cl(p.x); val y = cl(p.y)
        x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
    }
    return BBox(x0, y0, x1, y1)
}

class Metrics(private val lm: List<Landmark>) {
    private fun g(i: Int) = lm[i]

    val kneeR = ang3(g(24), g(26), g(28))
    val kneeL = ang3(g(23), g(25), g(27))
    val elbowR = ang3(g(12), g(14), g(16))
    val elbowL = ang3(g(11), g(13), g(15))
    val hipR = ang3(g(12), g(24), g(26))
    val hipL = ang3(g(11), g(23), g(25))
    val shoulderR = ang3(g(24), g(12), g(14))
    val shoulderL = ang3(g(23), g(11), g(13))
    val ankleR = ang3(g(26), g(28), g(32))
    val ankleL = ang3(g(25), g(27), g(31))

    val shC = Landmark((g(11).x + g(12).x) / 2, (g(11).y + g(12).y) / 2)
    val hipC = Landmark((g(23).x + g(24).x) / 2, (g(23).y + g(24).y) / 2)
    val knC = Landmark((g(25).x + g(26).x) / 2, (g(25).y + g(26).y) / 2)
    val ankC = Landmark((g(27).x + g(28).x) / 2, (g(27).y + g(28).y) / 2)

    val torsoLean = vertAngle(hipC, shC)
    val trunk = ang3(shC, hipC, knC)
    val hip = (hipR + hipL) / 2
    val knee = (kneeR + kneeL) / 2
    val elbow = (elbowR + elbowL) / 2
    val shoulder = (shoulderR + shoulderL) / 2
    val ankle = (ankleR + ankleL) / 2

    private val shWid = abs(g(12).x - g(11).x).let { if (it < 1e-4) .118 else it }

    /** Encolhimento normalizado pela largura dos ombros (invariante à escala). */
    val shrug = (0.9448 - (shC.y - (g(7).y + g(8).y) / 2) / shWid) * 81.8

    val hipAbd = ang3(g(25), hipC, g(26))
    val headFwd = (g(0).x - shC.x) / abs(shC.y - hipC.y).let { if (it < 1e-4) .2 else it }

    /** Deslocamento horizontal médio dos cotovelos em relação aos ombros. */
    val elbowOff = ((g(13).x + g(14).x) / 2 - shC.x) / shWid

    /** Desalinhamento do quadril na linha ombro→tornozelo (prancha, flexão). */
    val hipDev: Double = run {
        val dx = ankC.x - shC.x
        val dy = ankC.y - shC.y
        val den = (dx * dx + dy * dy).let { if (it < 1e-6) 1e-6 else it }
        val t = ((hipC.x - shC.x) * dx + (hipC.y - shC.y) * dy) / den
        val px = shC.x + t * dx
        val py = shC.y + t * dy
        val len = hypot(dx, dy).let { if (it < 1e-4) .5 else it }
        (hipC.y - py) / len
    }

    /** Valgo: desvio do joelho para dentro da linha quadril→tornozelo. */
    private fun valg(hipA: Landmark, knA: Landmark, ankA: Landmark, sgn: Int): Double {
        val den = (ankA.y - hipA.y).let { if (abs(it) < 1e-6) 1e-6 else it }
        val tt = (knA.y - hipA.y) / den
        val lx = hipA.x + tt * (ankA.x - hipA.x)
        val scale = abs(hipA.y - ankA.y).let { if (it < 1e-4) .3 else it }
        return sgn * (lx - knA.x) / scale
    }

    val valgusR = valg(g(24), g(26), g(28), 1)
    val valgusL = valg(g(23), g(25), g(27), -1)
    val valgus = max(valgusR, valgusL)

    val vis = lm.sumOf { it.visibility } / lm.size
}

/* ============================================================
   ARTICULAÇÃO MOTORA
   ============================================================ */
object Joints {
    fun value(m: Metrics, joint: String): Double = when (joint) {
        "knee" -> m.knee
        "elbow" -> m.elbow
        "hip" -> m.hip
        "shoulder" -> m.shoulder
        "ankle" -> m.ankle
        "trunk" -> m.trunk
        "shrug" -> m.shrug
        "hipAbd" -> m.hipAbd
        "hipMin" -> min(m.hipL, m.hipR)
        "hipMax" -> max(m.hipL, m.hipR)
        "kneeMin" -> min(m.kneeL, m.kneeR)
        "kneeMax" -> max(m.kneeL, m.kneeR)
        else -> 0.0
    }
}

/* ============================================================
   VERIFICAÇÃO DE POSICIONAMENTO
   Confere só as articulações que ESTE exercício usa e devolve
   uma instrução curta, pensada para ser ouvida.
   ============================================================ */

private val P_TORSO = listOf(11, 12, 23, 24)
private val P_LEGS = listOf(25, 26, 27, 28)
private val P_KNEES = listOf(25, 26)
private val P_ARMS = listOf(13, 14, 15, 16)

fun requiredPoints(ex: Exercise?): List<Int> {
    if (ex == null) return P_TORSO + P_KNEES
    if (ex.hold) return P_TORSO + P_KNEES + listOf(13, 14)
    return when (ex.rep.joint) {
        "knee", "kneeMin", "kneeMax", "ankle", "hipAbd" -> P_TORSO + P_LEGS
        "hip", "hipMin", "hipMax", "trunk" -> P_TORSO + P_KNEES
        "elbow", "shoulder", "shrug" -> P_TORSO + P_ARMS
        else -> P_TORSO + P_KNEES
    }
}

val SETUP_MSG = mapOf(
    "noBody" to "Não estou te vendo. Fique de frente para a câmera.",
    "dark" to "Está muito escuro. Acenda uma luz.",
    "tooFar" to "Você está longe demais. Aproxime-se um pouco.",
    "tooClose" to "Você está perto demais. Afaste-se um pouco.",
    "cutBottom" to "Afaste-se: preciso ver suas pernas inteiras.",
    "cutTop" to "Abaixe o celular ou afaste-se: sua cabeça está cortada.",
    "notVisible" to "Vire o corpo para a câmera. Preciso ver você inteiro.",
    "moveLeft" to "Ande um pouco para a esquerda.",
    "moveRight" to "Ande um pouco para a direita.",
    "ready" to "Posição perfeita."
)

data class SetupState(
    val body: Boolean = false,
    val light: Boolean = true,
    val distance: Boolean = false,
    val full: Boolean = false,
    val framing: Boolean = false,
    val ready: Boolean = false,
    val hint: String = "noBody",
    val size: Double = 0.0,
    val bbox: BBox? = null
) {
    val msg: String get() = SETUP_MSG[hint] ?: SETUP_MSG["noBody"]!!
}

fun checkSetup(lm: List<Landmark>?, brightness: Double?, ex: Exercise?): SetupState {
    val light = brightness == null || brightness > .12
    if (lm == null || lm.size < 33) {
        return SetupState(light = light, hint = if (light) "noBody" else "dark")
    }
    val need = requiredPoints(ex)
    fun vis(i: Int) = lm.getOrNull(i)?.visibility ?: 0.0
    fun inFrame(i: Int): Boolean {
        val p = lm.getOrNull(i) ?: return false
        return p.x > -.06 && p.x < 1.06 && p.y > -.06 && p.y < 1.06
    }

    val torsoOk = P_TORSO.all { vis(it) > .2 && inFrame(it) }
    if (!torsoOk) return SetupState(body = false, light = light, hint = if (light) "noBody" else "dark")

    val b = bboxOf(lm, need)
    val full = bboxOf(lm, Kinematics.KEYPOINTS, clampToFrame = true)
    val size = max(full.w, full.h)

    /* "Longe demais" mede o tamanho aparente do corpo. "Perto demais" não:
       se tudo o que o exercício precisa está no quadro, preencher a tela
       não é problema. */
    val distance = size >= .35
    val missing = need.filter { !(vis(it) > .15 && inFrame(it)) }
    val isFull = missing.isEmpty()
    val framing = b.cx > .18 && b.cx < .82

    val hint = when {
        !light -> "dark"
        !distance -> "tooFar"
        !isFull -> {
            val belowOnly = missing.all { (lm.getOrNull(it)?.y ?: 0.0) >= 1.0 }
            val aboveOnly = missing.all { (lm.getOrNull(it)?.y ?: 1.0) <= 0.0 }
            when {
                size > .9 && !belowOnly && !aboveOnly -> "tooClose"
                belowOnly -> "cutBottom"
                aboveOnly -> "cutTop"
                else -> "notVisible"
            }
        }
        !framing -> if (b.cx <= .18) "moveRight" else "moveLeft"
        else -> "ready"
    }

    return SetupState(
        body = true, light = light, distance = distance, full = isFull, framing = framing,
        ready = light && distance && isFull && framing,
        hint = hint, size = size, bbox = b
    )
}

/* ============================================================
   REGRAS DE EXECUÇÃO
   nível 1 visual · 2 visual+som · 3 +vibração · 4 pausa a contagem
   ============================================================ */

data class Issue(val code: String, val level: Int, val msg: String, val weight: Int)

data class RepData(
    val index: Int,
    val valid: Boolean,
    val depth: Double,
    val tDown: Double,
    val tUp: Double,
    val restP: Double,
    val score: Int,
    val issues: List<String>
)

/** Mantido vivo entre quadros: as regras de estabilidade precisam de histórico. */
class RuleContext(
    var m: Metrics,
    var p: Double = 0.0,
    var joint: String = "knee",
    var down: Boolean = true
) {
    private val hist = HashMap<String, ArrayDeque<Double>>()
    private val rates = HashMap<String, Double>()

    /** Desvio padrão recente de um sinal — mede instabilidade. */
    fun stab(key: String, v: Double): Double {
        val h = hist.getOrPut(key) { ArrayDeque() }
        h.addLast(v)
        if (h.size > 26) h.removeFirst()
        if (h.size < 10) return 0.0
        val mean = h.sum() / h.size
        return sqrt(h.sumOf { (it - mean) * (it - mean) } / h.size)
    }

    fun rate(key: String, v: Double): Double {
        val prev = rates[key]
        rates[key] = v
        return if (prev == null) 0.0 else v - prev
    }

    fun reset() { hist.clear(); rates.clear() }
}

object Rules {
    val LIVE = setOf(
        "symmetry", "torsoLean", "backNeutral", "kneeValgus", "hipSag",
        "headPos", "torsoStable", "momentum", "elbowDrift", "hipShoot",
        "kneeLock", "hipStable", "shoulderDepth"
    )
    val REP = setOf("depth", "rom", "tempo", "lockout", "hipLockout")

    fun live(name: String, c: RuleContext): Issue? = when (name) {
        "symmetry" -> {
            val pair = when (c.joint) {
                "knee" -> c.m.kneeL to c.m.kneeR
                "elbow" -> c.m.elbowL to c.m.elbowR
                "shoulder" -> c.m.shoulderL to c.m.shoulderR
                "hip" -> c.m.hipL to c.m.hipR
                else -> null
            }
            if (pair == null) null else {
                val d = abs(pair.first - pair.second)
                when {
                    d > 22 -> Issue("symmetry", 2, "Equilibre os lados.", 16)
                    d > 14 -> Issue("symmetry", 1, "Lados desalinhados.", 8)
                    else -> null
                }
            }
        }
        "torsoLean" -> {
            val v = c.m.torsoLean
            when {
                v > 62 -> Issue("torsoLean", 3, "Peito mais alto.", 22)
                v > 50 -> Issue("torsoLean", 2, "Tronco caindo.", 12)
                else -> null
            }
        }
        "backNeutral" -> {
            val r = c.m.headFwd
            when {
                r > .52 -> Issue("backNeutral", 4, "Pare e ajuste sua posição.", 32)
                r > .34 -> Issue("backNeutral", 3, "Costas retas.", 20)
                else -> null
            }
        }
        "kneeValgus" -> {
            val v = c.m.valgus
            when {
                v > .16 -> Issue("kneeValgus", 3, "Joelho para fora.", 20)
                v > .09 -> Issue("kneeValgus", 2, "Alinhe o joelho.", 11)
                else -> null
            }
        }
        "hipSag" -> {
            val v = c.m.hipDev
            when {
                v > .085 -> Issue("hipSag", 3, "Quadril na linha.", 20)
                v < -.075 -> Issue("hipSag", 2, "Quadril alto demais.", 12)
                else -> null
            }
        }
        "headPos" -> if (c.m.headFwd > .42) Issue("headPos", 1, "Pescoço neutro.", 7) else null
        "torsoStable" -> {
            val s = c.stab("torsoLean", c.m.torsoLean)
            when {
                s > 9 -> Issue("torsoStable", 2, "Tronco firme.", 14)
                s > 6 -> Issue("torsoStable", 1, "Sem balanço.", 7)
                else -> null
            }
        }
        "momentum" -> {
            val s = c.stab("shY", c.m.shC.y * 100)
            if (s > 2.4) Issue("momentum", 2, "Sem impulso.", 15) else null
        }
        "elbowDrift" -> {
            /* o cotovelo deve ficar parado em relação ao ombro:
               medimos a variação, não a posição absoluta */
            val s = c.stab("elbowOff", c.m.elbowOff * 100)
            when {
                s > 12 -> Issue("elbowDrift", 2, "Cotovelo fixo.", 13)
                s > 8 -> Issue("elbowDrift", 1, "Cotovelo se movendo.", 7)
                else -> null
            }
        }
        "hipShoot" -> {
            val r = c.rate("hipY", c.m.hipC.y)
            val s = c.rate("shY2", c.m.shC.y)
            if (r < -.09 && s > r * .45) Issue("hipShoot", 3, "Suba junto.", 18) else null
        }
        "kneeLock" -> {
            val s = c.stab("knee", c.m.knee)
            if (s > 12) Issue("kneeLock", 2, "Joelhos fixos.", 12) else null
        }
        "hipStable" -> if (c.m.hipDev < -.08) Issue("hipStable", 2, "Quadril no banco.", 12) else null
        "shoulderDepth" -> if (c.p > 1.22) Issue("shoulderDepth", 4, "Pare e ajuste sua posição.", 26) else null
        else -> null
    }

    fun rep(name: String, c: RuleContext, depth: Double, tDown: Double, tUp: Double, restP: Double): Issue? =
        when (name) {
            "depth" -> if (depth < .82)
                Issue("depth", 2, if (c.down) "Desça mais." else "Amplitude maior.", ((0.82 - depth) * 90).roundToInt())
            else null
            "rom" -> if (depth < .8)
                Issue("rom", 2, "Amplitude completa.", ((0.8 - depth) * 85).roundToInt()) else null
            "tempo" -> when {
                tDown < .55 -> Issue("tempo", 2, "Controle a descida.", 14)
                tUp < .35 -> Issue("tempo", 1, "Não acelere.", 8)
                else -> null
            }
            "lockout" -> if (restP > .18) Issue("lockout", 1, "Estenda completamente.", 9) else null
            "hipLockout" -> if (depth < .86) Issue("hipLockout", 2, "Suba mais.", 15) else null
            else -> null
        }
}

val POSITIVES = listOf(
    "Boa execução.", "Excelente.", "Isso. Continue.", "Movimento limpo.", "Ótimo controle."
)

val ERROR_LABEL = mapOf(
    "depth" to "amplitude insuficiente", "rom" to "amplitude incompleta", "tempo" to "descida acelerada",
    "symmetry" to "desequilíbrio entre os lados", "torsoLean" to "tronco inclinando",
    "backNeutral" to "coluna perdendo a neutralidade", "kneeValgus" to "joelho desalinhado",
    "hipSag" to "quadril fora da linha", "headPos" to "pescoço projetado",
    "torsoStable" to "balanço de tronco", "momentum" to "uso de impulso",
    "elbowDrift" to "cotovelo se deslocando", "hipShoot" to "quadril subindo antes",
    "lockout" to "extensão incompleta", "hipLockout" to "extensão de quadril incompleta",
    "kneeLock" to "joelhos variando", "hipStable" to "quadril saindo do apoio",
    "shoulderDepth" to "amplitude além do recomendado"
)

val ERROR_TIP = mapOf(
    "depth" to "Aumente a amplitude — desça até a profundidade recomendada.",
    "rom" to "Complete a amplitude nas duas pontas do movimento.",
    "tempo" to "Leve cerca de 2 segundos na fase excêntrica.",
    "symmetry" to "Foque em subir e descer os dois lados juntos.",
    "torsoLean" to "Mantenha o peito mais alto durante a descida.",
    "backNeutral" to "Mantenha a coluna neutra. Considere reduzir a carga.",
    "kneeValgus" to "Empurre os joelhos na direção das pontas dos pés.",
    "hipSag" to "Contraia abdômen e glúteos para alinhar o quadril.",
    "headPos" to "Mantenha o pescoço alinhado com a coluna.",
    "torsoStable" to "Fixe o tronco: o movimento deve vir da articulação alvo.",
    "momentum" to "Reduza a carga e elimine o impulso.",
    "elbowDrift" to "Mantenha o cotovelo na mesma posição do início ao fim.",
    "hipShoot" to "Suba quadril e peito ao mesmo tempo.",
    "lockout" to "Estenda completamente antes de iniciar a próxima repetição.",
    "hipLockout" to "Suba até a extensão completa do quadril.",
    "kneeLock" to "Mantenha o ângulo do joelho constante.",
    "hipStable" to "Mantenha o quadril apoiado durante todo o movimento.",
    "shoulderDepth" to "Reduza a amplitude e pare se sentir desconforto."
)

data class ExerciseSummary(
    val reps: Int,
    val validReps: Int,
    val invalid: Int,
    val duration: Int,
    val holdSeconds: Int,
    val score: Int,
    val best: Int,
    val worst: Int,
    val avgDepth: Double,
    val avgTempo: Double,
    val errors: Map<String, Int>,
    val mainError: String?,
    val repDetail: List<RepData>
)
