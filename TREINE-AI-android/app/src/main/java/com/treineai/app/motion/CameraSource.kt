package com.treineai.app.motion

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/* ============================================================
   CÂMERA — CameraX entregando quadros para a análise.

   Um quadro por vez: se a análise ainda não terminou, o próximo
   é descartado (KEEP_ONLY_LATEST). É isso que mantém a interface
   fluida mesmo quando o modelo demora num aparelho modesto.
   ============================================================ */
class CameraSource(
    private val context: Context,
    private val onFrame: (bitmap: Bitmap, brightness: Double, timestampMs: Long) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null

    var facing: Int = CameraSelector.LENS_FACING_FRONT
        private set

    /** Proporção do último quadro (largura/altura), usada pelo desenho do esqueleto. */
    @Volatile var aspect: Double = 3.0 / 4.0; private set

    /** Média de luminância do último quadro, de 0 a 1 — o "fotômetro" da versão web. */
    @Volatile var brightness: Double = 0.5; private set

    @SuppressLint("UnsafeOptInUsageError")
    fun start(owner: LifecycleOwner, preview: Preview?, onError: (Throwable) -> Unit = {}) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val an = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                an.setAnalyzer(executor, ::analyse)
                analysis = an

                p.unbindAll()
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                val useCases = listOfNotNull(preview, an).toTypedArray()
                p.bindToLifecycle(owner, selector, *useCases)
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun flip(owner: LifecycleOwner, preview: Preview?) {
        facing = if (facing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        start(owner, preview)
    }

    fun stop() {
        try { provider?.unbindAll() } catch (_: Exception) {}
        analysis?.clearAnalyzer()
        analysis = null
    }

    fun release() {
        stop()
        executor.shutdown()
    }

    private fun analyse(image: ImageProxy) {
        try {
            val rotation = image.imageInfo.rotationDegrees
            val bmp = image.toUprightBitmap(rotation, mirror = facing == CameraSelector.LENS_FACING_FRONT)
            aspect = bmp.width.toDouble() / bmp.height
            brightness = meanLuma(bmp)
            onFrame(bmp, brightness, System.currentTimeMillis())
        } catch (_: Throwable) {
            /* um quadro perdido não interrompe o treino */
        } finally {
            image.close()
        }
    }

    /** Luminância média em Rec. 601, medida numa miniatura 32×32 — como o fotômetro da web. */
    private fun meanLuma(bmp: Bitmap): Double {
        val small = Bitmap.createScaledBitmap(bmp, 32, 32, true)
        val px = IntArray(32 * 32)
        small.getPixels(px, 0, 32, 0, 0, 32, 32)
        if (small !== bmp) small.recycle()
        var sum = 0.0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sum += r * .299 + g * .587 + b * .114
        }
        return sum / px.size / 255.0
    }
}

/** Roda e espelha o quadro para que a pose saia na mesma orientação que a pessoa vê. */
private fun ImageProxy.toUprightBitmap(rotation: Int, mirror: Boolean): Bitmap {
    val src = toBitmap()
    if (rotation == 0 && !mirror) return src
    val m = Matrix()
    if (rotation != 0) m.postRotate(rotation.toFloat())
    if (mirror) m.postScale(-1f, 1f)
    val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    if (out !== src) src.recycle()
    return out
}
