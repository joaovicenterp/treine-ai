package com.treineai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.data.WorkoutRecord
import com.treineai.app.data.addDays
import com.treineai.app.data.dayKey
import com.treineai.app.motion.ERROR_LABEL
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Bar
import com.treineai.app.ui.BarChart
import com.treineai.app.ui.Btn
import com.treineai.app.ui.BtnKind
import com.treineai.app.ui.Card
import com.treineai.app.ui.Chip
import com.treineai.app.ui.EmptyState
import com.treineai.app.ui.Field
import com.treineai.app.ui.Icon
import com.treineai.app.ui.LineChart
import com.treineai.app.ui.Muted
import com.treineai.app.ui.Note
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.Route
import com.treineai.app.ui.StatBlock
import com.treineai.app.ui.TA
import com.treineai.app.ui.ToggleRow
import com.treineai.app.ui.TopBar
import com.treineai.app.ui.WeekStrip
import com.treineai.app.ui.Wordmark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/* ============================================================
   PERFIL E EVOLUÇÃO — Evolução, Conquistas, Perfil, Editar
   perfil, Configurações, Privacidade, Planos e Assinatura.

   Portadas de `SCREENS.progress / achievements / profile /
   editProfile / settings / privacy / paywall / subscription`
   da versão web: mesmos textos, mesma hierarquia, mesmos números.
   ============================================================ */

/* ---------------- medidas e formatadores ---------------- */

private val PfBlockGap = 14.dp

/** A barra inferior é do shell; as abas só reservam a altura dela. */
private val PfTabPad = TA.navH + 20.dp

/** `fmtDate` da web: a chave `yyyy-MM-dd` vira `dd/MM`. */
private fun pfDate(key: String): String {
    val p = key.split("-")
    return if (p.size >= 3) "${p[2]}/${p[1]}" else key
}

/** `fmtMin` da web: segundos viram `3min 07s` ou `47s`. */
private fun pfMin(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}min ${s.toString().padStart(2, '0')}s" else "${s}s"
}

/** `money` da web: símbolo do catálogo e vírgula decimal. */
private fun pfMoney(symbol: String, value: Double): String =
    "$symbol " + String.format(Locale.US, "%.2f", value).replace('.', ',')

/** `toLocaleDateString('pt-BR')` — dia/mês/ano. */
private fun pfFullDate(ts: Long?): String =
    if (ts == null) "—" else SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(ts))

/** Mostra 72 em vez de 72.0 ao reabrir o formulário. */
private fun pfNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** O teclado brasileiro entrega vírgula decimal; o campo aceita as duas formas. */
private fun pfParseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/* ---------------- agregações da versão web ---------------- */

private data class PfWeek(val count: Int, val score: Int)

/**
 * `weekAgg` da web: a semana começa na segunda-feira e `back` conta
 * quantas semanas voltar. Serve para o "vs. semana anterior".
 */
private fun pfWeekAgg(ws: List<WorkoutRecord>, back: Int): PfWeek {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monday = addDays(now, -dow - back * 7)
    val a = dayKey(monday)
    val b = dayKey(addDays(monday, 6))
    val sel = ws.filter { it.date in a..b }
    return PfWeek(
        count = sel.size,
        score = if (sel.isEmpty()) 0 else (sel.sumOf { it.score }.toDouble() / sel.size).roundToInt()
    )
}

/**
 * `buildInsights` da web — só lê o histórico real, nunca inventa números.
 * A lista sai na mesma ordem e com o mesmo corte de quatro frases.
 */
private fun pfInsights(ws: List<WorkoutRecord>): List<String> {
    val out = ArrayList<String>()

    if (ws.size >= 2) {
        val a = ws[0].score
        val b = ws[1].score
        if (a != b) {
            val d = abs(a - b)
            out.add(
                "Seu último treino ficou $d ${if (d == 1) "ponto" else "pontos"} " +
                    "${if (a > b) "acima" else "abaixo"} do anterior."
            )
        }
    }

    val errs = HashMap<String, Int>()
    ws.take(6).forEach { w ->
        w.sessions.forEach { s -> s.errors.forEach { (k, v) -> errs[k] = (errs[k] ?: 0) + v } }
    }
    errs.entries.maxByOrNull { it.value }?.let { top ->
        out.add(
            "O padrão mais recorrente nos últimos treinos foi " +
                "${ERROR_LABEL[top.key] ?: top.key} (${top.value} ocorrências)."
        )
    }

    val byEx = LinkedHashMap<String, MutableList<Int>>()
    ws.take(8).forEach { w ->
        w.sessions.forEach { s -> byEx.getOrPut(s.name) { ArrayList() }.add(s.score) }
    }
    val ranked = byEx.filter { it.value.size >= 2 }
        .map { (name, scores) -> name to (scores.sum().toDouble() / scores.size).roundToInt() }
        .sortedBy { it.second }
    if (ranked.isNotEmpty()) {
        val best = ranked.last()
        out.add("Seu exercício mais consistente é ${best.first} (média ${best.second}).")
        if (ranked.size > 1) {
            val worst = ranked.first()
            out.add("${worst.first} é onde há mais espaço para ganho (média ${worst.second}).")
        }
    }

    val t = pfWeekAgg(ws, 0)
    val l = pfWeekAgg(ws, 1)
    if (t.score > 0 && l.score > 0) {
        val pct = abs(((t.score - l.score) / l.score.toDouble() * 100).roundToInt())
        out.add(
            "Sua qualidade de execução ${if (t.score >= l.score) "subiu" else "caiu"} " +
                "$pct% em relação à semana passada."
        )
    }

    return out.take(4)
}

