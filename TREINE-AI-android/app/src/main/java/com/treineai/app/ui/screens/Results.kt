package com.treineai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.data.ExerciseSession
import com.treineai.app.data.WorkoutRecord
import com.treineai.app.motion.ERROR_LABEL
import com.treineai.app.motion.ERROR_TIP
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Bar
import com.treineai.app.ui.BarChart
import com.treineai.app.ui.Btn
import com.treineai.app.ui.BtnKind
import com.treineai.app.ui.Card
import com.treineai.app.ui.Chip
import com.treineai.app.ui.EmptyState
import com.treineai.app.ui.Icon
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.Route
import com.treineai.app.ui.ScoreRing
import com.treineai.app.ui.SectionTitle
import com.treineai.app.ui.SessionPlan
import com.treineai.app.ui.StatBlock
import com.treineai.app.ui.TA
import com.treineai.app.ui.TopBar
import kotlinx.coroutines.delay

/* ============================================================
   RESULTADOS — do exercício e do treino.

   Ambos são FALADOS por inteiro assim que abrem: quem acabou de
   treinar ainda está longe do celular. No modo sem as mãos a tela
   de exercício avança sozinha depois de 15 segundos.
   ============================================================ */

private fun band(score: Int): Pair<String, Color> = when {
    score >= 90 -> "Excelente" to TA.good
    score >= 75 -> "Bom" to TA.cream
    score >= 60 -> "Precisa melhorar" to TA.warn
    else -> "Corrija sua execução" to TA.bad
}

/** Frase principal do resultado, igual à `mainFeedback` da versão web. */
private fun mainFeedback(r: ExerciseSession): String {
    if (r.reps == 0) return "Nenhuma repetição foi registrada neste exercício."
    if (r.score >= 92)
        return "Execução muito consistente. Amplitude e controle dentro do esperado em quase todas as repetições."
    val err = r.mainError
    if (err != null) {
        val label = ERROR_LABEL[err] ?: err
        val good = if (r.validReps >= r.reps * .7) "No geral a execução foi boa, mas "
        else "O ponto principal a corrigir foi "
        return "${good}o padrão mais frequente foi $label."
    }
    return "Boa execução. Continue mantendo esse padrão nas próximas séries."
}

private fun personalMessage(delta: Int?): String = when {
    delta == null -> "Primeiro treino registrado. A partir de agora o TREINE AI compara cada sessão com a anterior."
    delta > 4 -> "Excelente treino. Sua execução melhorou em relação ao último treino."
    delta > 0 -> "Evolução leve em relação ao último treino. Consistência está funcionando."
    delta == 0 -> "Mesmo nível do último treino. Constância também é progresso."
    delta > -6 -> "Score um pouco abaixo do treino anterior. Vale revisar a amplitude e o ritmo."
    else -> "Treino mais difícil que o anterior. Considere reduzir a carga e priorizar a técnica."
}

private fun fmtMin(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}min" else "${s}s"
}

/* ============================================================
   Resultado do exercício
   ============================================================ */
