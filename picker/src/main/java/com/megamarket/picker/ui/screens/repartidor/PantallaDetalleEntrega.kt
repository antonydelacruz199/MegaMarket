package com.megamarket.picker.ui.screens.repartidor

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
    alRegistrar: () -> Unit
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
                title = { Text(stringResource(R.string.detalle_entrega)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
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
                    if (estado.refrescando || estado.registrando) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
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
private fun CuerpoEntrega(detalle: DetalleReparto) {
    Text(
        text = "Pedido ${detalle.pedido.id.take(8)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(textoFecha(detalle.pedido.fecha), style = MaterialTheme.typography.bodySmall)
    Text(detalle.pedido.estado)
    Text(detalle.pedido.totalCentimos.formatearSoles(), fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.direccion_entrega), fontWeight = FontWeight.SemiBold)
    val direccion = detalle.direccion
    if (direccion == null || direccion.texto().isEmpty()) {
        Text(stringResource(R.string.sin_direccion))
    } else {
        Text(direccion.texto())
    }
    val telefono = direccion?.telefono.orEmpty()
    if (telefono.isNotBlank()) {
        Text(stringResource(R.string.telefono_entrega, telefono))
    }
    val pago = detalle.pago
    if (pago == null) {
        Text(stringResource(R.string.sin_pago))
    } else {
        Text(stringResource(R.string.pago_entrega, pago.modalidad, pago.estado))
    }
}
