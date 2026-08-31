package com.treineai.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Btn
import com.treineai.app.ui.BtnKind
import com.treineai.app.ui.CamStatus
import com.treineai.app.ui.CameraPane
import com.treineai.app.ui.Card
import com.treineai.app.ui.Chip
import com.treineai.app.ui.FitMode
import com.treineai.app.ui.Icon
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.Note
import com.treineai.app.ui.ProviderBadge
import com.treineai.app.ui.Route
import com.treineai.app.ui.SessionPlan
import com.treineai.app.ui.TA
import com.treineai.app.ui.drawSkeleton
import com.treineai.app.ui.drawStudio
import kotlinx.coroutines.delay

/* ============================================================
   PRÉ-TREINO — posicionamento guiado por voz.

   Esta é a tela que resolve o problema real relatado: a pessoa
   apoia o celular, se afasta, e é conduzida por instruções FALADAS
   até a posição certa. Quando fica correto, o treino começa
   sozinho — não é preciso voltar até o aparelho para tocar em nada.

   Três garantias:
     · toda instrução é falada, não só escrita;
     · segurando a posição por 1,2 s, começa automaticamente;
     · se em 18 s a verificação não fechar, libera o início manual
       em vez de deixar a pessoa presa.
   ============================================================ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PretrainScreen(app: AppState, plan: SessionPlan) {
    val ex = app.catalog.exercise(plan.current) ?: return
    val motion = app.motion
    val state by motion.state.collectAsState()

    var camStatus by remember { mutableStateOf(CamStatus.Pedindo) }
    var camAspect by remember { mutableStateOf(3f / 4f) }
    var firstFrameAt by remember { mutableStateOf(0L) }
    var lastSpokeAt by remember { mutableStateOf(0L) }
    var lastHint by remember { mutableStateOf("") }
    var override by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(-1) }
    var confirmQuit by remember { mutableStateOf(false) }

    val setup = state.setup
    val usingCamera = camStatus == CamStatus.Ativa && state.providerId != "simulation"

    /* liga o serviço de análise e avisa, em voz alta, o que fazer */
    DisposableEffect(plan.current) {
        motion.attach(ex, plan.targetReps, forceSimulation = false)
        motion.start()
        motion.pause()
        app.feedback.say(
            "Apoie o celular e afaste-se. Eu aviso quando você estiver na posição.",
            p = 2, always = true
        )
        onDispose { }
    }

    /* comandos de voz: começar, sair, pular, calar */
    DisposableEffect(Unit) {
        app.voice.onCommand = { cmd, _ ->
            when (cmd) {
                "start" -> if (motion.state.value.setup.ready || override) starting = true
                "back", "stopall" -> confirmQuit = true
                "next" -> { app.feedback.silence(); advance(app, plan) }
                "mute" -> app.patchSettings { it.copy(voice = false) }
                "unmute" -> {
                    app.patchSettings { it.copy(voice = true) }
                    app.feedback.say("Voz ligada.", p = 2, always = true)
                }
            }
        }
        app.syncVoiceSettings()
        onDispose { app.voice.onCommand = null }
    }

    /* Instrução falada: o mesmo aviso se repete com folga de 3,8 s,
       mas uma instrução NOVA é falada na hora — é o que faz a pessoa
       corrigir a posição sem olhar para a tela. */
    LaunchedEffect(setup.hint, setup.ready, starting) {
        if (starting) return@LaunchedEffect
        val t = System.currentTimeMillis()
        if (setup.hint != lastHint) { lastHint = setup.hint; lastSpokeAt = 0 }
        if (!setup.ready && t - lastSpokeAt > 3800) {
            lastSpokeAt = t
            app.feedback.say(setup.msg, p = 2, id = "setup", gap = 1500)
        }
    }

    /* Início automático: a posição precisa se manter por 1,2 s. */
    LaunchedEffect(setup.ready) {
        if (!setup.ready || !app.settings.autoStart || starting) return@LaunchedEffect
        delay(1200)
        if (motion.state.value.setup.ready && !starting) starting = true
    }

    /* Válvula de escape: 18 s sem confirmar o enquadramento libera
       o início manual, em vez de travar quem está tentando treinar. */
    LaunchedEffect(firstFrameAt) {
        if (firstFrameAt == 0L) return@LaunchedEffect
        delay(18_000)
        /* lê o estado vivo: o `setup` capturado aqui é o do primeiro quadro */
        if (!motion.state.value.setup.ready && !override) {
            override = true
            app.feedback.say(
                "Não consegui confirmar seu enquadramento. Toque em começar quando estiver pronto.",
                p = 3, always = true
            )
        }
    }

    /* Contagem regressiva e entrega para o modo foco. */
    LaunchedEffect(starting) {
        if (!starting) return@LaunchedEffect
        app.feedback.silence()
        app.feedback.say("Posição correta. Preparar.", p = 3, always = true)
        val total = if (app.settings.autoStart) 5 else 3
        for (n in total downTo 1) {
            countdown = n
            app.feedback.countdown(n)
            delay(1000)
        }
        countdown = 0
        app.feedback.countdown(0)
        delay(700)
        app.replace(Route.Live(plan))
    }

    Box(Modifier.fillMaxSize().background(TA.ink0)) {
        Column(Modifier.fillMaxSize()) {

            /* cabeçalho: sair, nome do exercício, atalho do microfone */
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(TA.rMd).clickable { confirmQuit = true },
                    contentAlignment = Alignment.Center
                ) { Icon("close", size = 21.dp, tint = TA.cream) }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.headlineSmall, color = TA.cream)
                    Text(
                        "Exercício ${plan.position} · meta ${plan.targetReps} reps",
                        style = NumberStyle.copy(fontSize = 12.sp), color = TA.cream3
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(TA.rMd).clickable {
                        app.feedback.tap()
                        app.patchSettings { it.copy(voiceCommands = !it.voiceCommands) }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (app.settings.voiceCommands) "mic" else "micoff",
                        size = 21.dp,
                        tint = if (app.settings.voiceCommands) TA.flame else TA.cream3
                    )
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = TA.pad),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                /* visor: imagem da câmera (ou estúdio sintético) + esqueleto */
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(TA.rLg).background(TA.ink0).border(1.dp, TA.line, TA.rLg)
                ) {
                    CameraPane(
                        modifier = Modifier.fillMaxSize(),
                        onFrame = { bmp, light, ts ->
                            if (firstFrameAt == 0L) firstFrameAt = System.currentTimeMillis()
                            camAspect = bmp.width.toFloat() / bmp.height
                            motion.onFrame(bmp, light, ts)
                        },
                        onHandle = { h -> camStatus = h.status }
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        if (!usingCamera) drawStudio()
                        drawSkeleton(
                            state.landmarks,
                            srcAspect = if (usingCamera) camAspect else 1f,
                            mode = if (usingCamera) FitMode.Cover else FitMode.Contain,
                            mirror = usingCamera,
                            silhouette = !usingCamera
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopStart).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProviderBadge(
                            when {
                                state.providerId.isEmpty() -> "CARREGANDO IA"
                                state.providerId == "simulation" -> "MODO DEMO"
                                else -> "IA ATIVA"
                            }
                        )
                        if (app.settings.voiceCommands && app.voice.listening) {
                            ProviderBadge("OUVINDO")
                        }
                    }
                }

                if (camStatus == CamStatus.Negada) {
                    Note(
                        "Permissão de câmera negada. Você pode treinar em modo demonstração para conhecer a análise.",
                        tone = TA.warn, icon = "camera"
                    )
                } else if (camStatus == CamStatus.Indisponivel) {
                    Note(
                        "Câmera indisponível neste dispositivo. Você pode treinar em modo demonstração para conhecer a análise.",
                        tone = TA.warn, icon = "camera"
                    )
                }

                /* canal principal: uma instrução grande, também falada */
                Row(
                    Modifier.fillMaxWidth().clip(TA.rMd)
                        .background(TA.ink1)
                        .border(1.dp, if (setup.ready) TA.good else TA.warn, TA.rMd)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (setup.ready) "check" else "info",
                        size = 19.dp,
                        tint = if (setup.ready) TA.good else TA.warn
                    )
                    Text(
                        if (override && !setup.ready)
                            "Enquadramento não confirmado — você pode começar mesmo assim."
                        else setup.msg,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = TA.cream
                    )
                }

                /* os quatro requisitos, na mesma ordem da versão web */
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Corpo", selected = setup.body)
                    Chip("Luz", selected = setup.light)
                    Chip("Distância", selected = setup.distance)
                    Chip("Enquadramento", selected = setup.full)
                }

                Card {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ONDE DEIXAR O CELULAR",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = TA.cream3
                        )
                        Chip(ex.view)
                    }
                    Spacer(Modifier.height(10.dp))
                    val posText = if (ex.view == "frontal")
                        listOf("Celular de frente para você", "Apoiado na altura do quadril", "2 a 3 metros de distância")
                    else
                        listOf("Celular na sua lateral", "Apoiado na altura do quadril", "2 a 3 metros de distância")
                    posText.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon("camera", size = 17.dp, tint = TA.cream3)
                            Text(t, style = MaterialTheme.typography.bodyMedium, color = TA.cream2)
                        }
                    }
                }

                /* convite aos comandos de voz, quando ainda desligados */
                if (!app.settings.voiceCommands && app.voice.available()) {
                    Card(
                        accent = true,
                        onClick = {
                            app.feedback.tap()
                            app.patchSettings { it.copy(voiceCommands = true) }
                            app.feedback.say("Comandos de voz ativos.", p = 2, always = true)
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon("mic", size = 20.dp, tint = TA.flame)
                            Column(Modifier.weight(1f)) {
                                Text("Comandos de voz", style = MaterialTheme.typography.titleSmall, color = TA.cream)
                                Text(
                                    "Diga \"começar\", \"pausar\", \"finalizar\" sem tocar no celular.",
                                    style = MaterialTheme.typography.bodySmall, color = TA.cream3
                                )
                            }
                            Icon("chevron", size = 18.dp, tint = TA.flame)
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Btn(
                    label = when {
                        setup.ready -> "Começar agora"
                        override -> "Começar mesmo assim"
                        firstFrameAt == 0L -> "Verificando..."
                        else -> "Ajuste sua posição"
                    },
                    onClick = { app.feedback.tap(); starting = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = setup.ready || override,
                    big = true
                )
                Btn(
                    "Pular este exercício",
                    onClick = { app.feedback.tap(); app.feedback.silence(); advance(app, plan) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = BtnKind.Ghost
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        /* contagem regressiva por cima de tudo */
        if (countdown >= 0) {
            Box(
                Modifier.fillMaxSize().background(TA.ink0.copy(alpha = .82f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (countdown == 0) "VAI" else "$countdown",
                    style = NumberStyle.copy(fontSize = 92.sp),
                    color = TA.flame
                )
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            containerColor = TA.ink1,
            shape = TA.rLg,
            title = { Text("Encerrar treino?", style = MaterialTheme.typography.headlineSmall, color = TA.cream) },
            text = {
                Text(
                    "O progresso deste treino não será salvo.",
                    style = MaterialTheme.typography.bodyMedium, color = TA.cream2
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmQuit = false; quitWorkout(app) }) {
                    Text("Encerrar", color = TA.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Cancelar", color = TA.cream2) }
            }
        )
    }
}

/* ------------------------------------------------------------
   Utilidades compartilhadas pelo fluxo de treino
   ------------------------------------------------------------ */

internal fun quitWorkout(app: AppState) {
    app.feedback.silence()
    app.voice.suspend()
    app.motion.detach()
    app.resetTo(Route.Home)
}

/** Vai para o próximo exercício do plano, ou encerra o treino. */
internal fun advance(app: AppState, plan: SessionPlan) {
    app.motion.detach()
    if (plan.isLast) quitWorkout(app) else app.replace(Route.Pretrain(plan.next()))
}