/* ---------------- peças visuais ---------------- */

/** `.eyebrow` — mono, versalete espaçado; a variante `hot` é a da marca. */
@Composable
private fun PfEyebrow(text: String, tint: Color = TA.cream3, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = NumberStyle.copy(fontSize = 10.5.sp, letterSpacing = 1.7.sp),
        color = tint
    )
}

/** `.listrow` — ícone à esquerda, título, legenda e seta. */
@Composable
private fun PfRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, size = 19.dp, tint = TA.cream2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp),
                    color = TA.cream
                )
                if (subtitle.isNotEmpty()) Muted(subtitle)
            }
            Icon("chevron", size = 18.dp, tint = TA.cream3)
        }
    }
}

/** `.row.between` com valor monoespaçado — usada nas fichas de resumo. */
@Composable
private fun PfKeyValue(label: String, value: String, valueColor: Color = TA.cream) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TA.cream2)
        Text(value, style = NumberStyle.copy(fontSize = 12.sp), color = valueColor)
    }
}

/** Item de lista com marcador — privacidade e "o plano gratuito inclui". */
@Composable
private fun PfCheck(text: String, icon: String = "check", tint: Color = TA.cream3) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, size = 16.dp, tint = tint, modifier = Modifier.padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TA.cream)
    }
}

/** Número grande dentro de uma ficha — o `.stat` da web. */
@Composable
private fun PfStat(value: String, label: String, modifier: Modifier = Modifier, color: Color = TA.cream) {
    Card(modifier, padding = 14.dp) {
        StatBlock(value, label, Modifier.fillMaxWidth(), color = color)
    }
}

/** Faixa de venda do PRO — o `proBanner` da web, com o mesmo texto reserva. */
@Composable
private fun PfProBanner(app: AppState, reason: String = "") {
    Card(onClick = { app.feedback.tap(); app.go(Route.Paywall(reason)) }, accent = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(TA.rPill)
                    .background(TA.flame.copy(alpha = .18f)),
                contentAlignment = Alignment.Center
            ) { Icon("crown", size = 18.dp, tint = TA.flame) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "TREINE AI PRO",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.5.sp),
                    color = TA.cream
                )
                Muted(reason.ifEmpty { "Análise avançada, insights e histórico completo." })
            }
            Icon("chevron", size = 18.dp, tint = TA.flame)
        }
    }
}

/** Faixa de escolha exclusiva — o `.seg` da web, feito de fichas. */
@Composable
private fun PfSegment(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            Chip(label, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}

/** `confirmSheet` da web: mesmo título, mesmo texto, mesmos rótulos. */
@Composable
private fun PfConfirm(
    title: String,
    text: String,
    ok: String,
    onOk: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TA.ink1,
        titleContentColor = TA.cream,
        textContentColor = TA.cream2,
        shape = TA.rLg,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium, color = TA.cream) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = TA.cream2) },
        confirmButton = { Btn(ok, onClick = onOk, kind = BtnKind.Danger) },
        dismissButton = { Btn("Cancelar", onClick = onDismiss, kind = BtnKind.Ghost) }
    )
}

/* ============================================================
   EVOLUÇÃO
   ============================================================ */

