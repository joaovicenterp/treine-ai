package com.treineai.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.R
import com.treineai.app.data.Exercise
import com.treineai.app.data.Workout
import com.treineai.app.motion.ERROR_LABEL
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Bar
import com.treineai.app.ui.Btn
import com.treineai.app.ui.BtnKind
import com.treineai.app.ui.Card
import com.treineai.app.ui.Chip
import com.treineai.app.ui.EmptyState
import com.treineai.app.ui.ExerciseDemo
import com.treineai.app.ui.Field
import com.treineai.app.ui.Icon
import com.treineai.app.ui.Muted
import com.treineai.app.ui.Note
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.Route
import com.treineai.app.ui.SectionTitle
import com.treineai.app.ui.SessionPlan
import com.treineai.app.ui.StatBlock
import com.treineai.app.ui.TA
import com.treineai.app.ui.TopBar
import com.treineai.app.ui.WeekStrip
import java.util.Calendar

/* ============================================================
   TELAS PRINCIPAIS — Início, Treinos, Detalhe do treino,
   Biblioteca e Detalhe do exercício.

   Portadas de `SCREENS.home / workouts / workoutDetail /
   library / exercise` da versão web: mesmos textos, mesma
   hierarquia, mesmos números.
   ============================================================ */

/* ---------------- helpers internos ---------------- */

/** Espaçamento vertical padrão entre blocos, como o `gap-16` da web. */
private val BlockGap = 14.dp

/** A barra inferior é do shell; as abas só reservam a altura dela. */
private val TabBottomPad = TA.navH + 20.dp

/** A saudação da web usa só o primeiro nome, com "atleta" como reserva. */
private fun firstName(name: String): String =
    name.trim().ifEmpty { "atleta" }.split(" ").first()

/**
 * Miniatura + nome + etiquetas — o `exCard` da web.
 * O cadeado e a etiqueta PRO aparecem pelo mesmo critério do
 * `C.Sub.exerciseLocked`: exercício não gratuito e usuário sem PRO.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(app: AppState, ex: Exercise, onClick: () -> Unit) {
    val locked = app.repo.exerciseLocked(ex)
    /* a web sorteia o período de cada demonstração para elas não pulsarem
       em uníssono; aqui o id serve de semente estável entre recomposições */
    val period = remember(ex.id) { 2400 + ex.id.hashCode().mod(700) }

    Card(onClick = { app.feedback.tap(); onClick() }, padding = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp)) {
                ExerciseDemo(ex.pattern, ex.rep, Modifier.fillMaxSize(), periodMs = period)
                if (locked) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clip(TA.rPill)
                            .background(TA.ink0)
                            .border(1.dp, TA.line, TA.rPill),
                        contentAlignment = Alignment.Center
                    ) { Icon("lock", size = 12.dp, tint = TA.flame) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    ex.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp),
                    color = TA.cream
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Chip(app.catalog.groupName(ex.group))
                    Chip(ex.equipment)
                    if (locked) Chip("PRO", selected = true)
                }
            }
            Icon("chevron", size = 18.dp, tint = TA.cream3)
        }
    }
}

/** Faixa de venda do PRO — o `proBanner` da web, com o mesmo texto reserva. */
@Composable
private fun ProBanner(app: AppState, reason: String = "") {
    Card(
        onClick = { app.feedback.tap(); app.go(Route.Paywall(reason)) },
        accent = true
    ) {
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

/** Linha de item com ícone à esquerda — instruções, erros e dicas. */
@Composable
private fun BulletRow(icon: String, tint: Color, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, size = 16.dp, tint = tint, modifier = Modifier.padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TA.cream)
    }
}

/** Rótulo curto em caixa alta, o `eyebrow` da web. */
@Composable
private fun Eyebrow(text: String, tint: Color = TA.cream3) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = tint
    )
}

/* ============================================================
   INÍCIO
   ============================================================ */
