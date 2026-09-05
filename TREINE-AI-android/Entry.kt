package com.treineai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.ui.AppState
import com.treineai.app.ui.Btn
import com.treineai.app.ui.BtnKind
import com.treineai.app.ui.Card
import com.treineai.app.ui.Chip
import com.treineai.app.ui.ExerciseDemo
import com.treineai.app.ui.Field
import com.treineai.app.ui.Icon
import com.treineai.app.ui.Muted
import com.treineai.app.ui.Note
import com.treineai.app.ui.NumberStyle
import com.treineai.app.ui.Route
import com.treineai.app.ui.TA
import com.treineai.app.ui.ToggleRow
import com.treineai.app.ui.TopBar
import com.treineai.app.ui.Wordmark
import kotlinx.coroutines.delay

/* ============================================================
   ENTRADA — abertura, introdução, autenticação e perfil inicial.
   As mesmas cinco telas de `SCREENS.splash … SCREENS.profileSetup`
   da versão web, com os mesmos textos e a mesma ordem de campos.
   ============================================================ */

/* ---------------- peças de texto da versão web ---------------- */

/** `.eyebrow` — mono, versalete espaçado; `hot` é a variante em chama. */
@Composable
private fun Eyebrow(text: String, hot: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = NumberStyle.copy(fontSize = 10.5.sp, letterSpacing = 1.7.sp),
        color = if (hot) TA.flame else TA.cream3
    )
}

/** `.h1` — 32px de display; mantém as quebras de linha do texto original. */
@Composable
private fun H1(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
        color = TA.cream
    )
}

/** `.body` — 15px em tinta secundária. */
@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = TA.cream2)
}

/** `.btn.link` — texto tocável, sem pílula nem borda. */
@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(TA.rPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = TA.cream2
    )
}

/** `.dots` — o ponto ativo vira um traço, como na web. */
@Composable
private fun Dots(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(if (i == index) 22.dp else 6.dp)
                    .height(6.dp)
                    .clip(TA.rPill)
                    .background(if (i == index) TA.flame else TA.line)
            )
        }
    }
}

/* ============================================================
   SPLASH
   ============================================================ */

@Composable
fun SplashScreen(app: AppState) {
    val rev = app.revision

    /* A pausa existe para a marca aparecer inteira; a decisão de destino
       repete a do `boot()` da web, só que lida do repositório local. */
    LaunchedEffect(Unit) {
        delay(1400)
        /* espera a restauração da sessão do Firebase e a busca do progresso */
        while (!app.ready) delay(50)
        val logged = app.isSignedIn
        app.resetTo(
            when {
                logged && app.user.profileDone -> Route.Home
                logged -> Route.ProfileSetup
                !app.repo.seenIntro -> Route.Onboarding
                else -> Route.Auth
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Wordmark(markSize = 64.dp, textSize = 26.sp)
        Spacer(Modifier.height(22.dp))
        CircularProgressIndicator(Modifier.size(22.dp), color = TA.flame, strokeWidth = 2.5.dp)
    }
}

/* ============================================================
   INTRODUÇÃO
   ============================================================ */

private data class IntroSlide(
    val eyebrow: String,
    val title: String,
    val body: String,
    val pattern: String
)

private val INTRO = listOf(
    IntroSlide(
        "Bem-vindo",
        "Seu treino.\nAgora com IA.",
        "O TREINE AI usa a câmera do seu celular para analisar a execução dos seus exercícios em tempo real.",
        "squat"
    ),
    IntroSlide(
        "Análise",
        "Treine melhor.",
        "A IA acompanha o movimento das suas articulações e identifica possíveis erros de execução repetição por repetição.",
        "row"
    ),
    IntroSlide(
        "Feedback",
        "Receba feedback\nem tempo real.",
        "Correções curtas na tela, por voz e por vibração — no momento exato em que você precisa.",
        "pushup"
    ),
    IntroSlide(
        "Pronto",
        "Comece seu treino.",
        "Escolha um treino, posicione o celular e deixe a IA acompanhar cada repetição.",
        "overheadPress"
    )
)

@Composable
fun OnboardingScreen(app: AppState) {
    val rev = app.revision
    var i by remember { mutableStateOf(0) }
    val slide = INTRO[i]
    val last = i == INTRO.lastIndex

    /* Só chega aqui quem ainda não tem sessão — a splash desvia os demais —,
       então a introdução sempre termina na autenticação. */
    val finish: () -> Unit = {
        app.feedback.tap()
        app.repo.markIntroSeen()
        app.resetTo(Route.Auth)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TA.pad)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark(markSize = 26.dp, textSize = 15.sp)
            if (!last) TextLink("Pular introdução", onClick = finish)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = TA.pad),
            contentAlignment = Alignment.Center
        ) {
            ExerciseDemo(
                slide.pattern,
                null,
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 280.dp)
                    .aspectRatio(1f)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TA.pad)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Dots(INTRO.size, i, Modifier.align(Alignment.CenterHorizontally))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow(slide.eyebrow, hot = true)
                H1(slide.title)
                BodyText(slide.body)
            }
            Btn(
                if (last) "Começar" else "Continuar",
                onClick = { app.feedback.tap(); if (last) finish() else i++ },
                modifier = Modifier.fillMaxWidth(),
                big = true
            )
        }
    }
}

