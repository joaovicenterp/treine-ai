package com.treineai.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/* ============================================================
   COMPONENTES — a mesma linguagem visual da versão web:
   botões chama, cartões de superfície, chips, anéis de pontuação
   e gráficos de linha com o ponto final destacado.
   ============================================================ */

enum class BtnKind { Primary, Ghost, Soft, Danger }

@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    icon: String? = null,
    enabled: Boolean = true,
    big: Boolean = false
) {
    val bg = when (kind) {
        BtnKind.Primary -> TA.flame
        BtnKind.Soft -> TA.ink2
        BtnKind.Ghost -> Color.Transparent
        BtnKind.Danger -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.Primary -> TA.flameInk
        BtnKind.Soft -> TA.cream
        BtnKind.Ghost -> TA.cream
        BtnKind.Danger -> TA.bad
    }
    val alpha = if (enabled) 1f else .45f
    Row(
        modifier
            .clip(TA.rPill)
            .background(bg.copy(alpha = bg.alpha * alpha))
            .then(
                if (kind == BtnKind.Ghost || kind == BtnKind.Danger)
                    Modifier.border(1.dp, (if (kind == BtnKind.Danger) TA.bad else TA.line).copy(alpha = alpha), TA.rPill)
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (big) 26.dp else 20.dp, vertical = if (big) 17.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, size = 18.dp, tint = fg.copy(alpha = alpha))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (big) 16.sp else 14.5.sp),
            color = fg.copy(alpha = alpha)
        )
    }
}

/** Superfície padrão: fundo elevado, borda discreta, cantos largos. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(TA.rLg)
            .background(if (accent) TA.flame.copy(alpha = .10f) else TA.ink1)
            .border(1.dp, if (accent) TA.flame.copy(alpha = .35f) else TA.line, TA.rLg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** Etiqueta curta — grupo muscular, nível, equipamento. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: String? = null
) {
    Row(
        modifier
            .clip(TA.rPill)
            .background(if (selected) TA.flame else TA.ink2)
            .border(1.dp, if (selected) TA.flame else TA.line, TA.rPill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, size = 14.dp, tint = if (selected) TA.flameInk else TA.cream2)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TA.flameInk else TA.cream2
        )
    }
}

/** Título de seção, com ação opcional à direita. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = TA.cream)
        action?.invoke()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, modifier, color = TA.cream2, style = MaterialTheme.typography.bodySmall, textAlign = align)
}

/** Número grande em fonte monoespaçada, com legenda embaixo. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, color: Color = TA.cream) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = NumberStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium), color = color)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TA.cream3, textAlign = TextAlign.Center)
    }
}

/** Anel de pontuação: verde acima de 85, âmbar acima de 65, vermelho abaixo. */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    caption: String? = null
) {
    val target = (score.coerceIn(0, 100)) / 100f
    val animated by animateFloatAsState(target, label = "score")
    val color = TA.scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 11.dp.toPx()
            val inset = w / 2
            drawArc(
                TA.ink3, -90f, 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - w, this.size.height - w),
                style = Stroke(w, cap = StrokeCap.Round)
            )
            drawArc(
                color, -90f, 360f * animated, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - w, this.size.height - w),
                style = Stroke(w, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = NumberStyle.copy(fontSize = 40.sp), color = color)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = TA.cream3)
        }
    }
}

/** Barra de progresso fina, na cor da marca. */
@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier, color: Color = TA.flame, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(TA.rPill)
            .background(TA.ink3)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(TA.rPill)
                .background(color)
        )
    }
}

/** Linha de ajuste com interruptor — usada na tela de configurações. */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: String? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, size = 19.dp, tint = TA.cream2)
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) TA.cream else TA.cream3)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TA.cream3)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TA.flameInk,
                checkedTrackColor = TA.flame,
                checkedBorderColor = TA.flame,
                uncheckedThumbColor = TA.cream3,
                uncheckedTrackColor = TA.ink3,
                uncheckedBorderColor = TA.line
            )
        )
    }
}

