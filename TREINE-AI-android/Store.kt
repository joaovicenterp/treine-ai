package com.treineai.app.data

import android.content.Context
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ============================================================
   NÚCLEO — persistência, autenticação, perfil, gamificação
   e assinatura.

   Local-first: tudo roda no aparelho, atrás de interfaces
   (Auth / Data / Billing) que podem ser trocadas por Firebase,
   Supabase ou uma API própria sem alterar as telas.
   ============================================================ */

private const val FILE = "treineai-v1.json"

/* ---------------- utilidades de data ---------------- */
private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

fun dayKey(time: Long = System.currentTimeMillis()): String = dayFmt.format(Date(time))

fun addDays(time: Long, n: Int): Long {
    val c = Calendar.getInstance().apply { timeInMillis = time; add(Calendar.DAY_OF_YEAR, n) }
    return c.timeInMillis
}

fun daysBetween(a: String, b: String): Int {
    val da = dayFmt.parse(a) ?: return 0
    val db = dayFmt.parse(b) ?: return 0
    return ((db.time - da.time) / 86_400_000.0).roundToInt()
}

fun uid(): String = "u" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

private fun clamp(v: Double, a: Double, b: Double) = max(a, min(b, v))

/* ============================================================
   ARMAZENAMENTO — um único arquivo JSON na área privada do app.
   ============================================================ */
class Store(private val context: Context) {
    private var mem: Database? = null

    fun read(): Database {
        mem?.let { return it }
        val db = try {
            val f = context.filesDir.resolve(FILE)
            if (f.exists()) AppJson.decodeFromString(Database.serializer(), f.readText()) else Database()
        } catch (_: Exception) {
            Database()
        }
        mem = db
        return db
    }

    fun write(db: Database) {
        mem = db
        try {
            context.filesDir.resolve(FILE).writeText(AppJson.encodeToString(Database.serializer(), db))
        } catch (_: Exception) {
            /* armazenamento indisponível: segue em memória */
        }
    }

    fun reset() = write(Database())
}

/* ============================================================
   HASH DE SENHA — SHA-256 com sal, como na versão web.
   ============================================================ */
fun hashPw(pw: String, salt: String): String {
    val txt = "$salt|$pw|treineai"
    val d = MessageDigest.getInstance("SHA-256").digest(txt.toByteArray(Charsets.UTF_8))
    return d.joinToString("") { "%02x".format(it) }
}

/* ============================================================
   ESTATÍSTICAS E GAMIFICAÇÃO
   ============================================================ */
data class Stats(
    val workouts: Int = 0,
    val exercises: Int = 0,
    val reps: Int = 0,
    val validReps: Int = 0,
    val minutes: Int = 0,
    val bestExerciseScore: Int = 0,
    val bestWorkoutScore: Int = 0,
    val earlyBird: Boolean = false,
    val groupsTrained: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val avgScore: Int = 0
)

data class LevelInfo(
    val level: Int,
    val name: String,
    val xp: Int,
    val next: XpLevel?,
    val progress: Double,
    val toNext: Int
)

data class WeekDay(val label: String, val key: String, val done: Boolean, val today: Boolean, val future: Boolean)

/** As mesmas 12 conquistas da versão web, com as mesmas condições. */
val ACHIEVEMENT_TESTS: Map<String, (Stats) -> Boolean> = mapOf(
    "first" to { s -> s.workouts >= 1 },
    "w5" to { s -> s.workouts >= 5 },
    "w10" to { s -> s.workouts >= 10 },
    "w30" to { s -> s.workouts >= 30 },
    "s7" to { s -> s.bestStreak >= 7 },
    "s30" to { s -> s.bestStreak >= 30 },
    "e100" to { s -> s.exercises >= 100 },
    "perfect" to { s -> s.bestExerciseScore >= 95 },
    "reps500" to { s -> s.validReps >= 500 },
    "score90" to { s -> s.bestWorkoutScore >= 90 },
    "early" to { s -> s.earlyBird },
    "variety" to { s -> s.groupsTrained >= 6 }
)

/* ============================================================
   REPOSITÓRIO — a única porta de entrada para o estado do app.
   ============================================================ */
class Repo(context: Context, val catalog: Catalog) {

