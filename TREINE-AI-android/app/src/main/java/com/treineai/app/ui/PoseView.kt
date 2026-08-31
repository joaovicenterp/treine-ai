package com.treineai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.treineai.app.data.RepRange
import com.treineai.app.motion.Kinematics
import com.treineai.app.motion.Landmark
import com.treineai.app.motion.PoseError
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/* ============================================================
   ESQUELETO E DEMONSTRAÇÕES
   O mesmo desenho da versão web: ossos em creme, articulações
   marcadas, silhueta suave por trás nas demonstrações.
   ============================================================ */

enum class FitMode { Cover, Contain }

/** Converte um ponto normalizado (0–1) em coordenada de tela. */
private fun mapper(
    canvasW: Float, canvasH: Float, srcAspect: Float, mode: FitMode, mirror: Boolean
): (Landmark) -> Offset {
    val cAspect = canvasW / canvasH
    val dw: Float
    val dh: Float
    if (mode == FitMode.Cover) {
        if (srcAspect > cAspect) { dh = canvasH; dw = canvasH * srcAspect }
        else { dw = canvasW; dh = canvasW / srcAspect }
    } else {
        if (srcAspect > cAspect) { dw = canvasW; dh = canvasW / srcAspect }
        else { dh = canvasH; dw = canvasH * srcAspect }
    }
    val ox = (canvasW - dw) / 2f
    val oy = (canvasH - dh) / 2f
    return { p ->
        val x = if (mirror) 1.0 - p.x else p.x
        Offset(ox + (x * dw).toFloat(), oy + (p.y * dh).toFloat())
    }
}

fun DrawScope.drawSkeleton(
    lm: List<Landmark>?,
    srcAspect: Float = 1f,
    mode: FitMode = FitMode.Contain,
    mirror: Boolean = false,
    silhouette: Boolean = false,
    dim: Boolean = false,
    hot: List<Int> = emptyList()
) {
    if (lm == null) return
    val map = mapper(size.width, size.height, srcAspect, mode, mirror)
    val k = min(size.width, size.height) / 420f

    /* silhueta larga e translúcida por trás, como no modo demonstração */
    if (silhouette) {
        Kinematics.BONES.forEach { (a, b) ->
            val p1 = lm.getOrNull(a) ?: return@forEach
            val p2 = lm.getOrNull(b) ?: return@forEach
            drawLine(TA.cream.copy(alpha = .10f), map(p1), map(p2), 34f * k, StrokeCap.Round)
        }
    }

    val boneColor = if (dim) TA.cream.copy(alpha = .45f) else TA.cream
    Kinematics.BONES.forEach { (a, b) ->
        val p1 = lm.getOrNull(a) ?: return@forEach
        val p2 = lm.getOrNull(b) ?: return@forEach
        drawLine(boneColor, map(p1), map(p2), 3.2f * k, StrokeCap.Round)
    }

    /* articulações: as destacadas ganham a cor da marca */
    Kinematics.KEYPOINTS.forEach { i ->
        val p = lm.getOrNull(i) ?: return@forEach
        val c = map(p)
        val isHot = i in hot
        drawCircle(if (isHot) TA.flame else TA.ink0, radius = (if (isHot) 7.5f else 5f) * k, center = c)
        drawCircle(
            if (isHot) TA.flame else TA.cream, radius = (if (isHot) 7.5f else 5f) * k, center = c,
            style = Stroke(width = 2.4f * k)
        )
    }

    /* cabeça */
    val nose = lm.getOrNull(0)
    val earL = lm.getOrNull(7)
    val earR = lm.getOrNull(8)
    if (nose != null && earL != null && earR != null) {
        val h = map(nose)
        val r = abs(map(earL).x - map(earR).x) * .72f + 8f * k
        drawCircle(TA.cream, radius = max(9f * k, r), center = h, style = Stroke(width = 3f * k))
    }
}

/** Fundo de estúdio sintético, usado quando não há imagem de câmera. */
fun DrawScope.drawStudio() {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF191412), .55f to Color(0xFF0E0C0B), 1f to Color(0xFF080706)
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(TA.flame.copy(alpha = .10f), Color.Transparent),
            center = Offset(size.width * .5f, size.height * .45f),
            radius = size.height * .7f
        )
    )
    for (i in 1 until 8) {
        val y = size.height * (.55f + i * .06f)
        drawLine(TA.cream.copy(alpha = .045f), Offset(0f, y), Offset(size.width, y), 1f)
    }
    drawRect(
        TA.ink0.copy(alpha = .55f),
        topLeft = Offset(0f, size.height * .86f),
        size = Size(size.width, size.height * .14f)
    )
}

private fun ease(t: Float): Float =
    if (t < .5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/**
 * Demonstração animada de um exercício: o mesmo ciclo de 2,6 s
 * da versão web, alimentado pela mesma cinemática.
 */
@Composable
fun ExerciseDemo(
    pattern: String,
    cfg: RepRange? = null,
    modifier: Modifier = Modifier,
    periodMs: Int = 2600
) {
    val transition = rememberInfiniteTransition(label = "demo")
    val u by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ciclo"
    )
    Canvas(modifier) {
        /* triângulo 0→1→0, suavizado — idêntico ao `demo()` da web */
        val p = if (u < .5f) u * 2 else 2 - u * 2
        val lm = Kinematics.pose(pattern, ease(p).toDouble(), PoseError(), cfg)
        drawSkeleton(lm, srcAspect = 1f, mode = FitMode.Contain, silhouette = true)
    }
}

/** Pose estática, para miniaturas da biblioteca. */
@Composable
fun PoseThumb(pattern: String, cfg: RepRange? = null, p: Double = .35, modifier: Modifier = Modifier) {
    val lm = remember(pattern, p) { Kinematics.pose(pattern, p, PoseError(), cfg) }
    Canvas(modifier) { drawSkeleton(lm, silhouette = true, dim = true) }
}
