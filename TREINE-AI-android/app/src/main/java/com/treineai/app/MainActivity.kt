package com.treineai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.treineai.app.ui.AppState
import com.treineai.app.ui.TA
import com.treineai.app.ui.TreineApp
import com.treineai.app.ui.TreineTheme

/* ============================================================
   Única Activity do app. Sem WebView, sem HTML: toda a interface
   é Jetpack Compose nativo.
   ============================================================ */
class MainActivity : ComponentActivity() {

    private val app: AppState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        /* a tela do treino precisa ficar acesa: a pessoa está longe do celular */
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TreineTheme {
                Box(Modifier.fillMaxSize().background(TA.ink0)) {
                    TreineApp(app)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        /* nada de voz tocando com o app em segundo plano */
        app.feedback.silence()
        app.voice.suspend()
    }

    override fun onResume() {
        super.onResume()
        app.syncVoiceSettings()
    }
}
