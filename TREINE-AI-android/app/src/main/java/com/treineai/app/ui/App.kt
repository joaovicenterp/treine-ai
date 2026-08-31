package com.treineai.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.ui.screens.AchievementsScreen
import com.treineai.app.ui.screens.AuthScreen
import com.treineai.app.ui.screens.EditProfileScreen
import com.treineai.app.ui.screens.ExResultScreen
import com.treineai.app.ui.screens.ExerciseDetailScreen
import com.treineai.app.ui.screens.ForgotScreen
import com.treineai.app.ui.screens.HomeScreen
import com.treineai.app.ui.screens.LibraryScreen
import com.treineai.app.ui.screens.LiveScreen
import com.treineai.app.ui.screens.OnboardingScreen
import com.treineai.app.ui.screens.PaywallScreen
import com.treineai.app.ui.screens.PretrainScreen
import com.treineai.app.ui.screens.PrivacyScreen
import com.treineai.app.ui.screens.ProfileScreen
import com.treineai.app.ui.screens.ProfileSetupScreen
import com.treineai.app.ui.screens.ProgressScreen
import com.treineai.app.ui.screens.SettingsScreen
import com.treineai.app.ui.screens.SplashScreen
import com.treineai.app.ui.screens.SubscriptionScreen
import com.treineai.app.ui.screens.WorkoutDetailScreen
import com.treineai.app.ui.screens.WorkoutResultScreen
import com.treineai.app.ui.screens.WorkoutsScreen
import kotlinx.coroutines.delay

/* ============================================================
   CASCA DO APLICATIVO
   Resolve a rota atual, decide quando a barra de abas aparece e
   mantém os avisos passageiros e a comemoração de conquistas.
   ============================================================ */

/** Telas que ocupam a tela inteira: câmera e contagem não convivem com abas. */
private val FULLSCREEN = setOf("splash", "onboarding", "auth", "forgot", "profileSetup", "pretrain", "live", "paywall")

@Composable
fun TreineApp(app: AppState) {
    val route = app.route
    val isTab = TABS.any { it.route.name == route.name }

    BackHandler(enabled = app.canGoBack) { app.back() }

    Box(Modifier.fillMaxSize().background(TA.ink0)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .then(if (route.name in FULLSCREEN) Modifier else Modifier.statusBarsPadding())
                    .imePadding()
            ) {
                when (route) {
                    is Route.Splash -> SplashScreen(app)
                    is Route.Onboarding -> OnboardingScreen(app)
                    is Route.Auth -> AuthScreen(app)
                    is Route.Forgot -> ForgotScreen(app)
                    is Route.ProfileSetup -> ProfileSetupScreen(app)

                    is Route.Home -> HomeScreen(app)
                    is Route.Workouts -> WorkoutsScreen(app)
                    is Route.Progress -> ProgressScreen(app)
                    is Route.Achievements -> AchievementsScreen(app)
                    is Route.Profile -> ProfileScreen(app)

                    is Route.WorkoutDetail -> WorkoutDetailScreen(app, route.workoutId)
                    is Route.Library -> LibraryScreen(app)
                    is Route.ExerciseDetail -> ExerciseDetailScreen(app, route.exId)

                    is Route.Pretrain -> PretrainScreen(app, route.plan)
                    is Route.Live -> LiveScreen(app, route.plan)
                    is Route.ExResult -> ExResultScreen(app, route.plan)
                    is Route.WorkoutResult -> WorkoutResultScreen(app, route.recordId)

                    is Route.EditProfile -> EditProfileScreen(app)
                    is Route.Settings -> SettingsScreen(app)
                    is Route.Privacy -> PrivacyScreen(app)
                    is Route.Paywall -> PaywallScreen(app, route.reason)
                    is Route.Subscription -> SubscriptionScreen(app)
                }
            }

            if (isTab) BottomNav(route.name, onTab = { app.feedback.tap(); app.tab(it) })
        }

        /* aviso passageiro, no rodapé */
        app.toast?.let { msg ->
            LaunchedEffect(msg) { delay(4200); app.toast = null }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isTab) TA.navH + 16.dp else 26.dp)
                    .padding(horizontal = TA.pad)
                    .navigationBarsPadding()
            ) {
                Text(
                    msg,
                    modifier = Modifier
                        .clip(TA.rPill)
                        .background(TA.ink2)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TA.cream
                )
            }
        }

        /* comemoração de conquistas */
        if (app.celebrating.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().background(TA.ink0.copy(alpha = .88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(TA.pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        "NOVA CONQUISTA",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = TA.flame
                    )
                    Text(
                        if (app.celebrating.size > 1) "${app.celebrating.size} conquistas"
                        else app.celebrating.first().name,
                        style = MaterialTheme.typography.displaySmall,
                        color = TA.cream,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        app.celebrating.forEach { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(a.icon, fontSize = 34.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(a.name, style = MaterialTheme.typography.labelMedium, color = TA.cream)
                                Text("+${a.xp} XP", style = MaterialTheme.typography.labelSmall, color = TA.flame)
                            }
                        }
                    }
                    Btn(
                        "Continuar",
                        onClick = { app.celebrating.clear() },
                        modifier = Modifier.fillMaxWidth(),
                        big = true
                    )
                }
            }
        }
    }
}
