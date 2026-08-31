package com.treineai.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ============================================================
   Catálogo — os mesmos 50 exercícios, treinos e conquistas da
   versão web. O arquivo assets/catalog.json é gerado a partir
   da mesma fonte, então as duas versões nunca divergem.
   ============================================================ */

@Serializable
data class RepRange(val joint: String, val top: Double, val bottom: Double)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val group: String,
    val equipment: String,
    val level: String,
    val pattern: String,
    val view: String,
    val rep: RepRange,
    val free: Boolean = false,
    val hold: Boolean = false,
    val checks: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val tips: List<String> = emptyList()
)

@Serializable
data class WorkoutItem(val ex: String, val sets: Int, val reps: Int)

@Serializable
data class Workout(
    val id: String,
    val name: String,
    val focus: String,
    val groups: List<String>,
    val minutes: Int,
    val items: List<WorkoutItem>
)

@Serializable
data class MuscleGroup(val id: String, val name: String)

@Serializable
data class Goal(val id: String, val name: String, val desc: String)

@Serializable
data class XpLevel(val level: Int, val name: String, val xp: Int)

@Serializable
data class AchievementDef(
    val id: String,
    val icon: String,
    val name: String,
    val desc: String,
    val xp: Int
)

@Serializable
data class Plan(
    val id: String,
    val label: String,
    val price: Double,
    val per: String,
    val billed: String,
    val save: String? = null
)

@Serializable
data class Offer(val cta: String, val headline: String, val note: String)

@Serializable
data class FreeLimits(
    val libraryLimit: Int,
    val analysesPerDay: Int,
    val historyDays: Int,
    val advancedFeedback: Boolean = false
)

@Serializable
data class Billing(val provider: String = "none")

@Serializable
data class AppConfig(
    val currency: String,
    val symbol: String,
    val plans: Map<String, Plan>,
    val defaultPlan: String,
    val offerStrategy: String,
    val offers: Map<String, Offer>,
    val trialDays: Int,
    val free: FreeLimits,
    val billing: Billing = Billing()
)

@Serializable
data class CatalogData(
    val config: AppConfig,
    val groups: List<MuscleGroup>,
    val levels: List<String>,
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val split: List<String>,
    val goals: List<Goal>,
    @SerialName("xpLevels") val xpLevels: List<XpLevel>,
    val achievements: List<AchievementDef>,
    val quotes: List<String>,
    val disclaimer: String
)

/* ============================================================
   Estado do usuário — equivalente ao que a versão web guarda
   no localStorage.
   ============================================================ */

@Serializable
data class Profile(
    val name: String = "",
    val photo: String = "",
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val goal: String = "",
    val experience: String = "Iniciante",
    val frequency: Int = 3,
    val unit: String = "kg"
)

@Serializable
data class Settings(
    val voice: Boolean = true,
    val voiceVolume: String = "alto",
    val audioFirst: Boolean = true,
    val autoStart: Boolean = true,
    val voiceCommands: Boolean = false,
    val haptics: Boolean = true,
    val notifications: Boolean = true,
    val sounds: Boolean = true,
    val skeleton: Boolean = true
)

@Serializable
data class Subscription(
    val plan: String = "free",
    val status: String = "none",
    val planId: String? = null,
    val trialEndsAt: Long? = null,
    val startedAt: Long? = null,
    val renewAt: Long? = null,
    val receipt: String? = null
)

@Serializable
data class ExerciseSession(
    val exId: String,
    val name: String,
    val group: String,
    val reps: Int,
    val validReps: Int,
    val invalid: Int = 0,
    val score: Int,
    val best: Int = 0,
    val duration: Int = 0,
    val avgDepth: Double = 0.0,
    val avgTempo: Double = 0.0,
    val errors: Map<String, Int> = emptyMap(),
    val mainError: String? = null
)

@Serializable
data class ScoreBreakdown(
    val tecnica: Int = 0,
    val consistencia: Int = 0,
    val amplitude: Int = 0,
    val controle: Int = 0,
    val total: Int = 0
)

@Serializable
data class WorkoutRecord(
    val id: String,
    val date: String,
    val ts: Long,
    val workoutId: String,
    val name: String,
    val focus: String = "",
    val duration: Int,
    val score: Int,
    val breakdown: ScoreBreakdown = ScoreBreakdown(),
    val startedHour: Int = 12,
    val sessions: List<ExerciseSession> = emptyList(),
    val prevScore: Int? = null
)

@Serializable
data class UserData(
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val onboarded: Boolean = false,
    val profileDone: Boolean = false,
    val xp: Int = 0,
    val achievements: List<String> = emptyList(),
    val workouts: List<WorkoutRecord> = emptyList(),
    val usage: Map<String, Int> = emptyMap(),
    val sub: Subscription = Subscription(),
    val createdAt: Long = 0L
)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val email: String,
    val salt: String,
    val pw: String,
    val createdAt: Long = 0L
)

@Serializable
data class Session(val userId: String, val remember: Boolean)

@Serializable
data class Database(
    val users: Map<String, Account> = emptyMap(),
    val session: Session? = null,
    val data: Map<String, UserData> = emptyMap(),
    val seenIntro: Boolean = false
)

/** Pacote de exportação usado pela tela de privacidade. */
@Serializable
data class ExportBundle(
    val exportedAt: String,
    val accountName: String,
    val accountEmail: String,
    val data: UserData
)

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}

/* Catálogo carregado uma vez e consultado como no `TA_DATA` da web. */
class Catalog(val d: CatalogData) {
    val byId: Map<String, Exercise> = d.exercises.associateBy { it.id }
    fun exercise(id: String): Exercise? = byId[id]
    fun exercisesOf(group: String): List<Exercise> = d.exercises.filter { it.group == group }
    fun groupName(id: String): String = d.groups.firstOrNull { it.id == id }?.name ?: id
    fun workout(id: String): Workout? = d.workouts.firstOrNull { it.id == id }
    val freeCount: Int get() = d.exercises.count { it.free }

    companion object {
        fun parse(json: String): Catalog = Catalog(AppJson.decodeFromString(CatalogData.serializer(), json))
    }
}