@Composable
fun HomeScreen(app: AppState) {
    val rev = app.revision            // assina as mudanças de dados
    val d = app.catalog.d
    val profile = app.user.profile
    val stats = app.repo.stats()
    val lv = app.repo.level()
    val today = app.repo.todayWorkout()
    /* mesma frase do dia da web: índice pelo dia do mês */
    val quote = d.quotes[Calendar.getInstance().get(Calendar.DAY_OF_MONTH) % d.quotes.size]

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        /* cabeçalho: saudação à esquerda, marca à direita */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TA.pad, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Olá, ${firstName(profile.name)} 👋",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp),
                    color = TA.cream
                )
                Muted("Pronto para treinar?")
            }
            Image(
                painter = painterResource(R.drawable.ta_logo),
                contentDescription = "TREINE AI",
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Fit
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(BlockGap)
        ) {
            /* ---- treino sugerido de hoje ---- */
            if (today != null) {
                Card(accent = true, padding = 18.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Eyebrow("Treino de hoje", TA.cream2)
                            Text(
                                today.name.uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                color = TA.cream
                            )
                            Text(
                                today.focus.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = TA.cream2
                            )
                        }
                        Chip("${today.items.size} exercícios")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "~${today.minutes} min · " +
                            today.groups.joinToString(" · ") { app.catalog.groupName(it) },
                        style = NumberStyle.copy(fontSize = 12.5.sp),
                        color = TA.cream2
                    )
                    Spacer(Modifier.height(14.dp))
                    Btn(
                        "Começar treino",
                        onClick = {
                            app.feedback.tap()
                            app.go(Route.WorkoutDetail(today.id))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            /* ---- sequência de dias ---- */
            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon("flame", size = 18.dp, tint = TA.flame)
                        Text(
                            "${stats.streak} ${if (stats.streak == 1) "dia" else "dias"} de sequência",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TA.cream
                        )
                    }
                    Muted("Recorde ${stats.bestStreak}")
                }
                Spacer(Modifier.height(14.dp))
                WeekStrip(app.repo.weekGrid())
            }

            /* ---- números do período ---- */
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock("${stats.workouts}", "Treinos", Modifier.fillMaxWidth())
                }
                Card(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock(
                        if (stats.avgScore > 0) "${stats.avgScore}" else "—",
                        "Score médio",
                        Modifier.fillMaxWidth()
                    )
                }
                Card(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock("${stats.validReps}", "Reps válidas", Modifier.fillMaxWidth())
                }
            }

            /* ---- nível e XP ---- */
            Card(onClick = { app.feedback.tap(); app.tab(Route.Profile) }) {
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
                    Text("${lv.xp} XP", style = NumberStyle.copy(fontSize = 12.sp), color = TA.cream2)
                }
                Spacer(Modifier.height(10.dp))
                Bar(lv.progress.toFloat())
                Spacer(Modifier.height(8.dp))
                Muted(
                    lv.next?.let { "${lv.toNext} XP para ${it.name}" } ?: "Nível máximo alcançado"
                )
            }

            /* ---- plano: teste em andamento ou convite ao PRO ---- */
            if (app.repo.isTrial()) {
                val left = app.repo.trialDaysLeft()
                Card(accent = true) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon("crown", size = 18.dp, tint = TA.flame)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Teste PRO ativo",
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp),
                                color = TA.cream
                            )
                            Muted(
                                "$left ${if (left == 1) "dia restante" else "dias restantes"} · renova automaticamente"
                            )
                        }
                        Btn(
                            "Gerenciar",
                            onClick = { app.feedback.tap(); app.go(Route.Subscription) },
                            kind = BtnKind.Ghost
                        )
                    }
                }
            } else if (!app.repo.isPro()) {
                ProBanner(app, "Análise avançada, feedback por voz e histórico completo.")
            }

            /* ---- atalhos ---- */
            SectionTitle("Acesso rápido")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    Modifier.weight(1f),
                    onClick = { app.feedback.tap(); app.go(Route.Library) },
                    padding = 14.dp
                ) {
                    Icon("grid", size = 18.dp, tint = TA.flame)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Biblioteca",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp),
                        color = TA.cream
                    )
                    Spacer(Modifier.height(4.dp))
                    Muted("${d.exercises.size} exercícios")
                }
                Card(
                    Modifier.weight(1f),
                    onClick = { app.feedback.tap(); app.tab(Route.Progress) },
                    padding = 14.dp
                ) {
                    Icon("chart", size = 18.dp, tint = TA.flame)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Evolução",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp),
                        color = TA.cream
                    )
                    Spacer(Modifier.height(4.dp))
                    Muted(if (stats.workouts > 0) "Últimos treinos" else "Sem dados ainda")
                }
            }

            /* ---- frase do dia ---- */
            Text(
                "“$quote”",
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = TA.cream3,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TabBottomPad))
        }
    }
}

/* ============================================================
   TREINOS
   ============================================================ */
@Composable
fun WorkoutsScreen(app: AppState) {
    val rev = app.revision
    val workouts = app.catalog.d.workouts

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        /* o cabeçalho da aba não tem voltar; a lupa leva à biblioteca */
        TopBar(
            "Treinos",
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp),
            action = {
                Card(
                    onClick = { app.feedback.tap(); app.go(Route.Library) },
                    padding = 9.dp
                ) { Icon("search", size = 19.dp, tint = TA.cream) }
            }
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = TA.pad, end = TA.pad, top = 4.dp, bottom = TabBottomPad
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(workouts, key = { it.id }) { wk ->
                WorkoutRow(app, wk)
            }
        }
    }
}

