package com.megamarket.cliente.ui.screens.sustituciones

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.ui.components.ImagenProducto
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.ui.textoEstadoSustitucion
import com.megamarket.cliente.viewmodel.SustitucionesViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaSustituciones(
    viewModel: SustitucionesViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirDetalle: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observador = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.cargar()
                    viewModel.iniciarSeguimiento()
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

    LaunchedEffect(estado.pendientes) {
        if (estado.pendientes > 0 &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            viewModel.iniciarSeguimiento()
        } else if (estado.pendientes <= 0) {
            viewModel.detenerSeguimiento()
        }
    }

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    EstructuraCliente(
        rutaActual = Ruta.Sustituciones.ruta,
        titulo = stringResource(R.string.sustituciones_titulo),
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        when {
            estado.cargando && estado.propuestas.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(relleno),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sustituciones_cargando))
                }
            }
            estado.propuestas.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(relleno)
                        .padding(16.dp)
                ) {
                    if (!estado.error.isNullOrBlank()) {
                        Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::cargar) {
                            Text(stringResource(R.string.reintentar))
                        }
                    } else {
                        EstadoVacio(
                            icono = Icons.Default.ShoppingCart,
                            titulo = stringResource(R.string.sustituciones_vacias_titulo),
                            descripcion = stringResource(R.string.sustituciones_vacias),
                            etiquetaAccion = stringResource(R.string.actualizar),
                            alPulsarAccion = viewModel::cargar
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(relleno),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (estado.pendientes > 0) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.sustituciones_atencion_count,
                                    estado.pendientes
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!estado.error.isNullOrBlank()) {
                        item {
                            Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::cargar) {
                                Text(stringResource(R.string.reintentar))
                            }
                        }
                    }
                    items(estado.propuestas, key = { it.id }) { propuesta ->
                        TarjetaPropuestaLista(
                            propuesta = propuesta,
                            alAbrir = { alAbrirDetalle(propuesta.id) }
                        )
                        HorizontalDivider()
                    }
                    if (estado.hayMas) {
                        item {
                            if (estado.cargandoMas) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::cargarMas,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cargar más") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPropuestaLista(
    propuesta: PropuestaSustitucionCliente,
    alAbrir: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alAbrir)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImagenProducto(
            solicitud = propuesta.imagenSustituto ?: propuesta.imagenOriginal,
            modifier = Modifier.size(64.dp),
            descripcion = propuesta.nombreSustituto
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = textoEstadoSustitucion(propuesta.estado),
                color = when {
                    propuesta.esAvisoSinStock -> MaterialTheme.colorScheme.error
                    EstadoSustitucion.esPendiente(propuesta.estado) ->
                        MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (propuesta.esAvisoSinStock) {
                    stringResource(
                        R.string.sustitucion_sin_stock_resumen,
                        propuesta.nombreOriginal
                    )
                } else {
                    stringResource(
                        R.string.sustitucion_resumen,
                        propuesta.nombreOriginal,
                        propuesta.nombreSustituto
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!propuesta.esAvisoSinStock) {
                Text(
                    text = stringResource(
                        R.string.sustitucion_importe_pagaras,
                        propuesta.importeClienteCentimos.formatearSoles()
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            propuesta.pedidoId?.let {
                Text(
                    text = stringResource(R.string.sustitucion_pedido_remoto, it.take(8)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