@Composable
fun ProgressScreen(app: AppState) {
    val rev = app.revision
    val cfg = app.catalog.d.config
    val pro = app.repo.isPro()
    val all = app.user.workouts          // estatísticas usam o histórico inteiro
    val vis = app.repo.visibleWorkouts() // a lista visível respeita o limite do plano
    val stats = app.repo.stats()

    /* O plano gratuito abre em 7 dias; o PRO, em 30 — como na web. */
    var range by remember(pro) { mutableStateOf(if (pro) 30 else 7) }
    val cutoff = dayKey(addDays(System.currentTimeMillis(), -range))
    /* do mais antigo para o mais recente: é a ordem que o gráfico espera */
    val data = vis.filter { it.date >= cutoff }.reversed()

    if (all.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TA.ink0)
        ) {
            TopBar("Evolução", modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TA.pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmptyState("chart", "Sem dados ainda")
                Muted(
                    "Complete seu primeiro treino para começar a acompanhar sua evolução.",
                    Modifier.fillMaxWidth(),
                    align = TextAlign.Center
                )
                Btn("Escolher treino", onClick = { app.feedback.tap(); app.tab(Route.Workouts) })
            }
        }
        return
    }

    val thisWeek = pfWeekAgg(all, 0)
    val lastWeek = pfWeekAgg(all, 1)
    val dq = if (thisWeek.score > 0 && lastWeek.score > 0)
        ((thisWeek.score - lastWeek.score) / lastWeek.score.toDouble() * 100).roundToInt() else null

    /* Distribuição dos padrões observados no período selecionado. */
    val errCounts = HashMap<String, Int>()
    data.forEach { w ->
        w.sessions.forEach { s -> s.errors.forEach { (k, v) -> errCounts[k] = (errCounts[k] ?: 0) + v } }
    }
    val errEntries = errCounts.entries.sortedByDescending { it.value }.take(5)
        .map { (ERROR_LABEL[it.key] ?: it.key) to it.value }

    /* Volume por grupo muscular: repetições válidas somadas por grupo. */
    val groupCounts = LinkedHashMap<String, Int>()
    data.forEach { w ->
        w.sessions.forEach { s ->
            if (s.group.isNotEmpty()) groupCounts[s.group] = (groupCounts[s.group] ?: 0) + s.validReps
        }
    }
    val groupEntries = groupCounts.entries.sortedByDescending { it.value }.take(6)
        .map { app.catalog.groupName(it.key) to it.value }

    val insights = pfInsights(all)

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Evolução",
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp),
            action = {
                Text(
                    "${stats.workouts} treinos",
                    style = NumberStyle.copy(fontSize = 11.sp),
                    color = TA.cream3
                )
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(PfBlockGap)
        ) {
            /* ---- período ---- */
            PfSegment(
                listOf("7" to "7 dias", "30" to "30 dias", "365" to "Tudo"),
                selected = range.toString(),
                onSelect = { v ->
                    app.feedback.tap()
                    val r = v.toInt()
                    /* além do limite gratuito o caminho é a tela de planos */
                    if (!pro && r > cfg.free.historyDays)
                        app.go(Route.Paywall("O histórico completo faz parte do TREINE AI PRO."))
                    else range = r
                }
            )

            /* ---- aviso do histórico limitado ---- */
            if (!pro) {
                Card(padding = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon("lock", size = 18.dp, tint = TA.flame)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Histórico limitado a ${cfg.free.historyDays} dias",
                                style = MaterialTheme.typography.bodySmall,
                                color = TA.cream
                            )
                            Muted("O plano PRO mantém todo o seu histórico.")
                        }
                        Btn(
                            "Ver PRO",
                            onClick = { app.feedback.tap(); app.go(Route.Paywall("")) },
                            kind = BtnKind.Ghost
                        )
                    }
                }
            }

            /* ---- score dos treinos ---- */
            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PfEyebrow("Score dos treinos")
                    if (dq != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tone = if (dq > 0) TA.good else if (dq < 0) TA.bad else TA.cream2
                            Icon(
                                if (dq > 0) "up" else if (dq < 0) "down" else "minus",
                                size = 14.dp,
                                tint = tone
                            )
                            Text(
                                "${if (dq > 0) "+" else ""}$dq% vs. semana anterior",
                                style = NumberStyle.copy(fontSize = 11.sp),
                                color = tone
                            )
                        }
                    } else {
                        Muted("série única")
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    data.size >= 2 -> LineChart(
                        data.map { it.score },
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        minY = max(0, (data.minOfOrNull { it.score } ?: 0) - 8),
                        maxY = 100
                    )

                    data.size == 1 -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${data[0].score}",
                            style = NumberStyle.copy(fontSize = 52.sp),
                            color = TA.cream
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                data[0].name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TA.cream
                            )
                            Muted("A linha de evolução aparece a partir do segundo treino.")
                        }
                    }

                    else -> Muted(
                        "Nenhum treino neste período.",
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        align = TextAlign.Center
                    )
                }
            }

            /* ---- números do histórico ---- */
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PfStat("${stats.avgScore}", "Score médio", Modifier.weight(1f))
                PfStat(
                    if (stats.minutes > 0) "${stats.minutes} min" else "<1 min",
                    "Tempo treinado",
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PfStat("${stats.validReps}", "Reps válidas", Modifier.weight(1f))
                PfStat("${stats.streak}", "Sequência", Modifier.weight(1f))
            }

            /* ---- esta semana ---- */
            Card {
                PfEyebrow("Esta semana")
                Spacer(Modifier.height(12.dp))
                WeekStrip(app.repo.weekGrid())
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TA.lineSoft))
                Spacer(Modifier.height(12.dp))
                PfKeyValue("Treinos", "${thisWeek.count} vs ${lastWeek.count}")
                Spacer(Modifier.height(8.dp))
                PfKeyValue(
                    "Qualidade média",
                    "${if (thisWeek.score > 0) thisWeek.score.toString() else "—"} " +
                        "vs ${if (lastWeek.score > 0) lastWeek.score.toString() else "—"}"
                )
            }

            /* ---- repetições válidas por treino ---- */
            if (data.isNotEmpty()) {
                Card {
                    PfEyebrow("Repetições válidas por treino")
                    Spacer(Modifier.height(12.dp))
                    BarChart(
                        data.takeLast(8).map { w ->
                            pfDate(w.date) to w.sessions.sumOf { it.validReps }
                        },
                        Modifier.fillMaxWidth()
                    )
                }
            }

            /* ---- padrões mais frequentes ---- */
            if (errEntries.isNotEmpty()) {
                Card {
                    PfEyebrow("Padrões mais frequentes", TA.warn)
                    Spacer(Modifier.height(12.dp))
                    BarChart(errEntries, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Muted("Revise sua execução nos pontos que aparecem com mais frequência.")
                }
            }

            /* ---- volume por grupo muscular ---- */
            if (groupEntries.isNotEmpty()) {
                Card {
                    PfEyebrow("Volume por grupo muscular")
                    Spacer(Modifier.height(12.dp))
                    BarChart(groupEntries, Modifier.fillMaxWidth())
                }
            }

            /* ---- TREINE AI Insight ---- */
            if (!pro) {
                Card(
                    onClick = {
                        app.feedback.tap()
                        app.go(Route.Paywall("Os insights da IA fazem parte do TREINE AI PRO."))
                    },
                    accent = true
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PfEyebrow("TREINE AI Insight", TA.flame)
                        Chip("PRO", selected = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    /* o conteúdo real fica oculto: só as faixas cinzas, como o borrão da web */
                    listOf(.92f, .76f, .84f).forEach { w ->
                        Box(
                            Modifier
                                .fillMaxWidth(w)
                                .height(12.dp)
                                .clip(TA.rSm)
                                .background(TA.ink3)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Descubra os padrões da sua execução →",
                        style = MaterialTheme.typography.bodySmall,
                        color = TA.cream
                    )
                }
            } else if (insights.isNotEmpty()) {
                Card(accent = true) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PfEyebrow("TREINE AI Insight", TA.flame)
                        Chip("PRO", selected = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        insights.forEach { PfCheck(it, icon = "bolt", tint = TA.flame) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Muted("Gerado a partir do seu histórico. Nenhuma informação é inventada.")
                }
            }

            /* ---- treinos recentes ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Treinos recentes")
                vis.take(8).forEach { h ->
                    Card(
                        onClick = { app.feedback.tap(); app.go(Route.WorkoutResult(h.id)) },
                        padding = 14.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${h.score}",
                                style = NumberStyle.copy(fontSize = 13.sp),
                                color = TA.scoreColor(h.score)
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    h.name,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp),
                                    color = TA.cream
                                )
                                Text(
                                    "${pfDate(h.date)} · ${pfMin(h.duration)}",
                                    style = NumberStyle.copy(fontSize = 11.sp),
                                    color = TA.cream3
                                )
                            }
                            Icon("chevron", size = 18.dp, tint = TA.cream3)
                        }
                    }
                }
            }

            Spacer(Modifier.height(PfTabPad))
        }
    }
}

/* ============================================================
   CONQUISTAS
   ============================================================ */

@Composable
fun AchievementsScreen(app: AppState) {
    val rev = app.revision
    val defs = app.catalog.d.achievements
    val got = app.user.achievements
    val lv = app.repo.level()
    val stats = app.repo.stats()

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Conquistas",
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp),
            action = {
                Text(
                    "${got.size}/${defs.size}",
                    style = NumberStyle.copy(fontSize = 11.sp),
                    color = TA.cream3
                )
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(PfBlockGap)
        ) {
            /* ---- nível atual ---- */
            Card(accent = true, padding = 18.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PfEyebrow("Nível ${lv.level}", TA.cream2)
                    Text("${lv.xp} XP", style = NumberStyle.copy(fontSize = 13.sp), color = TA.cream)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    lv.name.uppercase(),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                    color = TA.cream
                )
                Spacer(Modifier.height(12.dp))
                Bar(lv.progress.toFloat())
                Spacer(Modifier.height(10.dp))
                Muted(lv.next?.let { "${lv.toNext} XP para ${it.name}" } ?: "Nível máximo alcançado")
            }

            /* ---- números ---- */
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PfStat("${stats.streak}", "Sequência", Modifier.weight(1f))
                PfStat("${stats.workouts}", "Treinos", Modifier.weight(1f))
                PfStat("${stats.exercises}", "Exercícios", Modifier.weight(1f))
            }

            /* ---- as 12 conquistas ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Todas as conquistas")
                defs.chunked(3).forEach { linha ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        linha.forEach { a ->
                            val on = a.id in got
                            Card(Modifier.weight(1f), accent = on, padding = 12.dp) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    /* a medalha bloqueada fica apagada, como na web */
                                    Text(
                                        a.icon,
                                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                                        color = if (on) TA.cream else TA.cream3.copy(alpha = .45f)
                                    )
                                    Text(
                                        a.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (on) TA.cream else TA.cream3,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        if (on) "+${a.xp} XP" else a.desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (on) TA.flame else TA.cream3,
                                        textAlign = TextAlign.Center
                                    )
                                    /* bloqueada: o XP fica visível como recompensa a conquistar */
                                    if (!on) {
                                        Text(
                                            "+${a.xp} XP",
                                            style = NumberStyle.copy(fontSize = 10.5.sp),
                                            color = TA.cream3.copy(alpha = .7f)
                                        )
                                    }
                                }
                            }
                        }
                        repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            /* ---- escada de níveis ---- */
            Card {
                PfEyebrow("Níveis")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    app.catalog.d.xpLevels.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Nível ${l.level} — ${l.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lv.level >= l.level) TA.cream else TA.cream3
                            )
                            Text(
                                "${l.xp} XP",
                                style = NumberStyle.copy(fontSize = 11.sp),
                                color = TA.cream3
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(PfTabPad))
        }
    }
}