/* ============================================================
   AUTENTICAÇÃO
   ============================================================ */

@Composable
fun AuthScreen(app: AppState) {
    val rev = app.revision
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var keepSigned by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /* Agora o login vai à nuvem (Firebase), então é assíncrono: enquanto
       espera a resposta, o botão fica desabilitado. */
    val submit: () -> Unit = {
        app.feedback.tap()
        err = null
        val mail = email.trim()
        when {
            mail.isEmpty() -> err = "Informe seu e-mail."
            pw.isEmpty() -> err = "Informe sua senha."
            signup && pw != pw2 -> err = "As senhas não coincidem."
            else -> {
                loading = true
                scope.launch {
                    val r =
                        if (signup) app.signUp(name.trim(), mail, pw)
                        else app.signIn(mail, pw)
                    loading = false
                    r.onSuccess {
                        app.syncVoiceSettings()
                        if (!app.user.onboarded) app.repo.update { d -> d.copy(onboarded = true) }
                        app.toast = if (signup) "Conta criada. Bora treinar." else "Bom te ver de novo."
                        app.touch()
                        app.resetTo(if (app.user.profileDone) Route.Home else Route.ProfileSetup)
                    }.onFailure {
                        err = it.message ?: "Não foi possível continuar."
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = TA.pad)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark(markSize = 26.dp, textSize = 15.sp)
            TextLink(
                if (signup) "Já tenho conta" else "Criar conta",
                onClick = { app.feedback.tap(); signup = !signup; err = null }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow(if (signup) "Criar conta" else "Entrar", hot = true)
            H1(if (signup) "Comece a treinar\ncom a IA." else "Bem-vindo\nde volta.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (signup) Field(name, { name = it }, "Nome")
            Field(email, { email = it }, "E-mail", keyboard = KeyboardType.Email)
            Field(
                pw, { pw = it }, "Senha",
                keyboard = KeyboardType.Password,
                password = true,
                revealed = reveal,
                trailing = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable { app.feedback.tap(); reveal = !reveal },
                        contentAlignment = Alignment.Center
                    ) { Icon(if (reveal) "eyeoff" else "eye", size = 19.dp, tint = TA.cream3) }
                }
            )
            if (signup) {
                Field(
                    pw2, { pw2 = it }, "Confirmar senha",
                    keyboard = KeyboardType.Password,
                    password = true
                )
            }

            /* Na criação de conta a sessão já fica gravada pelo repositório;
               o interruptor só muda o comportamento do login. */
            ToggleRow(
                title = "Permanecer conectado",
                checked = keepSigned,
                onChange = { app.feedback.tap(); keepSigned = it }
            )
            if (!signup) {
                TextLink(
                    "Esqueci a senha",
                    onClick = { app.feedback.tap(); app.go(Route.Forgot) },
                    modifier = Modifier.align(Alignment.End)
                )
            }

            err?.let { Note(it, tone = TA.bad, icon = "alert") }

            Btn(
                when {
                    loading && signup -> "Criando conta..."
                    loading -> "Entrando..."
                    signup -> "Criar conta"
                    else -> "Entrar"
                },
                onClick = submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                big = true
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(TA.lineSoft))
            Text("ou", style = MaterialTheme.typography.labelSmall, color = TA.cream3)
            Box(Modifier.weight(1f).height(1.dp).background(TA.lineSoft))
        }

        /* O login com Google exige configuração extra (SHA-1/OAuth); por ora
           avisamos que chega em breve e mantemos e-mail e senha. */
        Btn(
            "Continuar com Google",
            onClick = {
                app.feedback.tap()
                app.toast = app.googleNotReady()
            },
            modifier = Modifier.fillMaxWidth(),
            kind = BtnKind.Ghost,
            icon = "google"
        )

        Muted(
            "Ao continuar você concorda com o uso da câmera apenas durante a análise dos exercícios.\nNenhum vídeo é gravado ou enviado.",
            Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp),
            align = TextAlign.Center
        )
    }
}

/* ============================================================
   RECUPERAR SENHA
   ============================================================ */

@Composable
fun ForgotScreen(app: AppState) {
    val rev = app.revision
    var email by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
    ) {
        TopBar("Recuperar senha", onBack = { app.back() })
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = TA.pad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (sent) {
                BodyText("Enviamos um link de redefinição para $email. Abra o e-mail, defina a nova senha e volte para entrar.")
                Btn(
                    "Voltar para o login",
                    onClick = { app.feedback.tap(); app.replace(Route.Auth) },
                    modifier = Modifier.fillMaxWidth(),
                    big = true
                )
            } else {
                BodyText("Informe o e-mail da sua conta. Enviaremos um link para você criar uma nova senha com segurança.")
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(email, { email = it }, "E-mail", keyboard = KeyboardType.Email)
                    err?.let { Note(it, tone = TA.bad, icon = "alert") }
                    Btn(
                        if (loading) "Enviando..." else "Enviar link de redefinição",
                        onClick = {
                            app.feedback.tap()
                            err = null
                            if (email.trim().isEmpty()) {
                                err = "Informe seu e-mail."
                            } else {
                                loading = true
                                scope.launch {
                                    val r = app.sendPasswordReset(email.trim())
                                    loading = false
                                    r.onSuccess { sent = true }.onFailure { err = it.message }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        big = true
                    )
                }
            }
        }
    }
}

/* ============================================================
   PERFIL INICIAL
   ============================================================ */

@Composable
fun ProfileSetupScreen(app: AppState) {
    val rev = app.revision
    val profile = app.user.profile

    var name by remember { mutableStateOf(profile.name) }
    var age by remember { mutableStateOf(profile.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile.weight?.let { fmtNumber(it) } ?: "") }
    var height by remember { mutableStateOf(profile.height?.let { fmtNumber(it) } ?: "") }
    var goal by remember { mutableStateOf(profile.goal) }
    var level by remember { mutableStateOf(profile.experience.ifEmpty { "Iniciante" }) }
    var freq by remember { mutableStateOf(if (profile.frequency > 0) profile.frequency else 3) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TA.ink0)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = TA.pad)
            .padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Seu perfil", hot = true)
            H1("Vamos calibrar\nseu treino.")
            BodyText("Esses dados personalizam suas metas e a análise de execução. Você pode alterar tudo depois.")
        }

        Field(name, { name = it }, "Nome")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(age, { age = it }, "Idade", Modifier.weight(1f), keyboard = KeyboardType.Number)
            Field(weight, { weight = it }, "Peso (kg)", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            Field(height, { height = it }, "Altura (cm)", Modifier.weight(1f), keyboard = KeyboardType.Number)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Objetivo")
            /* Duas colunas, como o `.picks` da web; a última linha ímpar
               fica com metade vazia para as fichas manterem a largura. */
            app.catalog.d.goals.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { g ->
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
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Nível de experiência")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                app.catalog.d.levels.forEach { l ->
                    Chip(l, selected = level == l, onClick = { app.feedback.tap(); level = l })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Treinos por semana")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 5, 6).forEach { n ->
                    Chip("$n×", selected = freq == n, onClick = { app.feedback.tap(); freq = n })
                }
            }
        }

        err?.let { Note(it, tone = TA.bad, icon = "alert") }

        Btn(
            "Concluir",
            onClick = {
                app.feedback.tap()
                err = null
                when {
                    name.isBlank() -> err = "Informe seu nome."
                    goal.isEmpty() -> err = "Escolha um objetivo."
                    else -> {
                        app.repo.patchProfile(
                            profile.copy(
                                name = name.trim(),
                                age = age.trim().toIntOrNull(),
                                weight = parseNumber(weight),
                                height = parseNumber(height),
                                goal = goal,
                                experience = level,
                                frequency = freq
                            )
                        )
                        app.repo.update { it.copy(profileDone = true) }
                        app.touch()
                        app.resetTo(Route.Home)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            big = true
        )
    }
}

/** O teclado brasileiro entrega vírgula decimal; o campo aceita as duas formas. */
private fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Mostra 72 em vez de 72.0 ao reabrir o formulário. */
private fun fmtNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
