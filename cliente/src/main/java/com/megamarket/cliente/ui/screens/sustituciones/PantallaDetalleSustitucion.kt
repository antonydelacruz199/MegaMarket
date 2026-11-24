package com.megamarket.cliente.ui.screens.sustituciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.ui.components.ImagenProducto
import com.megamarket.cliente.ui.textoEstadoSustitucion
import com.megamarket.cliente.viewmodel.DetalleSustitucionViewModel
import com.megamarket.modelo.SolicitudImagenProducto
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleSustitucion(
    viewModel: DetalleSustitucionViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirPedido: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, estado.propuesta?.id, estado.propuesta?.estado) {
        val observador = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (EstadoSustitucion.esPendiente(estado.propuesta?.estado)) {
                        viewModel.iniciarSeguimiento()
                    }
                }
                Lifecycle.Event.ON_STOP -> viewModel.detenerSeguimiento()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observador)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observador)
            viewModel.detenerSeguimiento()
        }
    }

    LaunchedEffect(estado.propuesta?.estado) {
        if (EstadoSustitucion.esPendiente(estado.propuesta?.estado) &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            viewModel.iniciarSeguimiento()
        }
    }

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sustitucion_detalle_titulo)) },
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
            estado.cargando && estado.propuesta == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sustituciones_cargando))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    estado.propuesta?.let { propuesta ->
                        item {
                            Text(
                                text = stringResource(
                                    R.string.sustitucion_estado,
                                    textoEstadoSustitucion(propuesta.estado)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (propuesta.respuestaAutomatica) {
                                Text(
                                    text = stringResource(R.string.sustitucion_respuesta_automatica),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item {
                            Text(
                                text = stringResource(R.string.sustitucion_contexto_disponibles),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (estado.lineasDisponibles.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sustitucion_sin_disponibles),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                estado.lineasDisponibles.forEach { linea ->
                                    Text("• ${linea.nombre} (x${linea.cantidad})")
                                }
                            }
                        }
                        item {
                            Text(
                                text = stringResource(R.string.sustitucion_contexto_faltantes),
                                fontWeight = FontWeight.SemiBold
                            )
                            val faltantes = estado.lineasFaltantes.ifEmpty {
                                emptyList()
                            }
                            if (faltantes.isEmpty()) {
                                Text("• ${propuesta.nombreOriginal} (x${propuesta.cantidad})")
                            } else {
                                faltantes.forEach { linea ->
                                    Text("• ${linea.nombre} (x${linea.cantidad})")
                                }
                            }
                        }
                        if (propuesta.esAvisoSinStock) {
                            item {
                                Text(
                                    text = stringResource(R.string.sustitucion_sin_stock_titulo),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(stringResource(R.string.sustitucion_sin_stock_mensaje))
                            }
                            item {
                                BloqueProducto(
                                    titulo = stringResource(R.string.sustitucion_producto_original),
                                    nombre = propuesta.nombreOriginal,
                                    marca = propuesta.marcaOriginal,
                                    imagen = propuesta.imagenOriginal,
                                    precioCentimos = propuesta.precioOriginalCentimos,
                                    cantidad = propuesta.cantidad
                                )
                            }
                            if (estado.pedidoCancelado) {
                                item {
                                    Text(
                                        text = "Pedido cancelado.",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else if (estado.puedeCancelarPedido) {
                                item {
                                    OutlinedButton(
                                        onClick = viewModel::cancelarPedido,
                                        enabled = !estado.cancelandoPedido,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (estado.cancelandoPedido) {
                                                "Cancelando…"
                                            } else {
                                                stringResource(R.string.cancelar_pedido_sustitucion)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                BloqueProducto(
                                    titulo = stringResource(R.string.sustitucion_contexto_reemplazo),
                                    nombre = propuesta.nombreSustituto,
                                    marca = propuesta.marcaSustituto,
                                    imagen = propuesta.imagenSustituto,
                                    precioCentimos = propuesta.precioSustitutoCentimos,
                                    cantidad = propuesta.cantidad
                                )
                                Text(
                                    text = stringResource(
                                        R.string.sustitucion_cantidad_igual,
                                        propuesta.cantidad
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                BloqueProducto(
                                    titulo = stringResource(R.string.sustitucion_producto_original),
                                    nombre = propuesta.nombreOriginal,
                                    marca = propuesta.marcaOriginal,
                                    imagen = propuesta.imagenOriginal,
                                    precioCentimos = propuesta.precioOriginalCentimos,
                                    cantidad = propuesta.cantidad
                                )
                            }
                            item {
                                Text(
                                    text = stringResource(
                                        R.string.sustitucion_importe_pagaras,
                                        propuesta.importeClienteCentimos.formatearSoles()
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (propuesta.precioSustitutoCentimos > propuesta.precioOriginalCentimos) {
                                    Text(
                                        text = stringResource(R.string.sustitucion_no_paga_mas),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!propuesta.fechaLimite.isNullOrBlank()) {
                                item {
                                    Text(
                                        text = stringResource(
                                            R.string.sustitucion_vence,
                                            formatearFechaHoraPresentacion(propuesta.fechaLimite)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (EstadoSustitucion.esPendiente(propuesta.estado) &&
                                        !estado.tiempoRestanteTexto.isNullOrBlank()
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.sustitucion_tiempo_restante,
                                                estado.tiempoRestanteTexto!!
                                            ),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        if (EstadoSustitucion.esPendiente(propuesta.estado)) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = viewModel::aceptar,
                                        enabled = !estado.enviando,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.aceptar))
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::rechazar,
                                        enabled = !estado.enviando,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.rechazar))
                                    }
                                }
                                TextButton(
                                    onClick = viewModel::consultarEstado,
                                    enabled = !estado.enviando && !estado.consultandoEstado
                                ) {
                                    Text(stringResource(R.string.sustitucion_actualizar_estado))
                                }
                            }
                        } else if (EstadoSustitucion.esDefinitivo(propuesta.estado) &&
                            !propuesta.esAvisoSinStock
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.sustitucion_estado_definitivo),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (estado.detallePedidoActualizado && estado.pedidoLocalId != null) {
                                    TextButton(
                                        onClick = { alAbrirPedido(estado.pedidoLocalId!!) }
                                    ) {
                                        Text(stringResource(R.string.ver_pedido))
                                    }
                                }
                            }
                        }
                    }
                    if (!estado.error.isNullOrBlank()) {
                        item {
                            Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                            if (estado.propuesta == null) {
                                TextButton(onClick = viewModel::cargar) {
                                    Text(stringResource(R.string.reintentar))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloqueProducto(
    titulo: String,
    nombre: String,
    marca: String?,
    imagen: SolicitudImagenProducto?,
    precioCentimos: Long,
    cantidad: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(titulo, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenProducto(
                solicitud = imagen,
                modifier = Modifier.size(72.dp),
                descripcion = nombre
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(nombre, style = MaterialTheme.typography.titleSmall)
                if (!marca.isNullOrBlank()) {
                    Text(marca, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.cantidad_solicitada, cantidad))
                Text(precioCentimos.formatearSoles())
            }
        }
    }
}