/* ============================================================
   PERFIL
   ============================================================ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(app: AppState) {
    val rev = app.revision
    val p = app.user.profile
    val account = app.account
    val stats = app.repo.stats()
    val lv = app.repo.level()
    val pro = app.repo.isPro()
    val goal = app.catalog.d.goals.firstOrNull { it.id == p.goal }?.name ?: "—"
    var confirmLogout by remember { mutableStateOf(false) }

    /* iniciais do nome, como o avatar reserva da web */
    val initials = (p.name.ifBlank { account?.name ?: "A" })
        .trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull() }.joinToString("").uppercase()

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Perfil",
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp),
            action = {
                Card(
                    onClick = { app.feedback.tap(); app.go(Route.Settings) },
                    padding = 9.dp
                ) { Icon("settings", size = 19.dp, tint = TA.cream) }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(PfBlockGap)
        ) {
            /* ---- cabeçalho ---- */
            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(TA.rPill)
                            .background(TA.ink3)
                            .border(1.5.dp, TA.line, TA.rPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initials,
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                            color = TA.cream2
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            p.name.ifBlank { account?.name ?: "Atleta" },
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 19.sp),
                            color = TA.cream
                        )
                        Muted(account?.email.orEmpty())
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Chip(goal)
                            Chip(p.experience)
                            if (pro) Chip("PRO", selected = true)
                        }
                    }
                }
            }

            /* ---- números ---- */
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PfStat("${stats.streak}", "Sequência", Modifier.weight(1f), color = TA.flame)
                PfStat("${stats.workouts}", "Treinos", Modifier.weight(1f))
                PfStat("${lv.xp}", "XP", Modifier.weight(1f))
            }

            /* ---- nível ---- */
            Card(onClick = { app.feedback.tap(); app.tab(Route.Achievements) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon("bolt", size = 18.dp, tint = TA.flame)
                        Text(
                            "Nível ${lv.level} — ${lv.name}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TA.cream
                        )
                    }
                    Icon("chevron", size = 18.dp, tint = TA.cream3)
                }
                Spacer(Modifier.height(10.dp))
                Bar(lv.progress.toFloat())
            }

            /* ---- acessos ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Meus dados")
                PfRow(
                    "user", "Editar perfil",
                    buildString {
                        p.age?.let { append("$it anos · ") }
                        p.weight?.let { append("${pfNumber(it)} ${p.unit} · ") }
                        p.height?.let { append("${pfNumber(it)} cm") }
                    }.trim().removeSuffix("·").trim()
                ) { app.feedback.tap(); app.go(Route.EditProfile) }

                PfRow(
                    "crown", "Assinatura",
                    when {
                        app.repo.isTrial() -> "Teste PRO · ${app.repo.trialDaysLeft()} dias restantes"
                        pro -> "TREINE AI PRO ativo"
                        else -> "Plano gratuito"
                    }
                ) { app.feedback.tap(); app.go(Route.Subscription) }

                PfRow("settings", "Configurações", "Som, vibração, notificações") {
                    app.feedback.tap(); app.go(Route.Settings)
                }
                PfRow("shield", "Privacidade e dados", "Câmera, exportação e exclusão") {
                    app.feedback.tap(); app.go(Route.Privacy)
                }
            }

            if (!pro) PfProBanner(app)

            Btn(
                "Sair da conta",
                onClick = { app.feedback.tap(); confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                kind = BtnKind.Ghost,
                icon = "logout"
            )

            Muted(app.catalog.d.disclaimer, Modifier.fillMaxWidth(), align = TextAlign.Center)

            Spacer(Modifier.height(PfTabPad))
        }
    }

    if (confirmLogout) {
        PfConfirm(
            title = "Sair da conta?",
            text = "Seus dados continuam salvos neste dispositivo.",
            ok = "Sair",
            onOk = {
                confirmLogout = false
                app.signOut()
            },
            onDismiss = { confirmLogout = false }
        )
    }
}

/* ============================================================
   EDITAR PERFIL
   ============================================================ */

