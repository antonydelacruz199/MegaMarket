package com.megamarket.picker.ui.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import com.megamarket.picker.camera.VinculoCamara
import com.megamarket.picker.escaneo.CambioLectura
import com.megamarket.picker.escaneo.FiltroFotogramas
import java.util.concurrent.Executors

private const val TAG = "MegaMarketPickerCamara"

fun tieneCamara(contexto: Context): Boolean =
    contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

private val selectorLectura = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(1280, 720),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
        )
    )
    .build()

private val opcionesBarras = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_ITF
    )
    .build()

private class ControlAnalisis {
    @Volatile var analizar: Boolean = true
}

@Composable
fun VistaPreviaCodigo(
    activo: Boolean,
    analizando: Boolean = true,
    alDetectar: (String) -> Unit,
    alVacio: () -> Unit,
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
    val control = remember { ControlAnalisis() }
    SideEffect { control.analizar = analizando }
    val detectar by rememberUpdatedState(alDetectar)
    val vacio by rememberUpdatedState(alVacio)
    val sinCamara by rememberUpdatedState(alSinCamara)

    AndroidView(factory = { vistaPrevia }, modifier = modifier)

    DisposableEffect(ciclo, activo) {
        if (!activo) {
            return@DisposableEffect onDispose { }
        }
        val ejecutor = Executors.newSingleThreadExecutor()
        val principal = ContextCompat.getMainExecutor(contexto)
        val lector = BarcodeScanning.getClient(opcionesBarras)
        var proveedor: ProcessCameraProvider? = null
        val analizador = AnalizadorBarras(
            lector = lector,
            debeAnalizar = { control.analizar },
            alDetectar = { codigo -> principal.execute { detectar(codigo) } },
            alVacio = { principal.execute { vacio() } }
        )
        val vinculo = VinculoCamara(
            desenlazar = {
                analizador.detener()
                proveedor?.unbindAll()
            },
            cerrarLector = { lector.close() },
            cerrarEjecutor = { ejecutor.shutdown() }
        )
        val futuro = ProcessCameraProvider.getInstance(contexto.applicationContext)
        futuro.addListener({
            if (vinculo.cerrado) return@addListener
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
                .setResolutionSelector(selectorLectura)
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
            if (vinculo.cerrado) {
                runCatching { cameraProvider.unbindAll() }
                return@addListener
            }
            if (ligado.isFailure) {
                Log.w(TAG, "No se pudo vincular la cámara", ligado.exceptionOrNull())
                sinCamara()
            } else {
                vinculo.marcarAbierto()
            }
        }, principal)
        onDispose { vinculo.cerrar() }
    }
}

private class AnalizadorBarras(
    private val lector: BarcodeScanner,
    private val debeAnalizar: () -> Boolean,
    private val alDetectar: (String) -> Unit,
    private val alVacio: () -> Unit
) : ImageAnalysis.Analyzer {
    private val filtro = FiltroFotogramas()

    @Volatile
    private var activo = true

    fun detener() {
        activo = false
    }

    override fun analyze(imagen: ImageProxy) {
        if (!activo || !debeAnalizar()) {
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
                    val texto = codigos.firstNotNullOfOrNull { it.rawValue }
                    when (val cambio = filtro.observar(texto)) {
                        is CambioLectura.Codigo -> alDetectar(cambio.valor)
                        CambioLectura.Vacio -> alVacio()
                        CambioLectura.Igual -> Unit
                    }
                }
                .addOnCompleteListener { imagen.close() }
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo leer el fotograma", error)
            imagen.close()
        }
    }
}