/** Campo de texto no estilo do app. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    revealed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = TA.rMd,
        textStyle = LocalTextStyle.current.copy(color = TA.cream),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TA.flame,
            unfocusedBorderColor = TA.line,
            focusedContainerColor = TA.ink1,
            unfocusedContainerColor = TA.ink1,
            focusedLabelColor = TA.flame,
            unfocusedLabelColor = TA.cream3,
            cursorColor = TA.flame,
            focusedTextColor = TA.cream,
            unfocusedTextColor = TA.cream
        )
    )
}

/** Aviso curto — erro de formulário, dica, alerta de plano. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, tone: Color = TA.warn, icon: String = "info") {
    Row(
        modifier
            .fillMaxWidth()
            .clip(TA.rMd)
            .background(tone.copy(alpha = .10f))
            .border(1.dp, tone.copy(alpha = .30f), TA.rMd)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, size = 17.dp, tint = tone)
        Text(text, style = MaterialTheme.typography.bodySmall, color = TA.cream)
    }
}

/**
 * Gráfico de linha: série única, cor da marca, grade discreta e
 * ponto final destacado — igual ao da versão web.
 */
@Composable
fun LineChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    minY: Int = 0,
    maxY: Int = 100
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val padL = 6.dp.toPx()
        val padB = 6.dp.toPx()
        val w = size.width - padL * 2
        val h = size.height - padB * 2
        val span = max(1, maxY - minY).toFloat()

        /* grade */
        for (i in 0..3) {
            val y = padB + h * i / 3f
            drawLine(TA.cream.copy(alpha = .06f), Offset(padL, y), Offset(padL + w, y), 1f)
        }

        val pts = values.mapIndexed { i, v ->
            val x = padL + w * i / (values.size - 1).toFloat()
            val y = padB + h * (1f - (v.coerceIn(minY, maxY) - minY) / span)
            Offset(x, y)
        }

        /* área sob a curva, bem discreta */
        val area = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts.first().x, padB + h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, padB + h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(TA.flame.copy(alpha = .22f), Color.Transparent)))

        /* linha */
        for (i in 0 until pts.size - 1) {
            drawLine(TA.flame, pts[i], pts[i + 1], 2.6.dp.toPx(), StrokeCap.Round)
        }

        /* ponto final destacado */
        val last = pts.last()
        drawCircle(TA.ink0, radius = 6.dp.toPx(), center = last)
        drawCircle(TA.flame, radius = 6.dp.toPx(), center = last, style = Stroke(2.4.dp.toPx()))
    }
}

/** Barras verticais — distribuição de erros, volume por grupo. */
@Composable
fun BarChart(entries: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val top = max(1, entries.maxOfOrNull { it.second } ?: 1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        entries.forEach { (label, v) ->
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TA.cream2)
                    Text("$v", style = NumberStyle.copy(fontSize = 13.sp), color = TA.cream)
                }
                Spacer(Modifier.height(5.dp))
                Bar(v.toFloat() / top, height = 6.dp)
            }
        }
    }
}

/** Grade da semana: sete quadradinhos, o de hoje em destaque. */
@Composable
fun WeekStrip(days: List<com.treineai.app.data.WeekDay>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        days.forEach { d ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .height(40.dp)
                        .clip(TA.rSm)
                        .background(if (d.done) TA.flame else TA.ink2)
                        .border(
                            1.dp,
                            if (d.today) TA.flame else if (d.future) TA.lineSoft else TA.line,
                            TA.rSm
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (d.done) Icon("check", size = 16.dp, tint = TA.flameInk)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    d.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d.today) TA.flame else TA.cream3
                )
            }
        }
    }
}

/** Cabeçalho de tela com voltar e título. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon("back", size = 21.dp, tint = TA.cream) }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = TA.cream
        )
        action?.invoke()
    }
}

/** Selo do provedor de visão computacional — "IA ATIVA" ou "MODO DEMO". */
@Composable
fun ProviderBadge(label: String, modifier: Modifier = Modifier) {
    val real = label.contains("IA")
    Row(
        modifier
            .clip(TA.rPill)
            .background(TA.ink0.copy(alpha = .72f))
            .border(1.dp, if (real) TA.good.copy(alpha = .5f) else TA.warn.copy(alpha = .5f), TA.rPill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(TA.rPill)
                .background(if (real) TA.good else TA.warn)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = if (real) TA.good else TA.warn
        )
    }
}

/** Estado vazio, com ícone e uma frase. */
@Composable
fun EmptyState(icon: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, size = 34.dp, tint = TA.cream3)
        Spacer(Modifier.height(11.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TA.cream3,
            textAlign = TextAlign.Center
        )
    }
}
