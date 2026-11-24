package com.megamarket.picker.ui.screens.repartidor

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import com.megamarket.picker.ui.components.QrBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.megamarket.modelo.decodificarImagen
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.CODIGO_REQUIERE_PAGO
import com.megamarket.picker.data.repository.DetalleReparto
import com.megamarket.picker.state.DetalleEntregaUiState
import com.megamarket.picker.ui.camera.VistaCapturaEntrega
import com.megamarket.picker.ui.camera.tieneCamara
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.textoFecha
import com.megamarket.picker.ubicacion.UbicacionPrimerPlano
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleEntrega(
    estado: DetalleEntregaUiState,
    fotografia: () -> ByteArray?,
    alVolver: () -> Unit,
    alReintentar: () -> Unit,
    alFoto: (ByteArray) -> Unit,
    alFijarUbicacion: (Double, Double) -> Unit,
    alRegistrar: () -> Unit,
    alGenerarQr: () -> Unit = {},
    alOcultarQr: () -> Unit = {},
    alConfirmarPago: () -> Unit = {}
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ubicacion = remember { UbicacionPrimerPlano(contexto) }
    var permisoUbicacion by remember { mutableStateOf(ubicacion.permisoConcedido()) }
    var permisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var mostrarCamara by remember { mutableStateOf(false) }
    var avisoCamara by remember { mutableStateOf<String?>(null) }
    var buscandoGps by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf<Bitmap?>(null) }
    val falloFoto = stringResource(R.string.foto_fallo)
    val sinCamara = stringResource(R.string.camara_no_disponible)
    val permisoCamaraTexto = stringResource(R.string.permiso_camara_entrega)

    val pedirUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        permisoUbicacion = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val pedirCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        permisoCamara = concedido
        if (concedido) {
            mostrarCamara = true
            avisoCamara = null
        } else {
            avisoCamara = permisoCamaraTexto
        }
    }

    val enCamino = estado.detalle?.pedido?.estado.equals("EN_CAMINO", ignoreCase = true) && !estado.entregado

    LaunchedEffect(enCamino) {
        if (enCamino && !permisoUbicacion) {
            pedirUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(enCamino, permisoUbicacion, estado.latitud) {
        if (!enCamino || !permisoUbicacion || estado.latitud != null || buscandoGps) return@LaunchedEffect
        buscandoGps = true
        val punto = ubicacion.leer()
        buscandoGps = false
        if (punto != null) alFijarUbicacion(punto.latitud, punto.longitud)
    }

    LaunchedEffect(estado.revisionFoto) {
        vista = fotografia()?.decodificarImagen(640)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.detalle_entrega),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        when {
            estado.cargando && estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.cargando_pedidos))
                }
            }
            estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(estado.error ?: stringResource(R.string.reintentar), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = alReintentar) { Text(stringResource(R.string.reintentar)) }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    CuerpoEntrega(estado.detalle)
                    if (estado.pagoPendiente || estado.codigoError == CODIGO_REQUIERE_PAGO) {
                        Text(
                            text = stringResource(R.string.entrega_requiere_pago),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (estado.requierePagoQr) {
                        BloquePagoQr(
                            estado = estado,
                            alGenerarQr = alGenerarQr,
                            alOcultarQr = alOcultarQr,
                            alConfirmarPago = alConfirmarPago
                        )
                    } else if (estado.pagoConfirmadoReciente) {
                        Text(
                            text = stringResource(R.string.pago_qr_confirmado),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    estado.error?.let { mensaje ->
                        if (estado.codigoError != CODIGO_REQUIERE_PAGO) {
                            Text(mensaje, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (estado.entregado && estado.confirmacionReciente) {
                        Text(
                            text = if (estado.idempotente) {
                                stringResource(R.string.entrega_idempotente)
                            } else {
                                stringResource(R.string.entrega_confirmada)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(onClick = alVolver, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.volver))
                        }
                    } else if (enCamino) {
                        if (!permisoUbicacion) {
                            Text(stringResource(R.string.permiso_ubicacion))
                            TextButton(onClick = {
                                pedirUbicacion.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }) { Text(stringResource(R.string.reintentar)) }
                        } else if (buscandoGps && estado.latitud == null) {
                            Text(stringResource(R.string.ubicacion_pendiente))
                        } else if (estado.latitud != null && estado.longitud != null) {
                            Text(stringResource(R.string.ubicacion_ok))
                        } else {
                            Text(stringResource(R.string.ubicacion_fallo))
                            TextButton(onClick = {
                                alcance.launch {
                                    buscandoGps = true
                                    val punto = ubicacion.leer()
                                    buscandoGps = false
                                    if (punto != null) alFijarUbicacion(punto.latitud, punto.longitud)
                                }
                            }) { Text(stringResource(R.string.reintentar)) }
                        }
                        avisoCamara?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (estado.fotoLista) {
                            Text(stringResource(R.string.foto_lista))
                            vista?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.foto_lista),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                            }
                        }
                        if (mostrarCamara && permisoCamara) {
                            VistaCapturaEntrega(
                                alFoto = { bytes ->
                                    mostrarCamara = false
                                    avisoCamara = null
                                    alFoto(bytes)
                                },
                                alFallo = { avisoCamara = falloFoto },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = {
                                if (!tieneCamara(contexto)) {
                                    avisoCamara = sinCamara
                                    return@Button
                                }
                                if (!permisoCamara) {
                                    pedirCamara.launch(Manifest.permission.CAMERA)
                                } else {
                                    mostrarCamara = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (estado.fotoLista) {
                                    stringResource(R.string.repetir_foto)
                                } else {
                                    stringResource(R.string.tomar_foto)
                                }
                            )
                        }
                        val puede = estado.detalle.puedeFinalizar &&
                            estado.fotoLista &&
                            estado.latitud != null &&
                            !estado.pagoPendiente &&
                            !estado.registrando
                        Button(
                            onClick = alRegistrar,
                            enabled = puede,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (estado.registrando) {
                                    stringResource(R.string.registrando_entrega)
                                } else {
                                    stringResource(R.string.registrar_entrega)
                                }
                            )
                        }
                    } else if (estado.entregado) {
                        Text(stringResource(R.string.estado_entregado))
                    } else {
                        Text(stringResource(R.string.solo_en_camino))
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloquePagoQr(
    estado: DetalleEntregaUiState,
    alGenerarQr: () -> Unit,
    alOcultarQr: () -> Unit,
    alConfirmarPago: () -> Unit
) {
    val qr = estado.qrPago
    val imagen = remember(qr?.qrContenido) {
        qr?.qrContenido?.let { QrBitmap.generar(it)?.asImageBitmap() }
    }
    val importe = qr?.montoCentimos
        ?: estado.detalle?.pago?.montoCentimos
        ?: estado.detalle?.pedido?.totalCentimos
        ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.pago_qr_titulo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.pago_qr_monto, importe.formatearSoles()),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Confirma el cobro en efectivo, o muestra el QR para que el cliente lo escanee y pague con su billetera.",
                style = MaterialTheme.typography.bodySmall
            )
            val puedeCobrar = !estado.confirmandoPago &&
                (estado.detalle?.pago?.id != null || qr?.pagoId != null)
            Button(
                onClick = alConfirmarPago,
                enabled = puedeCobrar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (estado.confirmandoPago) {
                        stringResource(R.string.pago_qr_confirmando)
                    } else {
                        "Confirmar cobro en efectivo"
                    }
                )
            }
            when {
                estado.generandoQr -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.pago_qr_generando))
                }
                qr != null -> {
                    val generado = textoFecha(qr.generadoEn)
                    if (generado.isNotEmpty()) Text(stringResource(R.string.pago_qr_generado, generado))
                    val vence = textoFecha(qr.expiraEn)
                    if (vence.isNotEmpty()) Text(stringResource(R.string.pago_qr_vence, vence))
                    qr.referencia?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.pago_qr_referencia, it))
                    }
                    if (imagen != null) {
                        Image(
                            bitmap = imagen,
                            contentDescription = stringResource(R.string.pago_qr_descripcion),
                            modifier = Modifier
                                .size(220.dp)
                                .background(Color.White)
                                .padding(8.dp)
                        )
                    }
                    Text(
                        text = "El cliente puede escanear este QR para pagar con billetera. Tú puedes confirmar el cobro en efectivo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = alOcultarQr) {
                        Text(stringResource(R.string.pago_qr_ocultar))
                    }
                }
                else -> {
                    Button(
                        onClick = alGenerarQr,
                        enabled = !estado.confirmandoPago,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.pago_qr_generar))
                    }
                }
            }
            estado.errorPago?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CuerpoEntrega(detalle: DetalleReparto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Pedido ${detalle.pedido.id.take(8)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = textoFecha(detalle.pedido.fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = detalle.pedido.estado,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detalle.pedido.totalCentimos.formatearSoles(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.direccion_entrega),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            val direccion = detalle.direccion
            if (direccion == null || direccion.texto().isEmpty()) {
                Text(
                    text = stringResource(R.string.sin_direccion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = direccion.texto(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            val telefono = direccion?.telefono.orEmpty()
            if (telefono.isNotBlank()) {
                Text(
                    text = stringResource(R.string.telefono_entrega, telefono),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    val pago = detalle.pago
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (pago == null) {
                Text(
                    text = stringResource(R.string.sin_pago),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.pago_entrega, pago.modalidad, pago.estado),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
