package com.treineai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ============================================================
   ÍCONES — os mesmos 42 traçados da versão web, desenhados a
   partir do mesmo `path` SVG. Nada foi redesenhado: a geometria
   é idêntica, só o renderizador mudou.

   Grade 24×24, traço 1,9, pontas e junções arredondadas.
   ============================================================ */

sealed interface IconPart {
    /** Traçado SVG. */
    data class P(val d: String, val filled: Boolean = false, val color: Long? = null) : IconPart
    data class C(val cx: Float, val cy: Float, val r: Float, val filled: Boolean = false) : IconPart
    data class R(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float) : IconPart
}

private typealias P = IconPart.P
private typealias C = IconPart.C
private typealias R = IconPart.R

val ICONS: Map<String, List<IconPart>> = mapOf(
    "home" to listOf(P("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", filled = false)),
    "dumbbell" to listOf(P("M6.5 6.5v11M3 9v6M17.5 6.5v11M21 9v6M6.5 12h11", filled = false)),
    "chart" to listOf(P("M4 20V10M10 20V4M16 20v-7M22 20H2", filled = false)),
    "trophy" to listOf(P("M7 4h10v5a5 5 0 0 1-10 0zM7 5H4v2a3 3 0 0 0 3 3M17 5h3v2a3 3 0 0 1-3 3M9 20h6M12 14v6", filled = false)),
    "user" to listOf(C(12.0f, 8.0f, 4.0f, filled = false), P("M4 21a8 8 0 0 1 16 0", filled = false)),
    "back" to listOf(P("M15 19 8 12l7-7", filled = false)),
    "close" to listOf(P("M18 6 6 18M6 6l12 12", filled = false)),
    "check" to listOf(P("M20 6 9 17l-5-5", filled = false)),
    "play" to listOf(P("M6 4l14 8-14 8z", filled = true)),
    "pause" to listOf(P("M8 5v14M16 5v14", filled = false)),
    "camera" to listOf(P("M3 8h3l2-3h8l2 3h3v11H3z", filled = false), C(12.0f, 13.0f, 4.0f, filled = false)),
    "flame" to listOf(P("M12 3s5 4 5 9a5 5 0 0 1-10 0c0-2 1-3 1-3s0 2 2 2 2-8 2-8z", filled = false)),
    "lock" to listOf(R(4.0f, 10.0f, 16.0f, 11.0f, 2.0f), P("M8 10V7a4 4 0 0 1 8 0v3", filled = false)),
    "bolt" to listOf(P("M13 2 4 14h7l-1 8 9-12h-7z", filled = false)),
    "star" to listOf(P("m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z", filled = false)),
    "alert" to listOf(P("M12 8v5M12 17h.01", filled = false), C(12.0f, 12.0f, 9.0f, filled = false)),
    "info" to listOf(C(12.0f, 12.0f, 9.0f, filled = false), P("M12 11v5M12 8h.01", filled = false)),
    "chevron" to listOf(P("m9 5 7 7-7 7", filled = false)),
    "settings" to listOf(P("M4 7h10M18 7h2M4 17h4M12 17h8", filled = false), C(16.0f, 7.0f, 2.2f, filled = false), C(10.0f, 17.0f, 2.2f, filled = false)),
    "search" to listOf(C(11.0f, 11.0f, 7.0f, filled = false), P("m20 20-3.5-3.5", filled = false)),
    "eye" to listOf(P("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z", filled = false), C(12.0f, 12.0f, 3.0f, filled = false)),
    "eyeoff" to listOf(P("M3 3l18 18M10.6 10.7a3 3 0 0 0 4.2 4.2M9.4 5.3A9.6 9.6 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4M6.2 6.3A16.6 16.6 0 0 0 2 12s3.5 7 10 7a9.9 9.9 0 0 0 3.5-.6", filled = false)),
    "up" to listOf(P("M12 19V5M5 12l7-7 7 7", filled = false)),
    "down" to listOf(P("M12 5v14M19 12l-7 7-7-7", filled = false)),
    "minus" to listOf(P("M5 12h14", filled = false)),
    "bell" to listOf(P("M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8", filled = false), P("M10.3 21a2 2 0 0 0 3.4 0", filled = false)),
    "shield" to listOf(P("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", filled = false)),
    "volume" to listOf(P("M11 5 6 9H3v6h3l5 4z", filled = false), P("M16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12", filled = false)),
    "vibrate" to listOf(R(8.0f, 4.0f, 8.0f, 16.0f, 2.0f), P("M4 9v6M20 9v6", filled = false)),
    "logout" to listOf(P("M14 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-3", filled = false), P("M9 12h11M17 9l3 3-3 3", filled = false)),
    "trash" to listOf(P("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3", filled = false)),
    "crown" to listOf(P("M3 7l4 4 5-7 5 7 4-4-2 12H5z", filled = false)),
    "target" to listOf(C(12.0f, 12.0f, 9.0f, filled = false), C(12.0f, 12.0f, 4.0f, filled = false), C(12.0f, 12.0f, 1.0f, filled = true)),
    "clock" to listOf(C(12.0f, 12.0f, 9.0f, filled = false), P("M12 7v5l3 2", filled = false)),
    "calendar" to listOf(R(3.0f, 5.0f, 18.0f, 16.0f, 2.0f), P("M8 3v4M16 3v4M3 10h18", filled = false)),
    "refresh" to listOf(P("M3 12a9 9 0 0 1 15-6.7L21 8", filled = false), P("M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16", filled = false), P("M3 21v-5h5", filled = false)),
    "filter" to listOf(P("M3 5h18l-7 8v6l-4 2v-8z", filled = false)),
    "grid" to listOf(R(3.0f, 3.0f, 7.0f, 7.0f, 1.5f), R(14.0f, 3.0f, 7.0f, 7.0f, 1.5f), R(3.0f, 14.0f, 7.0f, 7.0f, 1.5f), R(14.0f, 14.0f, 7.0f, 7.0f, 1.5f)),
    "skip" to listOf(P("M5 5l9 7-9 7zM19 5v14", filled = false)),
    "mic" to listOf(R(9.0f, 2.0f, 6.0f, 12.0f, 3.0f), P("M5 11a7 7 0 0 0 14 0M12 18v4M9 22h6", filled = false)),
    "micoff" to listOf(P("M3 3l18 18M9 5a3 3 0 0 1 6 0v5m-6 1v-1M5 11a7 7 0 0 0 10.5 6M19 11a6.9 6.9 0 0 1-.6 2.8M12 18v4M9 22h6", filled = false)),
    "google" to listOf(P("M21 12.2c0-.7-.1-1.3-.2-1.9H12v3.7h5.1a4.4 4.4 0 0 1-1.9 2.9v2.4h3.1c1.8-1.7 2.7-4.1 2.7-7.1z", filled = true, color = 0xFF4285F4), P("M12 21.5c2.5 0 4.6-.8 6.2-2.3l-3.1-2.4c-.8.6-1.9.9-3.1.9-2.4 0-4.4-1.6-5.1-3.8H3.7v2.4A9.3 9.3 0 0 0 12 21.5z", filled = true, color = 0xFF34A853), P("M6.9 13.9a5.6 5.6 0 0 1 0-3.6V7.9H3.7a9.3 9.3 0 0 0 0 8.4z", filled = true, color = 0xFFFBBC05), P("M12 6.4c1.4 0 2.6.5 3.5 1.4l2.7-2.7A9.2 9.2 0 0 0 12 2.5a9.3 9.3 0 0 0-8.3 5.4l3.2 2.4c.7-2.2 2.7-3.9 5.1-3.9z", filled = true, color = 0xFFEA4335)),
)

