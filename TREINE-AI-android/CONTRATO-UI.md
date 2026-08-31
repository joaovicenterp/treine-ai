# Contrato da camada de interface — TREINE AI nativo

Documento de referência para escrever telas em Compose. **Tudo aqui já existe e compila
conceitualmente; não invente APIs novas nem altere os arquivos listados como "existentes".**

O objetivo é reproduzir a versão web (`/home/claude/treineai/src/ui.js` e `screens.js`)
**exatamente**: mesmos textos em português, mesma hierarquia, mesmos números.

---

## Regras não negociáveis

1. **Nunca redesenhar a logo.** Use `Wordmark(...)` ou `painterResource(R.drawable.ta_logo)`.
2. Cor principal `#F73D14` (`TA.flame`), secundária `#E3E1DC` (`TA.cream`). Nada fora dos tokens.
3. **O app não é autoridade médica.** Nunca escreva "seguro", "perigoso", "lesão", "correto".
   Use "revise sua execução", "ajuste a posição". Copie os textos da versão web literalmente.
4. Comentários em português, explicando **por quê**, não o quê. Sem comentários óbvios.
5. Todo texto visível vem da versão web. Não invente frases novas.

---

## Tokens — `com.treineai.app.ui.TA`

```
TA.ink0 #0A0908   fundo          TA.cream  #E3E1DC  tinta primária
TA.ink1 #131110   superfície     TA.cream2 #A8A39D  tinta secundária
TA.ink2 #1C1918   elevada        TA.cream3 #8B857F  tinta terciária
TA.ink3 #262220   pressionada    TA.flame  #F73D14  acento
TA.line #2E2926   traço          TA.flame2 #FF6A45
TA.lineSoft #211D1B              TA.flameInk #FFF3EF
TA.good #3ECF7B   TA.warn #F5A524   TA.bad #FF4D4D
TA.rSm/rMd/rLg/rXl/rPill  (RoundedCornerShape)
TA.pad = 20.dp    TA.navH = 74.dp
TA.scoreColor(score: Int): Color   // >=85 good, >=65 warn, senão bad
```

Tipografia: `MaterialTheme.typography` já configurada
(`displayLarge/Medium/Small`, `headlineMedium/Small`, `titleMedium/Small`,
`bodyLarge/Medium/Small`, `labelLarge/Medium/Small`).
`NumberStyle` = fonte monoespaçada para números/cronômetros.

## Componentes existentes — `com.treineai.app.ui.*`

```kotlin
enum class BtnKind { Primary, Ghost, Soft, Danger }

Btn(label: String, onClick: () -> Unit, modifier, kind = Primary, icon: String? = null,
    enabled: Boolean = true, big: Boolean = false)

Card(modifier, onClick: (() -> Unit)? = null, accent: Boolean = false,
     padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit)

Chip(text, modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, icon: String? = null)
SectionTitle(text, modifier, action: (@Composable () -> Unit)? = null)
Muted(text, modifier, align: TextAlign? = null)
StatBlock(value: String, label: String, modifier, color = TA.cream)
ScoreRing(score: Int, modifier, size: Dp = 132.dp, caption: String? = null)
Bar(progress: Float, modifier, color = TA.flame, height: Dp = 8.dp)
ToggleRow(title, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit,
          icon: String? = null, enabled: Boolean = true)
Field(value, onValueChange, label, modifier, keyboard: KeyboardType = Text,
      password: Boolean = false, revealed: Boolean = false,
      trailing: (@Composable () -> Unit)? = null, singleLine: Boolean = true)
Note(text, modifier, tone: Color = TA.warn, icon: String = "info")
LineChart(values: List<Int>, modifier, minY: Int = 0, maxY: Int = 100)
BarChart(entries: List<Pair<String, Int>>, modifier)
WeekStrip(days: List<WeekDay>, modifier)
TopBar(title: String, onBack: (() -> Unit)? = null, modifier, action: (@Composable () -> Unit)? = null)
ProviderBadge(label: String, modifier)
EmptyState(icon: String, text: String, modifier)
Icon(name: String, size: Dp = 20.dp, tint: Color = TA.cream, modifier)
Wordmark(modifier, markSize: Dp = 30.dp, textSize: TextUnit = 19.sp)
ExerciseDemo(pattern: String, cfg: RepRange?, modifier, periodMs: Int = 2600)
PoseThumb(pattern: String, cfg: RepRange?, p: Double = .35, modifier)
BottomNav(current: String, onTab: (Route) -> Unit, modifier)
```

Nomes de ícone disponíveis (use só estes):
`home dumbbell chart trophy user back close check play pause camera flame lock bolt star
alert info chevron settings search eye eyeoff up down minus bell shield volume vibrate
logout trash crown target clock calendar refresh filter grid skip mic micoff google`

## Navegação — `com.treineai.app.ui.Route`

```kotlin
Route.Splash, Onboarding, Auth, Forgot, ProfileSetup
Route.Home, Workouts, Progress, Achievements, Profile        // abas
Route.WorkoutDetail(workoutId: String)
Route.Library
Route.ExerciseDetail(exId: String)
Route.Pretrain(plan: SessionPlan)
Route.Live(plan: SessionPlan)
Route.ExResult(plan: SessionPlan)
Route.WorkoutResult(recordId: String)
Route.EditProfile, Settings, Privacy, Subscription
Route.Paywall(reason: String = "")

data class SessionPlan(exerciseIds: List<String>, index: Int = 0, targetReps: Int = 12,
                       workoutId: String? = null, workoutName: String = "", startedAt: Long = 0L) {
    val current: String; val isLast: Boolean; val position: String  // "2/5"
    fun next(): SessionPlan
}
```

