package com.treineai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.treineai.app.motion.CameraSource

/* ============================================================
   PAINEL DE CÂMERA
   Envolve o CameraX numa superfície Compose e entrega cada quadro
   ao serviço de análise. Se a câmera não abrir, o app continua:
   a análise cai para o provider simulado.
   ============================================================ */

enum class CamStatus { Pedindo, Ativa, Negada, Indisponivel }

class CamHandle internal constructor(
    val source: CameraSource,
    internal val preview: Preview
) {
    var status: CamStatus by mutableStateOf(CamStatus.Pedindo)
        internal set
    val ok: Boolean get() = status == CamStatus.Ativa
}

/**
 * Mostra a imagem da câmera e chama [onFrame] a cada quadro analisado.
 * O quadro já vem na orientação correta e espelhado na câmera frontal.
 */
@Composable
fun CameraPane(
    modifier: Modifier = Modifier,
    onFrame: (android.graphics.Bitmap, Double, Long) -> Unit,
    onHandle: (CamHandle) -> Unit = {}
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val previewUseCase = remember { Preview.Builder().build() }
    val source = remember { CameraSource(context, onFrame) }
    val handle = remember { CamHandle(source, previewUseCase) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) handle.status = CamStatus.Negada
    }

    LaunchedEffect(Unit) {
        onHandle(handle)
        if (!granted) ask.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(granted) {
        if (granted) {
            source.start(owner, previewUseCase) { handle.status = CamStatus.Indisponivel }
            handle.status = CamStatus.Ativa
        }
    }

    DisposableEffect(Unit) { onDispose { source.release() } }

    Box(modifier) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewUseCase.setSurfaceProvider(surfaceProvider)
                    }
                }
            )
        }
    }
}
