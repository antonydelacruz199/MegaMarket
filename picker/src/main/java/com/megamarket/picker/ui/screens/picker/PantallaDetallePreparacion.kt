package com.megamarket.picker.ui.screens.picker

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.LineaPreparacion
import com.megamarket.picker.data.repository.listoParaSalida
import com.megamarket.picker.data.repository.resumenCierre
import com.megamarket.picker.state.DetallePreparacionUiState
import com.megamarket.picker.ui.components.ChipEstado
import com.megamarket.picker.ui.components.ImagenProductoRemota
import com.megamarket.picker.ui.components.TonoChip
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.textoEstadoLinea
import com.megamarket.picker.ui.textoFecha
import com.megamarket.picker.ui.theme.Espaciado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetallePreparacion(
    estado: DetallePreparacionUiState,
    alVolver: () -> Unit,
    alReintentar: () -> Unit,
    alEscanear: (String) -> Unit,
    alSustituir: (String) -> Unit,
    alResolverSinProducto: (String) -> Unit = {},
    alMarcarListo: () -> Unit = {}
) {
    var confirmarCierre by remember { mutableStateOf(false) }
    var confirmarEliminacion by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.detalle_preparacion),
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
            estado.cargando && estado.preparacion == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            estado.error != null && estado.preparacion == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(estado.error, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = alReintentar) {
                        Text(stringResource(R.string.reintentar))
                    }
                }
            }
            estado.preparacion != null -> {
                val preparacion = estado.preparacion
                if (confirmarCierre) {
                    AlertDialog(
                        onDismissRequest = { confirmarCierre = false },
                        title = { Text(stringResource(R.string.confirmar_cierre_titulo)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.confirmar_cierre_mensaje))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(preparacion.resumenCierre())
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    confirmarCierre = false
                                    alMarcarListo()
                                },
                                enabled = !estado.marcandoListo
                            ) {
                                Text(stringResource(R.string.confirmar_cierre_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmarCierre = false }) {
                                Text(stringResource(R.string.cancelar))
                            }
                        }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Pedido ${preparacion.id.take(8)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(textoFecha(preparacion.fecha))
                        Text(preparacion.totalCentimos.formatearSoles())
                        if (estado.error != null) {
                            Text(estado.error, color = MaterialTheme.colorScheme.error)
                            if (!estado.codigoError.isNullOrBlank()) {
                                Text(
                                    text = estado.codigoError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (estado.listoOk) {
                            Text(
                                text = stringResource(R.string.pedido_listo_ok),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else if (preparacion.listoParaSalida()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { confirmarCierre = true },
                                enabled = !estado.marcandoListo,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (estado.marcandoListo) {
                                        stringResource(R.string.marcando_listo)
                                    } else {
                                        stringResource(R.string.marcar_listo)
                                    }
                                )
                            }
                        }
                    }
                    preparacion.grupos.forEach { grupo ->
                        item(key = "pasillo-${grupo.pasillo}") {
                            Text(
                                text = if (grupo.pasillo == "Sin pasillo") {
                                    stringResource(R.string.sin_pasillo)
                                } else {
                                    stringResource(R.string.pasillo_nombre, grupo.pasillo)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(grupo.lineas, key = { it.id }) { linea ->
                            LineaProducto(
                                linea = linea,
                                resolviendo = estado.resolviendoDetalleId == linea.id,
                                alEscanear = alEscanear,
                                alSustituir = alSustituir,
                                alResolver = { confirmarEliminacion = linea.id }
                            )
                        }
                    }
                }
            }
        }
    }

    val detalleAEliminar = confirmarEliminacion
    if (detalleAEliminar != null) {
        AlertDialog(
            onDismissRequest = { confirmarEliminacion = null },
            title = { Text(stringResource(R.string.confirmar_eliminar_titulo)) },
            text = { Text(stringResource(R.string.confirmar_eliminar_mensaje)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminacion = null
                        alResolverSinProducto(detalleAEliminar)
                    },
                    enabled = estado.resolviendoDetalleId == null
                ) {
                    Text(stringResource(R.string.confirmar_eliminar_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminacion = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun LineaProducto(
    linea: LineaPreparacion,
    resolviendo: Boolean,
    alEscanear: (String) -> Unit,
    alSustituir: (String) -> Unit,
    alResolver: () -> Unit
) {
    val faltanteConfirmado = linea.estadoPicking.equals("FALTANTE", ignoreCase = true) &&
        !linea.faltantePendiente
    val escaneoSustituto = linea.requiereEscaneoSustituto()
    val unidadesEfectivas = linea.cantidadArmada + linea.cantidadPendienteLocal
    val cantidadCompleta = linea.cantidad > 0 && unidadesEfectivas >= linea.cantidad
    val puedeEscanear = when {
        escaneoSustituto -> !cantidadCompleta
        linea.estadoPicking.equals("PENDIENTE", true) -> !cantidadCompleta
        linea.estadoPicking.equals("ARMADO", true) -> !cantidadCompleta
        else -> false
    }
    val nombreMostrar = linea.nombreVigente()
    val tonoEstado = when {
        linea.estadoPicking.equals("ARMADO", true) -> TonoChip.Exito
        linea.estadoPicking.equals("FALTANTE", true) -> TonoChip.Advertencia
        else -> TonoChip.Neutro
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Espaciado.md),
            verticalAlignment = Alignment.Top
        ) {
            ImagenProductoRemota(
                url = linea.imagenUrl,
                descripcion = nombreMostrar,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.width(Espaciado.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(nombreMostrar, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(Espaciado.xs))
                ChipEstado(textoEstadoLinea(linea.estadoPicking), tonoEstado)
                if (escaneoSustituto) {
                    Text(
                        text = stringResource(R.string.sustituto_pendiente_escaneo),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Espaciado.xs)
                    )
                    if (!linea.nombre.equals(nombreMostrar, ignoreCase = true)) {
                        Text(
                            text = stringResource(R.string.producto_original, linea.nombre),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.cantidad_solicitada, linea.cantidad),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Espaciado.xs)
                )
                Text(
                    text = stringResource(R.string.cantidad_armada, linea.cantidadArmada),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (linea.cantidadPendienteLocal > 0) {
                    Text(
                        text = stringResource(R.string.pendiente_local, linea.cantidadPendienteLocal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (linea.faltantePendiente) {
                    Text(
                        text = stringResource(R.string.faltante_pendiente),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = if (linea.pasillo.isNullOrBlank()) {
                        stringResource(R.string.sin_pasillo)
                    } else {
                        stringResource(R.string.pasillo_nombre, linea.pasillo)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (puedeEscanear) {
                    Spacer(modifier = Modifier.height(Espaciado.sm))
                    Button(
                        onClick = { alEscanear(linea.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (escaneoSustituto) {
                                stringResource(R.string.escanear_sustituto)
                            } else {
                                stringResource(R.string.escanear)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (linea.faltantePendiente) {
                    Text(
                        text = stringResource(R.string.sustitucion_faltante_pendiente),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Espaciado.xs)
                    )
                } else if (faltanteConfirmado && !escaneoSustituto) {
                    Spacer(modifier = Modifier.height(Espaciado.sm))
                    OutlinedButton(
                        onClick = { alSustituir(linea.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(stringResource(R.string.proponer_sustituto))
                    }
                    if (linea.resolubleSinProducto) {
                        Spacer(modifier = Modifier.height(Espaciado.sm))
                        OutlinedButton(
                            onClick = alResolver,
                            enabled = !resolviendo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(stringResource(R.string.informar_sin_stock))
                        }
                    }
                }
            }
        }
    }
}