@Composable
fun ExResultScreen(app: AppState, plan: SessionPlan) {
    val r = app.sessionResults.lastOrNull() ?: run {
        EmptyState("alert", "Nenhum treino em andamento")
        return
    }
    val last = plan.isLast
    val tip = r.mainError?.let { ERROR_TIP[it] }
    val (bandText, bandColor) = band(r.score)

    var done by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(if (app.settings.autoStart) 15 else 0) }

    fun goNext() {
        if (done) return
        done = true
        app.feedback.silence()
        if (last) {
            val id = app.finishWorkout(plan)
            if (id != null) app.resetTo(Route.WorkoutResult(id))
        } else {
            app.motion.detach()
            app.replace(Route.Pretrain(plan.next()))
        }
    }

    fun again() {
        if (done) return
        done = true
        app.feedback.silence()
        app.sessionResults.removeAt(app.sessionResults.lastIndex)
        app.motion.detach()
        app.replace(Route.Pretrain(plan))
    }

    /* resumo falado — a pessoa não precisa olhar para a tela */
    LaunchedEffect(Unit) {
        val falho = if (r.invalid > 0) ", ${r.invalid} incompletas" else ""
        app.feedback.say(
            "Exercício completo. ${r.reps} repetições, ${r.validReps} válidas$falho. " +
                "Qualidade média ${r.score} por cento.",
            p = 2, always = true
        )
        if (tip != null) app.feedback.say(tip, p = 1, always = true)
        if (app.settings.autoStart) {
            app.feedback.say(
                if (last) "Diga finalizar para encerrar o treino." else "Diga próximo para continuar.",
                p = 1, always = true
            )
        }
    }

    /* avanço automático no modo sem as mãos */
    LaunchedEffect(secondsLeft) {
        if (secondsLeft <= 0) return@LaunchedEffect
        delay(1000)
        secondsLeft--
        if (secondsLeft == 5) {
            app.feedback.say(
                if (last) "Encerrando em cinco segundos." else "Próximo exercício em cinco segundos.",
                p = 1, always = true
            )
        }
        if (secondsLeft <= 0) goNext()
    }

    DisposableEffect(Unit) {
        app.voice.onCommand = { cmd, _ ->
            when (cmd) {
                "next", "start", "finish" -> goNext()
                "repeat" -> again()
                "back", "stopall" -> { app.sessionResults.clear(); quitWorkout(app) }
                "status" -> app.feedback.say(
                    "${r.reps} repetições, ${r.validReps} válidas, qualidade ${r.score}",
                    p = 2, always = true
                )
            }
        }
        onDispose { app.voice.onCommand = null }
    }

    Column(
        Modifier.fillMaxSize().background(TA.ink0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TA.pad),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "EXERCÍCIO COMPLETO 🔥",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = TA.flame
            )
            Text(r.name, style = MaterialTheme.typography.displaySmall, color = TA.cream)
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ScoreRing(r.score, size = 108.dp, caption = "SCORE")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(bandText)
                    ResultRow("Repetições", r.reps.toString(), TA.cream)
                    ResultRow("Válidas", r.validReps.toString(), TA.good)
                    ResultRow("Incompletas", r.invalid.toString(), if (r.invalid > 0) TA.warn else TA.cream3)
                    ResultRow("Melhor rep.", r.best.toString(), TA.cream)
                }
            }
        }

        Card {
            Text(
                "FEEDBACK PRINCIPAL",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = TA.cream3
            )
            Spacer(Modifier.height(10.dp))
            Text(mainFeedback(r), style = MaterialTheme.typography.bodyLarge, color = TA.cream)
            if (tip != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon("bolt", size = 17.dp, tint = TA.flame)
                    Text(tip, style = MaterialTheme.typography.bodySmall, color = TA.cream2)
                }
            }
        }

        if (r.errors.isNotEmpty()) {
            Card {
                Text(
                    "OCORRÊNCIAS DETECTADAS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = TA.cream3
                )
                Spacer(Modifier.height(10.dp))
                BarChart(
                    r.errors.entries.sortedByDescending { it.value }.take(4)
                        .map { (ERROR_LABEL[it.key] ?: it.key) to it.value }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(
                label = (if (last) "Finalizar treino" else "Próximo exercício") +
                    if (secondsLeft > 0) " · ${secondsLeft}s" else "",
                onClick = { app.feedback.tap(); goNext() },
                modifier = Modifier.fillMaxWidth(),
                big = true
            )
            if (!last) {
                Btn(
                    "Repetir este exercício",
                    onClick = { app.feedback.tap(); again() },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Ghost
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TA.cream2)
        Text(value, style = NumberStyle.copy(fontSize = 13.sp), color = color)
    }
}

/* ============================================================
   Resultado do treino
   ============================================================ */
@Composable
fun WorkoutResultScreen(app: AppState, recordId: String) {
    val rev = app.revision
    val rec: WorkoutRecord? = remember(recordId, rev) {
        app.repo.data().workouts.firstOrNull { it.id == recordId }
    }

    if (rec == null) {
        Column(Modifier.fillMaxSize().background(TA.ink0)) {
            TopBar("Treino", onBack = { app.back() })
            EmptyState("alert", "Treino não encontrado")
            Btn(
                "Ver evolução",
                onClick = { app.tab(Route.Progress) },
                modifier = Modifier.padding(TA.pad),
                kind = BtnKind.Ghost
            )
        }
        return
    }

    /* "Fresco" = acabou de ser gravado: comemora e fala o resultado. */
    val fresh = remember(recordId) { app.lastXp > 0 && app.repo.data().workouts.firstOrNull()?.id == recordId }
    val delta = rec.prevScore?.let { rec.score - it }
    val best = rec.sessions.maxByOrNull { it.score }
    val worst = rec.sessions.minByOrNull { it.score }
    val totalReps = rec.sessions.sumOf { it.reps }
    val validReps = rec.sessions.sumOf { it.validReps }

    LaunchedEffect(recordId) {
        if (!fresh) return@LaunchedEffect
        app.feedback.success()
        app.feedback.say(
            "Treino concluído. Score ${rec.score} de 100. ${personalMessage(delta)}",
            p = 2, always = true
        )
    }

    Column(Modifier.fillMaxSize().background(TA.ink0)) {
        if (!fresh) TopBar(rec.name, onBack = { app.back() })

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (fresh) {
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TREINO CONCLUÍDO 🔥",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = TA.flame
                    )
                    Text(rec.name, style = MaterialTheme.typography.displaySmall, color = TA.cream)
                    Text(personalMessage(delta), style = MaterialTheme.typography.bodyLarge, color = TA.cream2)
                }
            }

            /* cartão principal com o score do treino */
            Column(
                Modifier.fillMaxWidth().clip(TA.rXl).background(TA.flame).padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "TREINO SCORE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = TA.flameInk.copy(alpha = .75f)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${rec.score}", style = NumberStyle.copy(fontSize = 58.sp), color = TA.flameInk)
                            Text(
                                "/100",
                                style = NumberStyle.copy(fontSize = 24.sp),
                                color = TA.flameInk.copy(alpha = .7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    if (delta != null) {
                        Row(
                            Modifier.clip(TA.rPill).background(Color.Black.copy(alpha = .28f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (delta > 0) "up" else if (delta < 0) "down" else "minus",
                                size = 15.dp, tint = TA.flameInk
                            )
                            Text(
                                (if (delta > 0) "+" else "") + delta,
                                style = NumberStyle.copy(fontSize = 13.sp), color = TA.flameInk
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(fmtMin(rec.duration), "Duração", Modifier.weight(1f))
                StatBlock("${rec.sessions.size}", "Exercícios", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock("$totalReps", "Repetições", Modifier.weight(1f))
                StatBlock("$validReps", "Válidas", Modifier.weight(1f))
            }

            Card {
                Text(
                    "COMPOSIÇÃO DO SCORE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = TA.cream3
                )
                Spacer(Modifier.height(14.dp))
                BarChart(
                    listOf(
                        "Técnica" to rec.breakdown.tecnica,
                        "Consistência" to rec.breakdown.consistencia,
                        "Amplitude" to rec.breakdown.amplitude,
                        "Controle" to rec.breakdown.controle
                    )
                )
            }

            if (best != null && worst != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1f)) {
                        Text(
                            "MELHOR EXERCÍCIO",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = TA.good
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(best.name, style = MaterialTheme.typography.titleSmall, color = TA.cream)
                        Text("${best.score}", style = NumberStyle.copy(fontSize = 15.sp), color = TA.good)
                    }
                    Card(Modifier.weight(1f)) {
                        Text(
                            "PARA MELHORAR",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = TA.warn
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(worst.name, style = MaterialTheme.typography.titleSmall, color = TA.cream)
                        Text("${worst.score}", style = NumberStyle.copy(fontSize = 15.sp), color = TA.warn)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Exercícios")
                rec.sessions.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(TA.rMd).background(TA.ink1)
                            .border(1.dp, TA.line, TA.rMd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${s.score}", style = NumberStyle.copy(fontSize = 13.sp), color = TA.scoreColor(s.score))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = TA.cream)
                            Text(
                                "${s.validReps}/${s.reps} válidas" +
                                    (s.mainError?.let { " · ${ERROR_LABEL[it] ?: it}" } ?: ""),
                                style = NumberStyle.copy(fontSize = 11.sp), color = TA.cream3
                            )
                        }
                        Box(Modifier.width(56.dp)) { Bar(s.score / 100f, height = 5.dp) }
                    }
                }
            }

            if (fresh && app.lastXp > 0) {
                Card(accent = true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon("bolt", size = 20.dp, tint = TA.flame)
                        Column(Modifier.weight(1f)) {
                            Text("+${app.lastXp} XP", style = MaterialTheme.typography.titleSmall, color = TA.cream)
                            val lv = app.repo.level()
                            Text(
                                "Nível ${lv.level} — ${lv.name}",
                                style = MaterialTheme.typography.bodySmall, color = TA.cream3
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fresh) {
                    Btn("Ver evolução", onClick = { app.tab(Route.Progress) }, modifier = Modifier.fillMaxWidth())
                    Btn(
                        "Voltar ao início",
                        onClick = { app.tab(Route.Home) },
                        modifier = Modifier.fillMaxWidth(),
                        kind = BtnKind.Ghost
                    )
                } else {
                    Btn(
                        "Voltar",
                        onClick = { app.back() },
                        modifier = Modifier.fillMaxWidth(),
                        kind = BtnKind.Ghost
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
