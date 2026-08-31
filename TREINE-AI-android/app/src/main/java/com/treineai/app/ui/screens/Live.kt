package com.treineai.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.data.Exercise
import com.treineai.app.data.ExerciseSession
import com.treineai.app.motion.Issue
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Btn
import com.treineai.app.ui.CamStatus
import com.treineai.app.ui.CameraPane
import com.treineai.app.ui.FitMode
import com.treineai.app.ui.Icon
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.ProviderBadge
import com.treineai.app.ui.Route
import com.treineai.app.ui.ScoreRing
import com.treineai.app.ui.SessionPlan
import com.treineai.app.ui.TA
import com.treineai.app.ui.drawSkeleton
import com.treineai.app.ui.drawStudio
import kotlinx.coroutines.delay

/* ============================================================
   MODO FOCO — análise em tempo real.

   Tudo o que aparece aqui também é FALADO: a contagem, as
   correções, a meta atingida e o resumo. A tela é o canal
   secundário; quem está treinando não consegue lê-la.
   ============================================================ */

/** Articulações destacadas no esqueleto, conforme a articulação motora. */
private fun hotJoints(ex: Exercise): List<Int> = when (ex.rep.joint) {
    "knee", "kneeMin", "kneeMax" -> listOf(25, 26)
    "elbow" -> listOf(13, 14)
    "hip", "hipMin", "hipMax", "trunk", "hipAbd" -> listOf(23, 24)
    "shoulder", "shrug" -> listOf(11, 12)
    "ankle" -> listOf(27, 28)
    else -> emptyList()
}

private data class RepMark(val i: Int, val valid: Boolean)