/** Linha da lista de treinos: letra do treino, nome, foco, grupos e duração. */
@Composable
private fun WorkoutRow(app: AppState, wk: Workout) {
    Card(onClick = { app.feedback.tap(); app.go(Route.WorkoutDetail(wk.id)) }, padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(wk.id, style = MaterialTheme.typography.displaySmall, color = TA.flame)
                Text("${wk.minutes}min", style = NumberStyle.copy(fontSize = 11.sp), color = TA.cream3)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(wk.name, style = MaterialTheme.typography.headlineSmall, color = TA.cream)
                Muted(wk.focus)
                Text(
                    wk.groups.joinToString(" · ") { app.catalog.groupName(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = TA.cream3
                )
                Text(
                    "${wk.items.size} exercícios",
                    style = NumberStyle.copy(fontSize = 11.sp),
                    color = TA.cream3
                )
            }
            Icon("chevron", size = 18.dp, tint = TA.cream3)
        }
    }
}

/* ============================================================
   DETALHE DO TREINO
   ============================================================ */
@Composable
fun WorkoutDetailScreen(app: AppState, workoutId: String) {
    val rev = app.revision
    /* como no `D.workoutById(p.id) || D.WORKOUTS[0]` da web */
    val wk = app.catalog.workout(workoutId) ?: app.catalog.d.workouts.first()
    val lockedCount = wk.items.count { item ->
        app.catalog.exercise(item.ex)?.let { app.repo.exerciseLocked(it) } == true
    }

    /* No plano gratuito os exercícios PRO são pulados: a sessão só leva os liberados. */
    val start = {
        app.feedback.tap()
        val liberados = wk.items.filter { item ->
            app.catalog.exercise(item.ex)?.let { !app.repo.exerciseLocked(it) } == true
        }
        when {
            liberados.isEmpty() ->
                app.go(Route.Paywall("Este treino usa exercícios do plano PRO."))
            else -> {
                val motivo = app.blockReason(liberados.first().ex)
                if (motivo != null) app.go(Route.Paywall(motivo))
                else app.go(
                    Route.Pretrain(
                        SessionPlan(
                            exerciseIds = liberados.map { it.ex },
                            targetReps = liberados.first().reps,
                            workoutId = wk.id,
                            workoutName = wk.name,
                            startedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            wk.name,
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = TA.pad, end = TA.pad, top = 4.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(BlockGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(wk.focus, TA.flame)
                    Text(wk.name, style = MaterialTheme.typography.displayMedium, color = TA.cream)
                    Text(
                        "${wk.items.size} exercícios · ~${wk.minutes} min",
                        style = NumberStyle.copy(fontSize = 13.sp),
                        color = TA.cream2
                    )
                }
            }

            if (lockedCount > 0) {
                item {
                    Note(
                        "$lockedCount ${if (lockedCount == 1) "exercício exige" else "exercícios exigem"} " +
                            "o plano PRO. No plano gratuito eles são pulados automaticamente.",
                        tone = TA.flame,
                        icon = "lock"
                    )
                }
            }

            itemsIndexed(wk.items, key = { i, w -> "$i-${w.ex}" }) { i, item ->
                val ex = app.catalog.exercise(item.ex)
                if (ex != null) {
                    val locked = app.repo.exerciseLocked(ex)
                    Card(
                        onClick = { app.feedback.tap(); app.go(Route.ExerciseDetail(ex.id)) },
                        padding = 12.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseDemo(ex.pattern, ex.rep, Modifier.size(44.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    ex.name,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.5.sp),
                                    color = TA.cream
                                )
                                Text(
                                    "${item.sets} × ${item.reps} · ${ex.equipment}",
                                    style = NumberStyle.copy(fontSize = 11.sp),
                                    color = TA.cream3
                                )
                            }
                            if (locked) Chip("PRO", selected = true)
                            Text(
                                (i + 1).toString().padStart(2, '0'),
                                style = NumberStyle.copy(fontSize = 11.sp),
                                color = TA.cream3
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Começar treino", onClick = start, modifier = Modifier.fillMaxWidth(), big = true)
                    Muted(app.catalog.d.disclaimer, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }
            }
        }
    }
}

/* ============================================================
   BIBLIOTECA
   ============================================================ */
@Composable
fun LibraryScreen(app: AppState) {
    val rev = app.revision
    val all = app.catalog.d.exercises
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("all") }
    var level by remember { mutableStateOf("all") }

    /* Mesma busca da web: nome, grupo ou equipamento. */
    val filtrados = all.filter { ex ->
        (group == "all" || ex.group == group) &&
            (level == "all" || ex.level == level) &&
            (query.isBlank() || run {
                val q = query.trim().lowercase()
                ex.name.lowercase().contains(q) ||
                    app.catalog.groupName(ex.group).lowercase().contains(q) ||
                    ex.equipment.lowercase().contains(q)
            })
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            "Biblioteca",
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )

        Column(
            Modifier.padding(horizontal = TA.pad, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field(
                value = query,
                onValueChange = { query = it },
                label = "Buscar exercício...",
                trailing = { Icon("search", size = 18.dp, tint = TA.cream3) }
            )
            /* filtros em faixas roláveis: grupo muscular e, abaixo, nível */
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("Todos", selected = group == "all", onClick = {
                    app.feedback.tap(); group = "all"
                })
                app.catalog.d.groups.forEach { g ->
                    Chip(g.name, selected = group == g.id, onClick = {
                        app.feedback.tap(); group = g.id
                    })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("Todos", selected = level == "all", onClick = {
                    app.feedback.tap(); level = "all"
                })
                app.catalog.d.levels.forEach { l ->
                    Chip(l, selected = level == l, onClick = { app.feedback.tap(); level = l })
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = TA.pad, end = TA.pad, top = 12.dp, bottom = TabBottomPad
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtrados.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EmptyState("search", "Nada encontrado")
                        Muted(
                            "Tente outro termo ou escolha outro grupo muscular.",
                            align = TextAlign.Center
                        )
                        Btn("Limpar busca", onClick = {
                            app.feedback.tap(); query = ""; group = "all"; level = "all"
                        }, kind = BtnKind.Ghost)
                    }
                }
            } else {
                /* O plano gratuito libera `config.free.libraryLimit` exercícios: a lista
                   continua completa e o que está além do limite aparece com cadeado,
                   pelo mesmo `exerciseLocked` que marca os exercícios não gratuitos. */
                items(filtrados, key = { it.id }) { ex ->
                    ExerciseCard(app, ex) { app.go(Route.ExerciseDetail(ex.id)) }
                }
                item {
                    Muted(
                        "${filtrados.size} de ${all.size} exercícios",
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        align = TextAlign.Center
                    )
                }
            }
        }
    }
}

/* ============================================================
   DETALHE DO EXERCÍCIO
   ============================================================ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(app: AppState, exId: String) {
    val rev = app.revision
    val ex = app.catalog.exercise(exId)

    if (ex == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TA.ink0)
        ) {
            TopBar(
                "Exercício",
                onBack = { app.back() },
                modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
            )
            EmptyState("alert", "Exercício não encontrado", Modifier.padding(horizontal = TA.pad))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TA.pad),
                horizontalArrangement = Arrangement.Center
            ) {
                Btn("Voltar", onClick = { app.feedback.tap(); app.back() }, kind = BtnKind.Ghost)
            }
        }
        return
    }

    val locked = app.repo.exerciseLocked(ex)

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar(
            app.catalog.groupName(ex.group),
            onBack = { app.back() },
            modifier = Modifier.padding(horizontal = TA.pad - 4.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(BlockGap + 4.dp)
        ) {
            ExerciseDemo(
                ex.pattern, ex.rep,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(ex.name, style = MaterialTheme.typography.displaySmall, color = TA.cream)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(ex.equipment)
                    Chip(ex.level)
                    Chip("Câmera ${ex.view}")
                    if (locked) Chip("PRO", selected = true)
                }
            }

            Card {
                Eyebrow("Como executar")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ex.instructions.forEach { BulletRow("check", TA.cream3, it) }
                }
            }

            Card {
                Eyebrow("Erros comuns", TA.warn)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ex.errors.forEach { BulletRow("alert", TA.warn, it) }
                }
            }

            Card {
                Eyebrow("Dicas", TA.good)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ex.tips.forEach { BulletRow("bolt", TA.good, it) }
                }
            }

            if (ex.checks.isNotEmpty()) {
                Card {
                    Eyebrow("O que a IA analisa")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ex.checks.forEach { Chip(ERROR_LABEL[it] ?: it) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (locked) {
                    /* exercício fora do plano gratuito: o caminho é a tela de planos */
                    Btn(
                        "Desbloquear com o PRO",
                        onClick = {
                            app.feedback.tap()
                            app.go(
                                Route.Paywall(
                                    "Este exercício faz parte da biblioteca completa do TREINE AI PRO."
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = "lock"
                    )
                } else {
                    Btn(
                        "Treinar agora",
                        onClick = {
                            app.feedback.tap()
                            /* o limite diário de análises também barra aqui */
                            val motivo = app.blockReason(ex.id)
                            if (motivo != null) app.go(Route.Paywall(motivo))
                            else app.go(
                                Route.Pretrain(
                                    SessionPlan(exerciseIds = listOf(ex.id), targetReps = 12)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        big = true
                    )
                }
                Muted(app.catalog.d.disclaimer, Modifier.fillMaxWidth(), align = TextAlign.Center)
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}