@Composable
fun EditProfileScreen(app: AppState) {
    val rev = app.revision
    val profile = app.user.profile

    var name by remember { mutableStateOf(profile.name) }
    var age by remember { mutableStateOf(profile.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile.weight?.let { pfNumber(it) } ?: "") }
    var height by remember { mutableStateOf(profile.height?.let { pfNumber(it) } ?: "") }
    var goal by remember { mutableStateOf(profile.goal) }
    var level by remember { mutableStateOf(profile.experience.ifEmpty { "Iniciante" }) }
    var freq by remember { mutableStateOf(if (profile.frequency > 0) profile.frequency else 3) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Editar perfil",
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Field(name, { name = it }, "Nome")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(age, { age = it }, "Idade", Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field(
                    weight, { weight = it }, "Peso (${profile.unit})",
                    Modifier.weight(1f), keyboard = KeyboardType.Decimal
                )
                Field(height, { height = it }, "Altura (cm)", Modifier.weight(1f), keyboard = KeyboardType.Number)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Objetivo")
                /* duas colunas, como o `.picks` da web */
                app.catalog.d.goals.chunked(2).forEach { par ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        par.forEach { g ->
                            Card(
                                Modifier.weight(1f),
                                onClick = { app.feedback.tap(); goal = g.id },
                                accent = goal == g.id,
                                padding = 14.dp
                            ) {
                                Text(
                                    g.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
                                    color = TA.cream
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(g.desc, style = MaterialTheme.typography.labelMedium, color = TA.cream3)
                            }
                        }
                        if (par.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Nível")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.catalog.d.levels.forEach { l ->
                        Chip(l, selected = level == l, onClick = { app.feedback.tap(); level = l })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Treinos por semana")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 5, 6).forEach { n ->
                        Chip("$n×", selected = freq == n, onClick = { app.feedback.tap(); freq = n })
                    }
                }
            }

            Btn(
                "Salvar alterações",
                onClick = {
                    app.feedback.tap()
                    /* o nome em branco mantém o anterior, como na web */
                    app.repo.patchProfile(
                        profile.copy(
                            name = name.trim().ifEmpty { profile.name },
                            age = age.trim().toIntOrNull(),
                            weight = pfParseNumber(weight),
                            height = pfParseNumber(height),
                            goal = goal,
                            experience = level,
                            frequency = freq
                        )
                    )
                    app.touch()
                    app.toast = "Perfil atualizado."
                    app.back(Route.Profile)
                },
                modifier = Modifier.fillMaxWidth(),
                big = true
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

/* ============================================================
   CONFIGURAÇÕES
   ============================================================ */

@Composable
fun SettingsScreen(app: AppState) {
    val rev = app.revision
    val s = app.settings
    val p = app.user.profile
    val micOk = app.voice.available()

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Configurações",
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            /* ---- treinar sem olhar para a tela ---- */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PfEyebrow("Treinar sem olhar para a tela")
                Card(padding = 4.dp) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        /* `app.patchSettings` é obrigatório: ele sincroniza TTS e reconhecimento */
                        ToggleRow(
                            "Voz do treinador",
                            "Correções e instruções faladas em português",
                            checked = s.voice,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(voice = v) } }
                        )
                        ToggleRow(
                            "Modo sem olhar",
                            "Narra posicionamento, cada repetição e o resultado",
                            checked = s.audioFirst,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(audioFirst = v) } }
                        )
                        ToggleRow(
                            "Início automático",
                            "Começa sozinho assim que você estiver na posição",
                            checked = s.autoStart,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(autoStart = v) } }
                        )
                        ToggleRow(
                            "Comandos de voz",
                            if (micOk) "Diga começar, pausar, próximo, finalizar"
                            else "Não disponível neste aparelho",
                            checked = s.voiceCommands,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(voiceCommands = v) } },
                            enabled = micOk
                        )
                    }
                }
                Muted("Os comandos de voz usam o microfone só durante o treino e exigem conexão.")
                /* o reconhecimento só liga depois que o sistema concede o microfone */
                if (micOk && s.voiceCommands && !app.voice.hasMicPermission()) {
                    Note(
                        "Permita o acesso ao microfone para usar os comandos de voz.",
                        icon = "mic"
                    )
                }
            }

            /* ---- outros retornos ---- */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PfEyebrow("Outros retornos")
                Card(padding = 4.dp) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        ToggleRow(
                            "Vibração",
                            "Alertas por vibração quando suportado",
                            checked = s.haptics,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(haptics = v) } },
                            icon = "vibrate"
                        )
                        ToggleRow(
                            "Sons",
                            "Bipes de repetição e alerta",
                            checked = s.sounds,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(sounds = v) } },
                            icon = "volume"
                        )
                        ToggleRow(
                            "Esqueleto na tela",
                            "Mostra os pontos do corpo sobre a imagem",
                            checked = s.skeleton,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(skeleton = v) } },
                            icon = "grid"
                        )
                    }
                }
            }

            /* ---- volume da voz ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Volume da voz")
                /* os valores gravados continuam baixo/medio/alto; só o rótulo é acentuado */
                PfSegment(
                    listOf("baixo" to "Baixo", "medio" to "Médio", "alto" to "Alto"),
                    selected = s.voiceVolume,
                    onSelect = { v ->
                        app.feedback.tap()
                        app.patchSettings { it.copy(voiceVolume = v) }
                        app.feedback.say("Volume $v", p = 3, always = true)
                    }
                )
                Btn(
                    "Testar voz",
                    onClick = {
                        app.feedback.tap()
                        if (!app.settings.voice) app.toast = "Ative a voz para testar."
                        else {
                            app.feedback.say(
                                "Costas retas. Controle a descida. Oito repetições.",
                                p = 3, always = true
                            )
                            app.feedback.tone(660.0, 90)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Ghost,
                    icon = "volume"
                )
            }

            /* ---- notificações ---- */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PfEyebrow("Notificações")
                Card(padding = 4.dp) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        ToggleRow(
                            "Lembretes de treino",
                            "Avisos para manter sua sequência",
                            checked = s.notifications,
                            onChange = { v -> app.feedback.tap(); app.patchSettings { it.copy(notifications = v) } },
                            icon = "bell"
                        )
                    }
                }
                Muted("Os lembretes são locais e só aparecem com sua permissão. Nada é enviado para servidores.")
            }

            /* ---- unidade de peso ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Unidade de peso")
                PfSegment(
                    listOf("kg" to "Quilos (kg)", "lb" to "Libras (lb)"),
                    selected = p.unit,
                    onSelect = { u ->
                        app.feedback.tap()
                        app.repo.patchProfile(p.copy(unit = u))
                        app.touch()
                    }
                )
            }

            /* ---- atalhos ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfRow(
                    "crown", "Assinatura",
                    if (app.repo.isPro()) "TREINE AI PRO" else "Plano gratuito"
                ) { app.feedback.tap(); app.go(Route.Subscription) }
                PfRow("shield", "Privacidade e conta", "Câmera, dados e exclusão") {
                    app.feedback.tap(); app.go(Route.Privacy)
                }
            }

            /* ---- sobre ---- */
            Card {
                PfEyebrow("Sobre")
                Spacer(Modifier.height(10.dp))
                PfKeyValue("Versão", "MVP 1.0")
                Spacer(Modifier.height(8.dp))
                PfKeyValue(
                    "Análise de pose",
                    app.motion.state.value.providerLabel.ifEmpty { "Simulada / sob demanda" }
                )
                Spacer(Modifier.height(10.dp))
                Muted(app.catalog.d.disclaimer)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ============================================================
   PRIVACIDADE E DADOS
   ============================================================ */

@Composable
fun PrivacyScreen(app: AppState) {
    val rev = app.revision
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Privacidade e dados",
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(PfBlockGap)
        ) {
            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon("camera", size = 18.dp, tint = TA.flame)
                    Text(
                        "Como usamos a câmera",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TA.cream
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "A câmera é usada apenas durante a análise de um exercício.",
                        "O processamento da pose acontece no seu dispositivo.",
                        "Nenhum vídeo é gravado, armazenado ou enviado para servidores.",
                        "Somente ângulos e métricas do movimento são salvos no histórico.",
                        "Seus vídeos nunca são usados para treinar modelos."
                    ).forEach { PfCheck(it) }
                }
            }

            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon("shield", size = 18.dp, tint = TA.flame)
                    Text(
                        "Onde ficam seus dados",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TA.cream
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nesta versão, conta, perfil e histórico ficam salvos apenas neste dispositivo. Nada é sincronizado com a nuvem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TA.cream2
                )
            }

            /* Sem provedor de arquivos configurado, a exportação vai para a área de
               transferência: o JSON é o mesmo do `exportAll` da web. */
            PfRow("chart", "Exportar meus dados", "Baixa um arquivo JSON com tudo") {
                app.feedback.tap()
                clipboard.setText(AnnotatedString(app.repo.exportAll()))
                app.toast = "Dados copiados para a área de transferência."
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PfEyebrow("Zona de risco", TA.bad)
                Btn(
                    "Apagar histórico de treinos",
                    onClick = { app.feedback.tap(); confirmWipe = true },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Danger,
                    icon = "trash"
                )
                Btn(
                    "Excluir conta e todos os dados",
                    onClick = { app.feedback.tap(); confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Danger,
                    icon = "trash"
                )
            }

            Muted(app.catalog.d.disclaimer, Modifier.fillMaxWidth(), align = TextAlign.Center)

            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmWipe) {
        PfConfirm(
            title = "Apagar histórico?",
            text = "Todos os treinos registrados serão removidos. XP e conquistas são mantidos.",
            ok = "Apagar",
            onOk = {
                confirmWipe = false
                app.repo.update { it.copy(workouts = emptyList()) }
                app.touch()
                app.toast = "Histórico apagado."
            },
            onDismiss = { confirmWipe = false }
        )
    }

    if (confirmDelete) {
        PfConfirm(
            title = "Excluir conta?",
            text = "Sua conta, perfil e todo o histórico serão apagados permanentemente — no aparelho e na nuvem.",
            ok = "Excluir tudo",
            onOk = {
                confirmDelete = false
                scope.launch {
                    app.deleteAccount()
                        .onSuccess { app.toast = "Conta excluída." }
                        .onFailure { app.toast = it.message }
                    app.touch()
                    app.resetTo(Route.Auth)
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/* ============================================================
   PLANOS
   ============================================================ */

/** Os mesmos sete benefícios do `BENEFITS` da web, na mesma ordem. */
private val PF_BENEFITS = listOf(
    Triple("grid", "Biblioteca completa", "exercícios, todos com análise"),
    Triple("target", "Análise de movimento avançada", "Todas as regras de execução ativas"),
    Triple("volume", "Feedback por voz e vibração", "Correções no momento exato"),
    Triple("chart", "Histórico e evolução completos", "Sem limite de dias"),
    Triple("bolt", "TREINE AI Insight", "Padrões da sua execução ao longo do tempo"),
    Triple("refresh", "Análises ilimitadas", "Sem limite diário"),
    Triple("trophy", "Conquistas exclusivas", "Metas e recordes avançados")
)

@Composable
fun PaywallScreen(app: AppState, reason: String) {
    val rev = app.revision
    val cfg = app.catalog.d.config
    val offer = app.repo.offer()
    /* a estratégia do catálogo decide se a compra abre com teste gratuito */
    val trial = cfg.offerStrategy == "trial7"
    var plan by remember { mutableStateOf(cfg.defaultPlan) }

    val close = {
        app.feedback.tap()
        if (app.canGoBack) app.back() else app.tab(Route.Home)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        /* a web usa só o "X" à direita nesta tela: não há título nem voltar */
        TopBar(
            "",
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp),
            action = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(TA.rMd)
                        .clickable(onClick = close),
                    contentAlignment = Alignment.Center
                ) { Icon("close", size = 21.dp, tint = TA.cream) }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Wordmark(markSize = 26.dp, textSize = 15.sp)
                    Chip("PRO", selected = true)
                }
                Text(
                    "Eleve seu treino.",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                    color = TA.cream
                )
                Text(
                    "Tenha sua própria IA acompanhando cada repetição.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TA.cream2
                )
                if (reason.isNotEmpty()) {
                    Card(accent = true, padding = 12.dp) {
                        Text(
                            /* "intro" é o motivo genérico da primeira visita */
                            if (reason == "intro")
                                "Comece com 7 dias de acesso completo à análise da IA."
                            else reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = TA.cream
                        )
                    }
                }
            }

            /* ---- benefícios ---- */
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PF_BENEFITS.forEach { (icon, title, desc) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(TA.rPill)
                                .background(TA.flame.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(icon, size = 17.dp, tint = TA.flame) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
                                color = TA.cream
                            )
                            /* o primeiro item conta quantos exercícios existem */
                            Muted(
                                if (icon == "grid") "${app.catalog.d.exercises.size} $desc" else desc
                            )
                        }
                    }
                }
            }

            /* ---- planos ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cfg.plans.values.forEach { pl ->
                    Card(
                        onClick = { app.feedback.tap(); plan = pl.id },
                        accent = plan == pl.id,
                        padding = 16.dp
                    ) {
                        pl.save?.let {
                            Chip("Economize $it", selected = true)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    pl.label,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp),
                                    color = TA.cream
                                )
                                Muted(pl.billed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    pfMoney(cfg.symbol, pl.price),
                                    style = NumberStyle.copy(fontSize = 22.sp),
                                    color = TA.cream
                                )
                                Muted("/${pl.per}")
                            }
                        }
                    }
                }
            }

            /* ---- compra ---- */
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(
                    offer.cta,
                    onClick = {
                        app.feedback.tap()
                        app.repo.subscribe(plan, trial = trial)
                        app.feedback.success()
                        app.toast =
                            if (trial) "Teste PRO de 7 dias ativado." else "TREINE AI PRO ativado."
                        app.touch()
                        app.replace(Route.Subscription)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    big = true
                )
                Muted(offer.note, Modifier.fillMaxWidth(), align = TextAlign.Center)
                Btn(
                    "Continuar com a versão gratuita",
                    onClick = close,
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Ghost
                )
            }

            /* ---- o que já vem no plano gratuito ---- */
            Card {
                PfEyebrow("O plano gratuito inclui")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "${app.catalog.freeCount} exercícios",
                        "${cfg.free.analysesPerDay} análises por dia",
                        "Contagem de repetições e score",
                        "Sequência, XP e conquistas",
                        "Histórico de ${cfg.free.historyDays} dias"
                    ).forEach { PfCheck(it) }
                }
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}