@Composable
fun LiveScreen(app: AppState, plan: SessionPlan) {
    val ex = app.catalog.exercise(plan.current) ?: return
    val motion = app.motion
    val state by motion.state.collectAsState()
    val isHold = ex.hold

    var camStatus by remember { mutableStateOf(CamStatus.Pedindo) }
    var camAspect by remember { mutableStateOf(3f / 4f) }
    var paused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var countdown by remember { mutableIntStateOf(-1) }

    val trace = remember { mutableStateListOf<Float>() }
    val marks = remember { mutableStateListOf<RepMark>() }

    val holdTarget = 40

    fun showCoach(msg: String, level: Int) { coach = msg to level }

    /* Encerra o exercício e leva ao resultado. */
    fun finish() {
        if (finished) return
        finished = true
        app.feedback.silence()
        val sum = motion.summary()
        motion.stop()
        if (sum != null) {
            app.sessionResults.add(
                ExerciseSession(
                    exId = ex.id, name = ex.name, group = ex.group,
                    reps = sum.reps, validReps = sum.validReps, invalid = sum.invalid,
                    score = sum.score, best = sum.best, duration = sum.duration,
                    avgDepth = sum.avgDepth, avgTempo = sum.avgTempo,
                    errors = sum.errors, mainError = sum.mainError
                )
            )
        }
        app.replace(Route.ExResult(plan))
    }

    /* eventos do serviço de análise */
    DisposableEffect(plan.current) {
        motion.onRep = { rec ->
            marks.add(RepMark(trace.lastIndex.coerceAtLeast(0), rec.valid))
            app.feedback.rep(motion.state.value.reps, rec.valid)
        }
        motion.onFeedback = { issue: Issue ->
            showCoach(issue.msg, issue.level)
            app.feedback.coach(issue)
        }
        motion.onTarget = {
            showCoach("Meta atingida.", 0)
            app.feedback.say(
                "Meta atingida. Diga finalizar quando quiser parar.",
                p = 2, always = true, id = "target", gap = 20_000
            )
        }
        onDispose {
            motion.onRep = null; motion.onFeedback = null; motion.onTarget = null
        }
    }

    /* comandos de voz durante o treino */
    DisposableEffect(Unit) {
        app.voice.onCommand = { cmd, _ ->
            when (cmd) {
                "pause" -> if (!paused) { paused = true; motion.pause() }
                "resume" -> if (paused) { paused = false; motion.resume() }
                "finish", "next", "stopall" -> finish()
                "status" -> {
                    val s = motion.state.value
                    val falta = (plan.targetReps - s.reps).coerceAtLeast(0)
                    app.feedback.say(
                        "${s.reps} repetições, ${s.validReps} válidas. Qualidade ${s.quality}" +
                            if (falta > 0) ". Faltam $falta" else ". Meta atingida",
                        p = 2, always = true
                    )
                }
                "back" -> confirmQuit = true
                "mute" -> { app.patchSettings { it.copy(voice = false) }; app.feedback.silence() }
                "unmute" -> {
                    app.patchSettings { it.copy(voice = true) }
                    app.feedback.say("Voz ligada.", p = 2, always = true)
                }
            }
        }
        if (app.settings.voiceCommands) app.voice.resumeListening()
        onDispose { app.voice.onCommand = null }
    }

    /* O pré-treino já contou até "vai": aqui só retomamos a análise. */
    LaunchedEffect(Unit) {
        motion.pause()
        countdown = -1
        motion.resume()
        app.repo.consumeAnalysis()
        app.feedback.say(
            "${ex.name}. Meta: ${plan.targetReps} repetições.",
            p = 2, always = true
        )
    }

    /* a mensagem do treinador some sozinha, como na versão web */
    LaunchedEffect(coach) {
        val c = coach ?: return@LaunchedEffect
        delay(if (c.second >= 3) 3600 else 2600)
        coach = null
    }

    /* isometria: encerra sozinho ao atingir o tempo alvo */
    LaunchedEffect(state.holdSeconds) {
        if (isHold && state.holdSeconds >= holdTarget && !finished) finish()
    }

    /* traço da amplitude ao longo do tempo */
    LaunchedEffect(state.progress) {
        if (paused || isHold) return@LaunchedEffect
        trace.add(state.progress.toFloat())
        if (trace.size > 160) {
            trace.removeAt(0)
            val shifted = marks.map { RepMark(it.i - 1, it.valid) }.filter { it.i >= 0 }
            marks.clear(); marks.addAll(shifted)
        }
    }

    val usingCamera = camStatus == CamStatus.Ativa && state.providerId != "simulation"

    Box(Modifier.fillMaxSize().background(TA.ink0)) {

        CameraPane(
            modifier = Modifier.fillMaxSize(),
            onFrame = { bmp, light, ts ->
                camAspect = bmp.width.toFloat() / bmp.height
                motion.onFrame(bmp, light, ts)
            },
            onHandle = { h -> camStatus = h.status }
        )

        Canvas(Modifier.fillMaxSize()) {
            if (!usingCamera) drawStudio()
            if (app.settings.skeleton) {
                drawSkeleton(
                    state.landmarks,
                    srcAspect = if (usingCamera) camAspect else 1f,
                    mode = if (usingCamera) FitMode.Cover else FitMode.Contain,
                    mirror = usingCamera,
                    silhouette = !usingCamera,
                    hot = hotJoints(ex)
                )
            }
        }

        /* ---------------- HUD ---------------- */
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.size(42.dp).clip(TA.rMd)
                        .background(TA.ink0.copy(alpha = .6f))
                        .clickable { confirmQuit = true },
                    contentAlignment = Alignment.Center
                ) { Icon("close", size = 21.dp, tint = TA.cream) }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderBadge(if (state.providerId == "simulation") "MODO DEMO" else "IA ATIVA")
                    if (app.settings.voiceCommands && app.voice.listening) ProviderBadge("OUVINDO")
                    Box(
                        Modifier.size(42.dp).clip(TA.rMd)
                            .background(TA.ink0.copy(alpha = .6f))
                            .clickable {
                                app.feedback.tap()
                                paused = !paused
                                if (paused) motion.pause() else motion.resume()
                                showCoach(if (paused) "Treino pausado." else "Retomando.", 1)
                                app.feedback.say(
                                    if (paused) "Pausado. Diga continuar para retomar." else "Continuando.",
                                    p = 2, always = true
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(if (paused) "play" else "pause", size = 21.dp, tint = TA.cream) }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                /* correção do treinador — some sozinha */
                coach?.let { (msg, level) ->
                    Row(
                        Modifier.fillMaxWidth().clip(TA.rMd)
                            .background(TA.ink0.copy(alpha = .82f))
                            .border(
                                1.dp,
                                when {
                                    level == 0 -> TA.good
                                    level >= 3 -> TA.bad
                                    else -> TA.warn
                                },
                                TA.rMd
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (level == 0) "check" else if (level >= 3) "alert" else "info",
                            size = 19.dp,
                            tint = if (level == 0) TA.good else if (level >= 3) TA.bad else TA.warn
                        )
                        Text(msg, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = TA.cream)
                    }
                }

                /* painel: contagem e qualidade */
                Column(
                    Modifier.fillMaxWidth().clip(TA.rLg)
                        .background(TA.ink0.copy(alpha = .82f))
                        .border(1.dp, TA.line, TA.rLg)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (isHold) "TEMPO" else "REPETIÇÕES",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = TA.cream3
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (isHold) "${state.holdSeconds.toInt()}s"
                                    else state.reps.toString().padStart(2, '0'),
                                    style = NumberStyle.copy(fontSize = 46.sp), color = TA.cream
                                )
                                if (!isHold) {
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        "/ ${plan.targetReps}",
                                        style = NumberStyle.copy(fontSize = 17.sp), color = TA.cream3,
                                        modifier = Modifier.padding(bottom = 7.dp)
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "QUALIDADE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = TA.cream3
                            )
                            ScoreRing(state.quality, size = 74.dp)
                        }
                    }

                    /* traço da amplitude: um pico por repetição */
                    if (!isHold) {
                        Spacer(Modifier.height(12.dp))
                        Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                            if (trace.size < 2) return@Canvas
                            val stepX = size.width / 160f
                            listOf(0f, .5f, 1f).forEach { v ->
                                val y = size.height - 6 - v * (size.height - 12)
                                drawLine(TA.cream.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), 1f)
                            }
                            val n = trace.size
                            for (i in 0 until n - 1) {
                                fun pt(k: Int) = Offset(
                                    size.width - (n - 1 - k) * stepX,
                                    size.height - 6 - trace[k].coerceIn(-.15f, 1.15f) * (size.height - 12)
                                )
                                drawLine(TA.flame, pt(i), pt(i + 1), 2.6f, StrokeCap.Round)
                            }
                            marks.forEach { m ->
                                val x = size.width - (n - 1 - m.i) * stepX
                                if (x >= 0) drawCircle(
                                    if (m.valid) TA.good else TA.warn,
                                    radius = 3.4f, center = Offset(x, 8f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ex.name, style = NumberStyle.copy(fontSize = 12.sp), color = TA.cream3)
                        Text(
                            "${state.validReps} válida${if (state.validReps == 1) "" else "s"}",
                            style = NumberStyle.copy(fontSize = 12.sp), color = TA.cream3
                        )
                    }
                }

                /* progresso do treino inteiro */
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    plan.exerciseIds.forEachIndexed { i, _ ->
                        Box(
                            Modifier.weight(1f).height(3.dp).clip(TA.rPill)
                                .background(
                                    when {
                                        i < plan.index -> TA.cream2
                                        i == plan.index -> TA.flame
                                        else -> TA.ink3
                                    }
                                )
                        )
                    }
                }

                Btn(
                    "Finalizar exercício",
                    onClick = {
                        if (state.reps == 0 && !isHold) confirmEmpty = true
                        else { app.feedback.tap(); finish() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    big = true
                )
            }
        }

        if (countdown >= 0) {
            Box(
                Modifier.fillMaxSize().background(TA.ink0.copy(alpha = .82f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (countdown == 0) "VAI" else "$countdown",
                    style = NumberStyle.copy(fontSize = 92.sp), color = TA.flame
                )
            }
        }
    }

    if (confirmQuit) {
        LiveDialog(
            title = "Encerrar treino?",
            text = "O progresso deste treino não será salvo.",
            ok = "Encerrar",
            onOk = { confirmQuit = false; app.sessionResults.clear(); quitWorkout(app) },
            onCancel = { confirmQuit = false }
        )
    }

    if (confirmEmpty) {
        LiveDialog(
            title = "Nenhuma repetição registrada",
            text = "Quer encerrar mesmo assim? Este exercício não entrará na avaliação.",
            ok = "Encerrar",
            onOk = { confirmEmpty = false; finish() },
            onCancel = { confirmEmpty = false }
        )
    }
}

@Composable
private fun LiveDialog(
    title: String, text: String, ok: String,
    onOk: () -> Unit, onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TA.ink1,
        shape = TA.rLg,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = TA.cream) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = TA.cream2) },
        confirmButton = { TextButton(onClick = onOk) { Text(ok, color = TA.bad) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = TA.cream2) } }
    )
}
