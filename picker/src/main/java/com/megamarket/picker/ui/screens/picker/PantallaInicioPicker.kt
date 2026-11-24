package com.megamarket.picker.ui.screens.picker

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.PedidoAsignado
import com.megamarket.picker.data.repository.PedidoDespacho
import com.megamarket.picker.data.repository.PedidoDisponible
import com.megamarket.picker.state.ListaPickerUiState
import com.megamarket.picker.ui.components.ChipEstado
import com.megamarket.picker.ui.components.DestinoOperativo
import com.megamarket.picker.ui.components.EstadoVacio
import com.megamarket.picker.ui.components.EstructuraOperativa
import com.megamarket.picker.ui.components.TonoChip
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.navigation.Ruta
import com.megamarket.picker.ui.navigation.SeccionPicker
import com.megamarket.picker.ui.textoFecha
import com.megamarket.picker.ui.theme.Espaciado

@Composable
fun PantallaInicioPicker(
    nombre: String,
    rutaActual: String,
    seccion: SeccionPicker,
    estado: ListaPickerUiState,
    alNavegarSeccion: (String) -> Unit,
    alCargar: () -> Unit,
    alCargarMas: () -> Unit = {},
    alTomar: (String) -> Unit,
    alMandarDelivery: (String) -> Unit = {},
    alAbrir: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val etiquetaDisp = stringResource(R.string.pedidos_disponibles)
    val etiquetaPrep = stringResource(R.string.en_preparacion_tab)
    val etiquetaDesp = stringResource(R.string.por_despachar_tab)
    val destinos = remember(etiquetaDisp, etiquetaPrep, etiquetaDesp) {
        listOf(
            DestinoOperativo(Ruta.PickerDisponibles.ruta, etiquetaDisp, Icons.Default.PendingActions),
            DestinoOperativo(Ruta.PickerPreparacion.ruta, etiquetaPrep, Icons.Default.Assignment),
            DestinoOperativo(Ruta.PickerDespacho.ruta, etiquetaDesp, Icons.Default.LocalShipping)
        )
    }
    val seccionLista = estado.seccion == seccion

    LifecycleResumeEffect(rutaActual, seccion) {
        if (estado.seccion == seccion) {
            alCargar()
        }
        onPauseOrDispose { }
    }

    EstructuraOperativa(
        titulo = stringResource(R.string.preparacion),
        rutaActual = rutaActual,
        destinos = destinos,
        alNavegar = alNavegarSeccion,
        alRefrescar = alCargar,
        alCerrarSesion = alCerrarSesion
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(
                    R.string.saludo,
                    nombre.ifBlank { stringResource(R.string.preparacion) }
                ),
                modifier = Modifier.padding(horizontal = Espaciado.xl, vertical = Espaciado.sm),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val vacio = when (seccion) {
                SeccionPicker.DISPONIBLES -> !seccionLista || estado.disponibles.isEmpty()
                SeccionPicker.PREPARACION -> !seccionLista || estado.asignados.isEmpty()
                SeccionPicker.DESPACHO -> !seccionLista || estado.despachables.isEmpty()
            }
            when {
                (!seccionLista || estado.cargando) && vacio -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(Espaciado.md))
                        Text(
                            text = stringResource(R.string.cargando_pedidos),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                estado.error != null && vacio -> {
                    EstadoVacio(
                        icono = Icons.Default.Warning,
                        titulo = "No se pudo cargar",
                        descripcion = estado.error,
                        etiquetaAccion = stringResource(R.string.reintentar),
                        alPulsarAccion = alCargar
                    )
                }
                vacio -> {
                    EstadoVacio(
                        icono = when (seccion) {
                            SeccionPicker.DISPONIBLES -> Icons.Default.PendingActions
                            SeccionPicker.PREPARACION -> Icons.Default.Assignment
                            SeccionPicker.DESPACHO -> Icons.Default.LocalShipping
                        },
                        titulo = when (seccion) {
                            SeccionPicker.DISPONIBLES -> etiquetaDisp
                            SeccionPicker.PREPARACION -> etiquetaPrep
                            SeccionPicker.DESPACHO -> etiquetaDesp
                        },
                        descripcion = when (seccion) {
                            SeccionPicker.DISPONIBLES -> stringResource(R.string.sin_disponibles)
                            SeccionPicker.PREPARACION -> stringResource(R.string.sin_asignados)
                            SeccionPicker.DESPACHO -> stringResource(R.string.sin_despacho)
                        },
                        etiquetaAccion = stringResource(R.string.reintentar),
                        alPulsarAccion = alCargar
                    )
                }
                else -> {
                    if (estado.error != null) {
                        Text(
                            text = estado.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(
                                horizontal = Espaciado.xl,
                                vertical = Espaciado.xs
                            )
                        )
                    }
                    ListaSeccion(
                        seccion = seccion,
                        estado = estado,
                        alTomar = alTomar,
                        alMandarDelivery = alMandarDelivery,
                        alAbrir = alAbrir,
                        alCargarMas = alCargarMas
                    )
                }
            }
        }
    }
}