/* Os traçados são convertidos uma única vez e reaproveitados:
   reanalisar a string SVG a cada quadro custaria caro na tela de treino. */
private val pathCache = HashMap<String, androidx.compose.ui.graphics.Path>()

private fun pathOf(d: String): androidx.compose.ui.graphics.Path =
    pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** Desenha um ícone dentro da caixa atual, na cor pedida. */
fun DrawScope.drawIcon(name: String, tint: Color, boxSize: Float) {
    val parts = ICONS[name] ?: ICONS["info"] ?: return
    val k = boxSize / 24f
    /* dentro do scale a espessura já é multiplicada por k */
    val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    scale(k, k, pivot = Offset.Zero) {
        parts.forEach { part ->
            when (part) {
                is IconPart.P -> {
                    val path = pathOf(part.d)
                    val color = part.color?.let { Color(it) } ?: tint
                    drawPath(path, color, style = if (part.filled) Fill else stroke)
                }
                is IconPart.C ->
                    drawCircle(
                        tint, radius = part.r, center = Offset(part.cx, part.cy),
                        style = if (part.filled) Fill else stroke
                    )
                is IconPart.R ->
                    drawRoundRect(
                        tint,
                        topLeft = Offset(part.x, part.y),
                        size = Size(part.w, part.h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(part.rx, part.rx),
                        style = stroke
                    )
            }
        }
    }
}

/** Ícone como elemento de interface. */
@Composable
fun Icon(name: String, size: Dp = 20.dp, tint: Color = TA.cream, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val box = kotlin.math.min(this.size.width, this.size.height)
        translate((this.size.width - box) / 2f, (this.size.height - box) / 2f) {
            drawIcon(name, tint, box)
        }
    }
}
