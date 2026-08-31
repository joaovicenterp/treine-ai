package com.treineai.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.R

/* ============================================================
   NAVEGAÇÃO — uma pilha simples, como no `App.go/back/tab` da
   versão web. Cada destino carrega seus próprios parâmetros.
   ============================================================ */
sealed interface Route {
    val name: String

    data object Splash : Route { override val name = "splash" }
    data object Onboarding : Route { override val name = "onboarding" }
    data object Auth : Route { override val name = "auth" }
    data object Forgot : Route { override val name = "forgot" }
    data object ProfileSetup : Route { override val name = "profileSetup" }

    /* abas */
    data object Home : Route { override val name = "home" }
    data object Workouts : Route { override val name = "workouts" }
    data object Progress : Route { override val name = "progress" }
    data object Achievements : Route { override val name = "achievements" }
    data object Profile : Route { override val name = "profile" }

    data class WorkoutDetail(val workoutId: String) : Route { override val name = "workoutDetail" }
    data object Library : Route { override val name = "library" }
    data class ExerciseDetail(val exId: String) : Route { override val name = "exercise" }

    /** Posicionamento guiado por voz, antes do treino começar. */
    data class Pretrain(val plan: SessionPlan) : Route { override val name = "pretrain" }
    data class Live(val plan: SessionPlan) : Route { override val name = "live" }
    data class ExResult(val plan: SessionPlan) : Route { override val name = "exResult" }
    data class WorkoutResult(val recordId: String) : Route { override val name = "workoutResult" }

    data object EditProfile : Route { override val name = "editProfile" }
    data object Settings : Route { override val name = "settings" }
    data object Privacy : Route { override val name = "privacy" }
    data class Paywall(val reason: String = "") : Route { override val name = "paywall" }
    data object Subscription : Route { override val name = "subscription" }
}

/** O que está sendo treinado agora: um exercício avulso ou um treino inteiro. */
data class SessionPlan(
    val exerciseIds: List<String>,
    val index: Int = 0,
    val targetReps: Int = 12,
    val workoutId: String? = null,
    val workoutName: String = "",
    val startedAt: Long = 0L
) {
    val current: String get() = exerciseIds[index.coerceIn(0, exerciseIds.lastIndex)]
    val isLast: Boolean get() = index >= exerciseIds.lastIndex
    val position: String get() = "${index + 1}/${exerciseIds.size}"
    fun next() = copy(index = index + 1)
}

data class Tab(val route: Route, val icon: String, val label: String)

val TABS = listOf(
    Tab(Route.Home, "home", "Início"),
    Tab(Route.Workouts, "dumbbell", "Treinos"),
    Tab(Route.Progress, "chart", "Evolução"),
    Tab(Route.Achievements, "trophy", "Conquistas"),
    Tab(Route.Profile, "user", "Perfil")
)

@Composable
fun BottomNav(current: String, onTab: (Route) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(TA.ink1)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TA.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(TA.navH),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEach { tab ->
                val on = tab.route.name == current
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(TA.rMd)
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { onTab(tab.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(tab.icon, size = 22.dp, tint = if (on) TA.flame else TA.cream3)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) TA.flame else TA.cream3
                    )
                }
            }
        }
    }
}

/** Marca oficial: a logo do TREINE AI, sem redesenho, ao lado do nome. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, markSize: Dp = 30.dp, textSize: TextUnit = 19.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ta_logo),
            contentDescription = "TREINE AI",
            modifier = Modifier.size(markSize),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "TREINE AI",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = textSize, letterSpacing = 0.4.sp
            ),
            color = TA.cream
        )
    }
}