    private val store = Store(context)
    private var db: Database = store.read()

    var currentUserId: String? = db.session?.userId
        private set

    val account: Account? get() = currentUserId?.let { id -> db.users.values.firstOrNull { it.id == id } }

    /* ---------------- ponte com a nuvem ----------------
       O login e o dono dos dados passam a vir do Firebase (ver AppState).
       O cache local continua sendo a fonte síncrona que as telas leem, e
       funciona offline; a nuvem sincroniza por trás. */
    var onChange: (() -> Unit)? = null
    private var boundName = ""
    private var boundEmail = ""

    /** Vincula a sessão local ao usuário do Firebase, sem apagar o cache. */
    fun bindUser(uid: String, name: String = boundName, email: String = boundEmail) {
        currentUserId = uid
        boundName = name
        boundEmail = email
    }

    /** Injeta no cache local os dados baixados da nuvem e passa a usá-los. */
    fun putData(uid: String, data: UserData) {
        db = db.copy(data = db.data + (uid to data))
        store.write(db)
        currentUserId = uid
    }

    /** Sai da conta: o app volta ao estado padrão (deslogado). */
    fun unbind() {
        currentUserId = null
        boundName = ""
        boundEmail = ""
    }

    /* ---------------- sessão ---------------- */
    fun restore(): Boolean {
        val s = db.session ?: return false
        val u = db.users.values.firstOrNull { it.id == s.userId } ?: run { persistSession(null); return false }
        currentUserId = u.id
        return true
    }

    private fun persistSession(s: Session?) {
        db = db.copy(session = s)
        store.write(db)
    }

    fun signUp(name: String, email: String, password: String): Result<Account> {
        val key = email.trim().lowercase()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(key))
            return Result.failure(Exception("Digite um e-mail válido."))
        if (db.users.containsKey(key))
            return Result.failure(Exception("Já existe uma conta com esse e-mail. Faça login."))
        if (password.length < 6)
            return Result.failure(Exception("A senha precisa de pelo menos 6 caracteres."))

