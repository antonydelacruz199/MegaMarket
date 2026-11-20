package com.megamarket.cliente.ui.screens.pagos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.camera.VistaEscanerQr
import com.megamarket.cliente.ui.camera.tieneCamara
import com.megamarket.cliente.viewmodel.EscanearPagoViewModel
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEscanearPago(
    viewModel: EscanearPagoViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit,
    alPagoConfirmado: () -> Unit = alVolver
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var permisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var sinCamara by remember { mutableStateOf(!tieneCamara(contexto)) }
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        permisoCamara = concedido
    }

    LaunchedEffect(Unit) {
        if (!sinCamara && !permisoCamara) {
            pedirPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    LaunchedEffect(estado.confirmado) {
        if (estado.confirmado) alPagoConfirmado()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR de pago") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (estado.confirmando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = "Apunta al código QR que muestra el repartidor para pagar con tu billetera.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                estado.confirmado -> {
                    Text(
                        "Pago confirmado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    estado.saldoClienteCentimos?.let {
                        Text("Saldo disponible: ${it.formatearSoles()}")
                    }
                }
                sinCamara -> {
                    Text(
                        "Este dispositivo no tiene cámara disponible.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                !permisoCamara -> {
                    Text(
                        "Se necesita permiso de cámara para escanear el QR.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { pedirPermiso.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder permiso")
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        VistaEscanerQr(
                            activo = estado.escaneando && !estado.confirmando,
                            alDetectar = viewModel::codigoDetectado,
                            alSinCamara = { sinCamara = true },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (estado.confirmando) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }

            estado.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            if (!estado.confirmado && estado.error != null) {
                OutlinedButton(
                    onClick = viewModel::reintentarEscaneo,
                    enabled = !estado.confirmando,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reintentar escaneo") }
            }

            OutlinedButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) {
                Text("Volver")
            }
        }
    }
}
