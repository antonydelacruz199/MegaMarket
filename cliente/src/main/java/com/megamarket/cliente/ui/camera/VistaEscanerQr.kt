package com.megamarket.cliente.ui.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MegaMarketClienteCamara"

fun tieneCamara(contexto: Context): Boolean =
    contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

@Composable
fun VistaEscanerQr(
    activo: Boolean,
    alDetectar: (String) -> Unit,
    alSinCamara: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vistaPrevia = remember {
        PreviewView(contexto).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val detectar by rememberUpdatedState(alDetectar)
    val sinCamara by rememberUpdatedState(alSinCamara)

    AndroidView(factory = { vistaPrevia }, modifier = modifier)

    DisposableEffect(ciclo, activo) {
        if (!activo) {
            return@DisposableEffect onDispose { }
        }
        val ejecutor = Executors.newSingleThreadExecutor()
        val principal = ContextCompat.getMainExecutor(contexto)
        val lector = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        var proveedor: ProcessCameraProvider? = null
        var cerrado = false
        val analizador = AnalizadorQr(
            lector = lector,
            alDetectar = { codigo -> principal.execute { detectar(codigo) } }
        )
        val futuro = ProcessCameraProvider.getInstance(contexto.applicationContext)
        futuro.addListener({
            if (cerrado) return@addListener
            val cameraProvider = runCatching { futuro.get() }.getOrElse { error ->
                Log.w(TAG, "No se pudo abrir la cámara", error)
                sinCamara()
                return@addListener
            }
            proveedor = cameraProvider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = vistaPrevia.surfaceProvider
            }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analisis.setAnalyzer(ejecutor, analizador)
            val ligado = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    ciclo,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analisis
                )
            }
            if (cerrado) {
                runCatching { cameraProvider.unbindAll() }
                return@addListener
            }
            if (ligado.isFailure) {
                Log.w(TAG, "No se pudo vincular la cámara", ligado.exceptionOrNull())
                sinCamara()
            }
        }, principal)
        onDispose {
            cerrado = true
            analizador.detener()
            runCatching { proveedor?.unbindAll() }
            lector.close()
            ejecutor.shutdown()
        }
    }
}

private class AnalizadorQr(
    private val lector: BarcodeScanner,
    private val alDetectar: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val activo = AtomicBoolean(true)
    private var ultimo: String? = null

    fun detener() {
        activo.set(false)
    }

    override fun analyze(imagen: ImageProxy) {
        if (!activo.get()) {
            imagen.close()
            return
        }
        val media = imagen.image
        if (media == null) {
            imagen.close()
            return
        }
        val entrada = InputImage.fromMediaImage(media, imagen.imageInfo.rotationDegrees)
        try {
            lector.process(entrada)
                .addOnSuccessListener { codigos ->
                    val texto = codigos.firstNotNullOfOrNull { it.rawValue }?.trim().orEmpty()
                    if (texto.isNotEmpty() && texto != ultimo) {
                        ultimo = texto
                        alDetectar(texto)
                    }
                }
                .addOnCompleteListener { imagen.close() }
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo leer el fotograma", error)
            imagen.close()
        }
    }
}