        val salt = java.util.UUID.randomUUID().toString().take(10)
        val display = name.trim().ifEmpty { key.substringBefore('@') }
        val u = Account(uid(), display, key, salt, hashPw(password, salt), System.currentTimeMillis())
        val fresh = UserData(profile = Profile(name = display), createdAt = System.currentTimeMillis())
        db = db.copy(users = db.users + (key to u), data = db.data + (u.id to fresh))
        currentUserId = u.id
        persistSession(Session(u.id, true))
        return Result.success(u)
    }

    fun signIn(email: String, password: String, remember: Boolean): Result<Account> {
        val key = email.trim().lowercase()
        val u = db.users[key] ?: return Result.failure(Exception("Não encontramos uma conta com esse e-mail."))
        if (hashPw(password, u.salt) != u.pw) return Result.failure(Exception("Senha incorreta. Tente novamente."))
        currentUserId = u.id
        persistSession(Session(u.id, remember))
        return Result.success(u)
    }

    /** Login social: a arquitetura está pronta; falta a chave do provedor. */
    fun signInWithGoogle(): Result<Account> = Result.failure(
        Exception("Login com Google exige a configuração do provedor (Firebase/OAuth). Use e-mail e senha por enquanto.")
    )

    fun signOut() {
        currentUserId = null
        persistSession(null)
    }

    fun resetPassword(email: String, newPassword: String): Result<Boolean> {
        val key = email.trim().lowercase()
        val u = db.users[key] ?: return Result.failure(Exception("Não encontramos uma conta com esse e-mail."))
        if (newPassword.length < 6) return Result.failure(Exception("A nova senha precisa de pelo menos 6 caracteres."))
        val salt = java.util.UUID.randomUUID().toString().take(10)
        db = db.copy(users = db.users + (key to u.copy(salt = salt, pw = hashPw(newPassword, salt))))
        store.write(db)
        return Result.success(true)
    }

    fun deleteAccount() {
        val u = account ?: return
        db = db.copy(users = db.users - u.email, data = db.data - u.id, session = null)
        currentUserId = null
        store.write(db)
    }

    fun markIntroSeen() { db = db.copy(seenIntro = true); store.write(db) }
    val seenIntro: Boolean get() = db.seenIntro

    /* ---------------- dados do usuário ---------------- */
    fun data(): UserData = currentUserId?.let { db.data[it] } ?: UserData()

    fun update(block: (UserData) -> UserData) {
        val id = currentUserId ?: return
        val next = block(db.data[id] ?: UserData())
        db = db.copy(data = db.data + (id to next))
        store.write(db)
        /* avisa a nuvem que o progresso mudou (sincronização em segundo plano) */
        onChange?.invoke()
    }

    fun patchProfile(p: Profile) = update { it.copy(profile = p) }
    fun patchSettings(s: Settings) = update { it.copy(settings = s) }

    fun addWorkout(rec: WorkoutRecord) = update {
        it.copy(workouts = (listOf(rec) + it.workouts).take(400))
    }

    /** O histórico visível respeita o limite do plano gratuito. */
    fun visibleWorkouts(): List<WorkoutRecord> {
        val d = data()
        if (isPro()) return d.workouts
        val cutoff = dayKey(addDays(System.currentTimeMillis(), -catalog.d.config.free.historyDays))
        return d.workouts.filter { it.date >= cutoff }
    }

    /** Exportação completa dos dados do usuário, em JSON legível. */
    fun exportAll(): String {
        val pretty = kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
        val payload = ExportBundle(
            exportedAt = dayKey(),
            accountName = account?.name ?: boundName,
            accountEmail = account?.email ?: boundEmail,
            data = data()
        )
        return pretty.encodeToString(ExportBundle.serializer(), payload)
    }

    /* ---------------- estatísticas ---------------- */
    fun stats(): Stats {
        val ws = data().workouts
        val groups = HashSet<String>()
        var exercises = 0; var reps = 0; var validReps = 0
        var bestExerciseScore = 0; var bestWorkoutScore = 0
        var earlyBird = false; var minutes = 0.0

        ws.forEach { wk ->
            minutes += wk.duration / 60.0
            bestWorkoutScore = max(bestWorkoutScore, wk.score)
            if (wk.startedHour < 7) earlyBird = true
            wk.sessions.forEach { s ->
                exercises++; reps += s.reps; validReps += s.validReps
                bestExerciseScore = max(bestExerciseScore, s.score)
                if (s.group.isNotEmpty()) groups.add(s.group)
            }
        }
        val st = streak(ws)
        return Stats(
            workouts = ws.size, exercises = exercises, reps = reps, validReps = validReps,
            minutes = minutes.roundToInt(), bestExerciseScore = bestExerciseScore,
            bestWorkoutScore = bestWorkoutScore, earlyBird = earlyBird, groupsTrained = groups.size,
            streak = st.first, bestStreak = st.second,
            avgScore = if (ws.isEmpty()) 0 else (ws.sumOf { it.score }.toDouble() / ws.size).roundToInt()
        )
    }

    /** Sequência atual e melhor sequência, em dias. */
    fun streak(ws: List<WorkoutRecord>): Pair<Int, Int> {
        val days = ws.map { it.date }.distinct().sortedDescending()
        if (days.isEmpty()) return 0 to 0
        val today = dayKey()
        var current = 0
        if (daysBetween(days[0], today) <= 1) {
            current = 1
            for (i in 1 until days.size) {
                if (daysBetween(days[i], days[i - 1]) == 1) current++ else break
            }
        }
        var best = 1; var run = 1
        for (i in 1 until days.size) {
            if (daysBetween(days[i], days[i - 1]) == 1) { run++; best = max(best, run) } else run = 1
        }
        return current to max(best, current)
    }

    fun weekGrid(): List<WeekDay> {
        val done = data().workouts.map { it.date }.toHashSet()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        /* segunda-feira como início da semana, igual à versão web */
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val monday = addDays(now, -dow)
        val labels = listOf("S", "T", "Q", "Q", "S", "S", "D")
        val todayKey = dayKey(now)
        return labels.mapIndexed { i, l ->
            val k = dayKey(addDays(monday, i))
            WeekDay(l, k, done.contains(k), k == todayKey, k > todayKey)
        }
    }

    fun level(): LevelInfo {
        val xp = data().xp
        val levels = catalog.d.xpLevels
        var cur = levels.first(); var next: XpLevel? = null
        for (i in levels.indices) {
            if (xp >= levels[i].xp) { cur = levels[i]; next = levels.getOrNull(i + 1) }
        }
        val base = cur.xp
        val top = next?.xp ?: (cur.xp + 1000)
        return LevelInfo(
            level = cur.level, name = cur.name, xp = xp, next = next,
            progress = clamp((xp - base).toDouble() / (top - base), 0.0, 1.0),
            toNext = next?.let { it.xp - xp } ?: 0
        )
    }

    /** @return true se o usuário subiu de nível. */
    fun addXp(amount: Int): Boolean {
        val before = level().level
        update { it.copy(xp = it.xp + amount) }
        return level().level > before
    }

    /** Desbloqueia o que estiver conquistado e credita o XP correspondente. */
    fun checkAchievements(): List<AchievementDef> {
        val s = stats()
        val have = data().achievements.toMutableList()
        val won = ArrayList<AchievementDef>()
        var bonus = 0
        catalog.d.achievements.forEach { a ->
            if (a.id in have) return@forEach
            val test = ACHIEVEMENT_TESTS[a.id] ?: return@forEach
            if (test(s)) { have.add(a.id); won.add(a); bonus += a.xp }
        }
        if (won.isNotEmpty()) update { it.copy(achievements = have, xp = it.xp + bonus) }
        return won
    }

    /* ---------------- assinatura ---------------- */
    fun isPro(): Boolean {
        val s = data().sub
        if (s.plan != "pro") return false
        val ends = s.trialEndsAt
        if (s.status == "trial" && ends != null && System.currentTimeMillis() > ends) { expire(); return false }
        return true
    }

    fun isTrial(): Boolean = isPro() && data().sub.status == "trial"

    fun trialDaysLeft(): Int {
        val ends = data().sub.trialEndsAt ?: return 0
        return max(0.0, ceil((ends - System.currentTimeMillis()) / 86_400_000.0)).toInt()
    }

    fun expire() = update { it.copy(sub = it.sub.copy(plan = "free", status = "expired")) }

    /**
     * Ativa o plano PRO. O `BillingAdapter` da versão web vira aqui um ponto de
     * integração único: trocar por Google Play Billing não muda as telas.
     */
    fun subscribe(planId: String, trial: Boolean) {
        val now = System.currentTimeMillis()
        val trialEnds = if (trial) now + catalog.d.config.trialDays * 86_400_000L else null
        val days = if (catalog.d.config.plans[planId]?.id == "annual") 365 else 30
        update {
            it.copy(
                sub = Subscription(
                    plan = "pro", status = if (trial) "trial" else "active", planId = planId,
                    trialEndsAt = trialEnds, startedAt = now,
                    renewAt = trialEnds ?: (now + days * 86_400_000L),
                    receipt = "local-$now"
                )
            )
        }
    }

    fun cancelSubscription() = update {
        it.copy(sub = it.sub.copy(plan = "free", status = "canceled", trialEndsAt = null, renewAt = null))
    }

    /* ---------------- limites do plano gratuito ---------------- */
    fun analysesToday(): Int = data().usage[dayKey()] ?: 0

    /** `null` significa ilimitado (plano PRO). */
    fun analysesLeft(): Int? =
        if (isPro()) null else max(0, catalog.d.config.free.analysesPerDay - analysesToday())

    fun consumeAnalysis() {
        if (isPro()) return
        val k = dayKey()
        update { it.copy(usage = it.usage + (k to ((it.usage[k] ?: 0) + 1))) }
    }

    fun exerciseLocked(ex: Exercise): Boolean = !ex.free && !isPro()

    fun offer(): Offer =
        catalog.d.config.offers[catalog.d.config.offerStrategy]
            ?: catalog.d.config.offers.values.first()

    /* ---------------- sugestão do dia ---------------- */
    fun todayWorkout(): Workout? {
        val split = catalog.d.split
        val last = data().workouts.firstOrNull() ?: return catalog.workout(split.first())
        val i = split.indexOf(last.workoutId)
        return catalog.workout(split[(i + 1).mod(split.size)]) ?: catalog.workout(split.first())
    }
}