@Composable
private fun ListaSeccion(
    seccion: SeccionPicker,
    estado: ListaPickerUiState,
    alTomar: (String) -> Unit,
    alMandarDelivery: (String) -> Unit,
    alAbrir: (String) -> Unit,
    alCargarMas: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Espaciado.lg, vertical = Espaciado.sm),
        verticalArrangement = Arrangement.spacedBy(Espaciado.md)
    ) {
        when (seccion) {
            SeccionPicker.DISPONIBLES -> {
                items(estado.disponibles, key = { it.id }) { pedido ->
                    TarjetaDisponible(
                        pedido = pedido,
                        tomando = estado.tomandoId == pedido.id,
                        bloqueado = estado.tomandoId != null || estado.despachandoId != null,
                        alTomar = alTomar
                    )
                }
            }
            SeccionPicker.PREPARACION -> {
                items(estado.asignados, key = { it.id }) { pedido ->
                    TarjetaAsignado(pedido, alAbrir)
                }
            }
            SeccionPicker.DESPACHO -> {
                items(estado.despachables, key = { "despacho-${it.id}" }) { pedido ->
                    TarjetaDespacho(
                        pedido = pedido,
                        despachando = estado.despachandoId == pedido.id,
                        bloqueado = estado.despachandoId != null || estado.tomandoId != null,
                        alMandar = alMandarDelivery
                    )
                }
            }
        }
        if (estado.hayMas) {
            item {
                if (estado.cargandoMas) {
                    Row(
                        Modifier.fillMaxWidth().padding(Espaciado.md),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    OutlinedButton(
                        onClick = alCargarMas,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cargar_mas))
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPedidoBase(
    titulo: String,
    fecha: String,
    chip: String,
    tonoChip: TonoChip,
    total: Long,
    onClick: (() -> Unit)? = null,
    accion: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Espaciado.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        fecha,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChipEstado(texto = chip, tono = tonoChip)
            }
            Spacer(modifier = Modifier.height(Espaciado.sm))
            Text(
                total.formatearSoles(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (accion != null) {
                Spacer(modifier = Modifier.height(Espaciado.md))
                accion()
            }
        }
    }
}

@Composable
private fun TarjetaDespacho(
    pedido: PedidoDespacho,
    despachando: Boolean,
    bloqueado: Boolean,
    alMandar: (String) -> Unit
) {
    TarjetaPedidoBase(
        titulo = textoPedido(pedido.id),
        fecha = textoFecha(pedido.fecha),
        chip = etiquetaDespachoCorta(pedido),
        tonoChip = TonoChip.Secundario,
        total = pedido.totalCentimos
    ) {
        Button(
            onClick = { alMandar(pedido.id) },
            enabled = !bloqueado,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(
                text = if (despachando) {
                    stringResource(R.string.mandando_delivery)
                } else {
                    stringResource(R.string.mandar_delivery)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun TarjetaAsignado(pedido: PedidoAsignado, alAbrir: (String) -> Unit) {
    TarjetaPedidoBase(
        titulo = textoPedido(pedido.id),
        fecha = textoFecha(pedido.fecha),
        chip = stringResource(R.string.productos_count, pedido.cantidadLineas),
        tonoChip = TonoChip.Primario,
        total = pedido.totalCentimos,
        onClick = { alAbrir(pedido.id) }
    )
}

@Composable
private fun TarjetaDisponible(
    pedido: PedidoDisponible,
    tomando: Boolean,
    bloqueado: Boolean,
    alTomar: (String) -> Unit
) {
    TarjetaPedidoBase(
        titulo = textoPedido(pedido.id),
        fecha = textoFecha(pedido.fecha),
        chip = etiquetaDisponible(pedido),
        tonoChip = TonoChip.Exito,
        total = pedido.totalCentimos
    ) {
        Button(
            onClick = { alTomar(pedido.id) },
            enabled = !bloqueado,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (tomando) {
                    stringResource(R.string.tomando)
                } else {
                    stringResource(R.string.tomar_pedido)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun textoPedido(id: String): String = "Pedido ${id.take(8)}"

private fun etiquetaDisponible(pedido: PedidoDisponible): String = when {
    pedido.estado == "PAGADO" -> "Pagado"
    pedido.modalidad.equals("CONTRAENTREGA", ignoreCase = true) -> "Contraentrega"
    else -> "Disponible"
}

private fun etiquetaDespachoCorta(pedido: PedidoDespacho): String = when {
    pedido.estado.equals("PAGADO", ignoreCase = true) -> "Pagado"
    pedido.modalidad.equals("CONTRAENTREGA", ignoreCase = true) -> "Contraentrega"
    else -> "Listo"
}
