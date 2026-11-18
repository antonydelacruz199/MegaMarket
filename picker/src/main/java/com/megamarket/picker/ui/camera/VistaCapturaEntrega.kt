package com.megamarket.picker.ui.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.megamarket.picker.R
import com.megamarket.picker.camera.VinculoCamara
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "MegaMarketEntregaCam"

@Composable
fun VistaCapturaEntrega(
    alFoto: (ByteArray) -> Unit,
    alFallo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember {
        PreviewView(contexto).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var captura by remember { mutableStateOf<ImageCapture?>(null) }
    var ocupada by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { vista },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )
        Button(
            onClick = {
                val actual = captura ?: return@Button
                if (ocupada) return@Button
                ocupada = true
                disparar(contexto, actual, alFoto, alFallo) { ocupada = false }
            },
            enabled = captura != null && !ocupada,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                if (ocupada) stringResource(R.string.capturando) else stringResource(R.string.capturar)
            )
        }
    }

    DisposableEffect(ciclo) {
        val ejecutor = Executors.newSingleThreadExecutor()
        val principal = ContextCompat.getMainExecutor(contexto)
        var proveedor: ProcessCameraProvider? = null
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(85)
            .build()
        val vinculo = VinculoCamara(
            desenlazar = { proveedor?.unbindAll() },
            cerrarLector = {},
            cerrarEjecutor = { ejecutor.shutdown() }
        )
        val futuro = ProcessCameraProvider.getInstance(contexto.applicationContext)
        futuro.addListener({
            if (vinculo.cerrado) return@addListener
            val cameraProvider = runCatching { futuro.get() }.getOrElse { error ->
                Log.w(TAG, "No se pudo abrir la cámara", error)
                alFallo()
                return@addListener
            }
            proveedor = cameraProvider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = vista.surfaceProvider
            }
            val ligado = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    ciclo,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            }
            if (vinculo.cerrado) {
                runCatching { cameraProvider.unbindAll() }
                return@addListener
            }
            if (ligado.isFailure) {
                Log.w(TAG, "No se pudo vincular la cámara", ligado.exceptionOrNull())
                alFallo()
            } else if (vinculo.marcarAbierto()) {
                captura = imageCapture
            }
        }, principal)
        onDispose {
            captura = null
            vinculo.cerrar()
        }
    }
}

private fun disparar(
    contexto: Context,
    captura: ImageCapture,
    alFoto: (ByteArray) -> Unit,
    alFallo: () -> Unit,
    alTerminar: () -> Unit
) {
    val archivo = File(contexto.cacheDir, "entrega-${System.currentTimeMillis()}.jpg")
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    val principal = ContextCompat.getMainExecutor(contexto)
    captura.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bytes = runCatching { archivo.readBytes() }.getOrNull()
                archivo.delete()
                principal.execute {
                    alTerminar()
                    if (bytes == null || bytes.isEmpty()) alFallo() else alFoto(bytes)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.w(TAG, "No se pudo capturar la foto", exception)
                archivo.delete()
                principal.execute {
                    alTerminar()
                    alFallo()
                }
            }
        }
    )
}