## Estado — `com.treineai.app.ui.AppState`

```kotlin
app.catalog: Catalog        app.repo: Repo
app.feedback: Feedback      app.voice: VoiceCommands
app.motion: MotionAnalysisService
app.route: Route            app.canGoBack: Boolean
app.revision: Int           // leia-o na tela para redesenhar quando os dados mudam
app.settings: Settings      app.user: UserData
app.toast: String?          app.celebrating: List<AchievementDef>

app.go(r) / app.replace(r) / app.resetTo(r) / app.back(fallback = Route.Home) / app.tab(r)
app.touch()                                   // força redesenho após alterar dados
app.patchSettings { it.copy(voice = false) }  // já sincroniza voz e TTS
app.checkAchievements()
app.blockReason(exId): String?                // null = liberado; senão, motivo p/ o paywall
```

## Dados — `com.treineai.app.data`

```kotlin
Catalog: d.config, d.groups, d.levels, d.exercises, d.workouts, d.split,
         d.goals, d.xpLevels, d.achievements, d.quotes, d.disclaimer
         exercise(id): Exercise?   exercisesOf(group)   groupName(id)   workout(id)   freeCount

Exercise(id, name, group, equipment, level, pattern, view, rep: RepRange,
         free, hold, checks, instructions, errors, tips)
RepRange(joint, top, bottom)
Workout(id, name, focus, groups, minutes, items: List<WorkoutItem(ex, sets, reps)>)
AppConfig(currency, symbol, plans: Map<String, Plan>, defaultPlan, offerStrategy,
          offers: Map<String, Offer>, trialDays, free: FreeLimits, billing)
Plan(id, label, price: Double, per, billed, save: String?)
Offer(cta, headline, note)
FreeLimits(libraryLimit, analysesPerDay, historyDays, advancedFeedback)
Profile(name, photo, age, weight, height, goal, experience, frequency, unit)
Settings(voice, voiceVolume, audioFirst, autoStart, voiceCommands, haptics,
         notifications, sounds, skeleton)
WorkoutRecord(id, date, ts, workoutId, name, focus, duration, score,
              breakdown: ScoreBreakdown, startedHour, sessions: List<ExerciseSession>, prevScore)
ExerciseSession(exId, name, group, reps, validReps, invalid, score, best, duration,
                avgDepth, avgTempo, errors: Map<String,Int>, mainError)
ScoreBreakdown(tecnica, consistencia, amplitude, controle, total)
AchievementDef(id, icon, name, desc, xp)

Repo: data(): UserData        update { it.copy(...) }
      patchProfile(p)         addWorkout(rec)        visibleWorkouts(): List<WorkoutRecord>
      stats(): Stats          level(): LevelInfo     weekGrid(): List<WeekDay>
      addXp(n): Boolean       checkAchievements()
      isPro() / isTrial() / trialDaysLeft() / subscribe(planId, trial) / cancelSubscription()
      analysesToday() / analysesLeft(): Int?  // null = ilimitado
      consumeAnalysis() / exerciseLocked(ex) / offer(): Offer
      todayWorkout(): Workout?
      account: Account?  signUp/signIn/signOut/resetPassword/deleteAccount/exportAll()
      seenIntro / markIntroSeen()

Stats(workouts, exercises, reps, validReps, minutes, bestExerciseScore, bestWorkoutScore,
      earlyBird, groupsTrained, streak, bestStreak, avgScore)
LevelInfo(level, name, xp, next: XpLevel?, progress: Double, toNext: Int)
WeekDay(label, key, done, today, future)
```

## Textos de diagnóstico — `com.treineai.app.motion`

`ERROR_LABEL: Map<String,String>` e `ERROR_TIP: Map<String,String>` traduzem os códigos de erro
(`depth`, `rom`, `tempo`, `kneeValgus`, …) para a linguagem do usuário. Use-os nos resultados.

---

## Forma de cada tela

```kotlin
@Composable
fun HomeScreen(app: AppState) {
    val rev = app.revision            // assina as mudanças de dados
    Column(Modifier.fillMaxSize().background(TA.ink0)) { … }
}
```

- Rolagem: `Column(Modifier.verticalScroll(rememberScrollState()))` ou `LazyColumn`.
- Padding lateral: `TA.pad` (20.dp).
- Espaçamento vertical entre blocos: `Arrangement.spacedBy(14.dp)`.
- Telas de aba **não** desenham a barra inferior — o shell cuida disso.
- Telas internas começam com `TopBar(titulo, onBack = { app.back() })`.
- `app.feedback.tap()` em toques importantes.

Assinaturas esperadas (o shell chama exatamente assim):

```kotlin
SplashScreen(app); OnboardingScreen(app); AuthScreen(app); ForgotScreen(app)
ProfileSetupScreen(app); HomeScreen(app); WorkoutsScreen(app)
WorkoutDetailScreen(app, workoutId: String); LibraryScreen(app)
ExerciseDetailScreen(app, exId: String)
PretrainScreen(app, plan: SessionPlan); LiveScreen(app, plan: SessionPlan)
ExResultScreen(app, plan: SessionPlan); WorkoutResultScreen(app, recordId: String)
ProgressScreen(app); AchievementsScreen(app); ProfileScreen(app); EditProfileScreen(app)
SettingsScreen(app); PrivacyScreen(app); PaywallScreen(app, reason: String)
SubscriptionScreen(app)
```
