package com.megamarket.picker.ui.screens.picker

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.CandidatoSustituto
import com.megamarket.picker.data.repository.EstadoSustitucion
import com.megamarket.picker.state.SustitucionUiState
import com.megamarket.picker.ui.components.ImagenProductoRemota
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.textoEstadoSustitucion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSustitucion(
    estado: SustitucionUiState,
    alVolver: () -> Unit,
    alReintentar: () -> Unit,
    alSeleccionar: (String) -> Unit,
    alProponer: () -> Unit,
    alIniciarSeguimiento: () -> Unit,
    alDetenerSeguimiento: () -> Unit,
    alConsultarEstado: () -> Unit,
    alInformarSinStock: () -> Unit = {},
    alResolverSinProducto: () -> Unit = {}
) {
    var confirmarInformar by remember { mutableStateOf(false) }
    var confirmarEliminacion by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, estado.propuesta?.id, estado.propuesta?.estado) {
        val observador = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (EstadoSustitucion.esPendiente(estado.propuesta?.estado)) {
                        alIniciarSeguimiento()
                    }
                }
                Lifecycle.Event.ON_STOP -> alDetenerSeguimiento()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observador)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observador)
            alDetenerSeguimiento()
        }
    }

    LaunchedEffect(estado.propuesta?.estado) {
        if (EstadoSustitucion.esPendiente(estado.propuesta?.estado) &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            alIniciarSeguimiento()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sustitucion_titulo)) },
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
            estado.cargando && estado.candidatos.isEmpty() && estado.propuesta == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sustitucion_cargando))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        EncabezadoSustitucion(estado)
                    }
                    if (estado.faltantePendienteLocal) {
                        item {
                            Text(
                                text = stringResource(R.string.sustitucion_faltante_pendiente),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!estado.error.isNullOrBlank()) {
                        item {
                            Text(estado.error, color = MaterialTheme.colorScheme.error)
                            if (!estado.faltantePendienteLocal && estado.propuesta == null) {
                                TextButton(onClick = alReintentar) {
                                    Text(stringResource(R.string.reintentar))
                                }
                            }
                        }
                    }
                    if (estado.propuesta != null) {
                        item {
                            PanelPropuesta(estado, alConsultarEstado)
                        }
                    }
                    val sinCandidatos = estado.sinStockAlmacen &&
                        !estado.faltantePendienteLocal &&
                        !EstadoSustitucion.esPendiente(estado.propuesta?.estado)
                    val rechazo = EstadoSustitucion.esRechazada(estado.propuesta?.estado)
                    val avisoEnviado = estado.avisoSinStockEnviado ||
                        EstadoSustitucion.esSinStock(estado.propuesta?.estado)
                    if (sinCandidatos && !estado.faltantePendienteLocal) {
                        item {
                            Text(
                                text = stringResource(R.string.informar_sin_stock),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.informar_sin_stock_ayuda))
                            Spacer(Modifier.height(8.dp))
                            if (avisoEnviado) {
                                Text(
                                    text = stringResource(R.string.informar_sin_stock_ok),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                if (estado.resolviendo) {
                                    Text(stringResource(R.string.eliminando_linea))
                                    Spacer(Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = { confirmarInformar = true },
                                    enabled = !estado.resolviendo && !estado.enviando,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.informar_sin_stock))
                                }
                            }
                        }
                    }
                    if (rechazo && !estado.faltantePendienteLocal) {
                        item {
                            Text(stringResource(R.string.sustitucion_rechazo_nueva))
                            Spacer(Modifier.height(8.dp))
                            if (estado.resolviendo) {
                                Text(stringResource(R.string.eliminando_linea))
                                Spacer(Modifier.height(8.dp))
                            }
                            Button(
                                onClick = { confirmarEliminacion = true },
                                enabled = !estado.resolviendo && !estado.enviando,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.resolver_sin_producto))
                            }
                        }
                    }
                    if (puedeMostrarCandidatos(estado)) {
                        items(estado.candidatos, key = { it.id }) { candidato ->
                            TarjetaCandidato(
                                candidato = candidato,
                                seleccionado = estado.seleccionadoId == candidato.id,
                                habilitado = !estado.enviando &&
                                    !EstadoSustitucion.esPendiente(estado.propuesta?.estado),
                                alSeleccionar = { alSeleccionar(candidato.id) }
                            )
                        }
                        if (estado.candidatos.isNotEmpty() &&
                            !EstadoSustitucion.esPendiente(estado.propuesta?.estado)
                        ) {
                            item {
                                if (estado.enviando) {
                                    Text(stringResource(R.string.sustitucion_enviando))
                                    Spacer(Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = alProponer,
                                    enabled = !estado.enviando &&
                                        !estado.seleccionadoId.isNullOrBlank() &&
                                        !estado.faltantePendienteLocal,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.sustitucion_proponer))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmarInformar) {
        AlertDialog(
            onDismissRequest = { confirmarInformar = false },
            title = { Text(stringResource(R.string.confirmar_informar_titulo)) },
            text = { Text(stringResource(R.string.confirmar_informar_mensaje)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarInformar = false
                        alInformarSinStock()
                    },
                    enabled = !estado.resolviendo
                ) {
                    Text(stringResource(R.string.confirmar_informar_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarInformar = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
    if (confirmarEliminacion) {
        AlertDialog(
            onDismissRequest = { confirmarEliminacion = false },
            title = { Text(stringResource(R.string.confirmar_eliminar_titulo)) },
            text = { Text(stringResource(R.string.confirmar_eliminar_mensaje)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminacion = false
                        alResolverSinProducto()
                    },
                    enabled = !estado.resolviendo
                ) {
                    Text(stringResource(R.string.confirmar_eliminar_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminacion = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

private fun puedeMostrarCandidatos(estado: SustitucionUiState): Boolean {
    if (estado.faltantePendienteLocal) return false
    if (estado.candidatos.isEmpty()) return false
    val estadoProp = estado.propuesta?.estado
    return estadoProp == null ||
        estadoProp.equals(EstadoSustitucion.RECHAZADA, true) ||
        estadoProp.equals(EstadoSustitucion.CANCELADA, true)
}

@Composable
private fun EncabezadoSustitucion(estado: SustitucionUiState) {
    Column {
        if (estado.nombreProductoOriginal.isNotBlank()) {
            Text(
                text = estado.nombreProductoOriginal,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (estado.cantidadLinea > 0) {
            Text(stringResource(R.string.cantidad_fija_sustitucion, estado.cantidadLinea))
        }
        if (estado.precioOriginalCentimos > 0) {
            Text(
                stringResource(
                    R.string.sustitucion_precio_original,
                    estado.precioOriginalCentimos.formatearSoles()
                )
            )
        }
        Text(stringResource(R.string.sustitucion_ayuda))
    }
}

@Composable
private fun PanelPropuesta(
    estado: SustitucionUiState,
    alConsultarEstado: () -> Unit
) {
    val propuesta = estado.propuesta ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.sustitucion_estado,
                textoEstadoSustitucion(propuesta.estado)
            ),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(
                R.string.sustitucion_precios,
                propuesta.precioOriginalCentimos.formatearSoles(),
                propuesta.precioSustitutoCentimos.formatearSoles()
            )
        )
        Text(stringResource(R.string.cantidad_solicitada, propuesta.cantidad))
        if (EstadoSustitucion.esPendiente(propuesta.estado)) {
            val restante = estado.tiempoRestanteTexto
            if (!restante.isNullOrBlank()) {
                Text(stringResource(R.string.sustitucion_tiempo_restante, restante))
            }
            TextButton(
                onClick = alConsultarEstado,
                enabled = !estado.consultandoEstado && !estado.enviando
            ) {
                Text(stringResource(R.string.sustitucion_actualizar_estado))
            }
        }
    }
}

@Composable
private fun TarjetaCandidato(
    candidato: CandidatoSustituto,
    seleccionado: Boolean,
    habilitado: Boolean,
    alSeleccionar: () -> Unit
) {
    val borde = if (seleccionado) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borde, RoundedCornerShape(8.dp))
            .clickable(enabled = habilitado, onClick = alSeleccionar)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImagenProductoRemota(
            url = candidato.imagenUrl,
            descripcion = candidato.nombre,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(candidato.nombre, fontWeight = FontWeight.Medium)
            if (!candidato.marca.isNullOrBlank()) {
                Text(candidato.marca)
            }
            Text(stringResource(R.string.cantidad_solicitada, candidato.cantidad))
            Text(
                stringResource(
                    R.string.sustitucion_precio_original,
                    candidato.precioOriginalCentimos.formatearSoles()
                )
            )
            Text(
                stringResource(
                    R.string.sustitucion_precio_sustituto,
                    candidato.precioSustitutoCentimos.formatearSoles()
                )
            )
            if (!candidato.pasillo.isNullOrBlank()) {
                Text(stringResource(R.string.pasillo_nombre, candidato.pasillo))
            }
        }
    }
}