/* ============================================================
   ASSINATURA
   ============================================================ */

@Composable
fun SubscriptionScreen(app: AppState) {
    val rev = app.revision
    val cfg = app.catalog.d.config
    val sub = app.user.sub
    val pro = app.repo.isPro()
    val plan = sub.planId?.let { cfg.plans[it] }
    var confirmCancel by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Assinatura",
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(PfBlockGap)
        ) {
            /* ---- plano atual ---- */
            Card(accent = pro, padding = 18.dp) {
                PfEyebrow("Plano atual", if (pro) TA.cream2 else TA.cream3)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (pro) "TREINE AI PRO" else "TREINE AI FREE",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp),
                    color = TA.cream
                )
                Spacer(Modifier.height(10.dp))
                if (pro) {
                    val left = app.repo.trialDaysLeft()
                    Text(
                        (if (app.repo.isTrial())
                            "Teste gratuito · $left ${if (left == 1) "dia restante" else "dias restantes"}"
                        else "Assinatura ativa") + (plan?.let { " · ${it.label}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = TA.cream
                    )
                } else {
                    Text(
                        "Recursos básicos de análise e gamificação.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TA.cream2
                    )
                }
            }

            /* ---- detalhes da cobrança ---- */
            if (pro) {
                Card {
                    PfKeyValue(
                        "Plano",
                        plan?.let { "${it.label} · ${pfMoney(cfg.symbol, it.price)}" } ?: "—"
                    )
                    Spacer(Modifier.height(10.dp))
                    PfKeyValue("Início", pfFullDate(sub.startedAt))
                    Spacer(Modifier.height(10.dp))
                    PfKeyValue(
                        if (app.repo.isTrial()) "Primeira cobrança" else "Próxima renovação",
                        pfFullDate(sub.renewAt)
                    )
                    Spacer(Modifier.height(10.dp))
                    PfKeyValue(
                        "Processador",
                        if (cfg.billing.provider == "none") "Simulado (MVP)" else cfg.billing.provider
                    )
                }
            }

            /* ---- uso do dia ---- */
            Card {
                PfEyebrow("Uso hoje")
                Spacer(Modifier.height(10.dp))
                PfKeyValue(
                    "Análises com IA",
                    if (pro) "ilimitadas" else "${app.repo.analysesToday()} de ${cfg.free.analysesPerDay}"
                )
                Spacer(Modifier.height(10.dp))
                PfKeyValue(
                    "Biblioteca",
                    "${if (pro) app.catalog.d.exercises.size else app.catalog.freeCount} de ${app.catalog.d.exercises.size}"
                )
            }

            if (pro) {
                Btn(
                    "Cancelar assinatura",
                    onClick = { app.feedback.tap(); confirmCancel = true },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Danger
                )
                Muted(
                    "Ao cancelar você mantém o acesso até ${pfFullDate(sub.renewAt)}. Neste MVP o cancelamento é imediato.",
                    Modifier.fillMaxWidth(),
                    align = TextAlign.Center
                )
            } else {
                Btn(
                    "Conhecer o TREINE AI PRO",
                    onClick = { app.feedback.tap(); app.go(Route.Paywall("")) },
                    modifier = Modifier.fillMaxWidth(),
                    big = true
                )
            }

            Card {
                PfEyebrow("Integração de cobrança")
                Spacer(Modifier.height(8.dp))
                Muted("A arquitetura já expõe adaptadores para Google Play Billing, Apple In-App Purchase e Stripe. Neste MVP a compra é simulada localmente — nenhum pagamento é processado.")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmCancel) {
        PfConfirm(
            title = "Cancelar assinatura?",
            text = "Você volta para o plano gratuito e perde a análise avançada.",
            ok = "Cancelar assinatura",
            onOk = {
                confirmCancel = false
                app.repo.cancelSubscription()
                app.touch()
                app.toast = "Assinatura cancelada."
            },
            onDismiss = { confirmCancel = false }
        )
    }
}
