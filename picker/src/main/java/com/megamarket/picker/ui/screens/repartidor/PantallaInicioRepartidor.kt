package com.megamarket.picker.ui.screens.repartidor

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.PedidoReparto
import com.megamarket.picker.state.ListaRepartidorUiState
import com.megamarket.picker.ui.components.ChipEstado
import com.megamarket.picker.ui.components.DestinoOperativo
import com.megamarket.picker.ui.components.EstadoVacio
import com.megamarket.picker.ui.components.EstructuraOperativa
import com.megamarket.picker.ui.components.TonoChip
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.navigation.Ruta
import com.megamarket.picker.ui.navigation.SeccionReparto
import com.megamarket.picker.ui.textoFecha
import com.megamarket.picker.ui.theme.Espaciado
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PantallaInicioRepartidor(
    nombre: String,
    rutaActual: String,
    seccion: SeccionReparto,
    estado: ListaRepartidorUiState,
    alNavegarSeccion: (String) -> Unit,
    alCargar: () -> Unit,
    alCargarMas: () -> Unit,
    alTomar: (String) -> Unit,
    alAbrir: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val etiquetaCamino = stringResource(R.string.en_camino)
    val etiquetaListos = stringResource(R.string.listos_para_entregar)
    val etiquetaEntregados = stringResource(R.string.entregados)
    val destinos = remember(etiquetaCamino, etiquetaListos, etiquetaEntregados) {
        listOf(
            DestinoOperativo(Ruta.RepartidorEnCamino.ruta, etiquetaCamino, Icons.Default.DeliveryDining),
            DestinoOperativo(Ruta.RepartidorListos.ruta, etiquetaListos, Icons.Default.Inventory),
            DestinoOperativo(Ruta.RepartidorEntregados.ruta, etiquetaEntregados, Icons.Default.TaskAlt)
        )
    }
    // Evita pintar la cola anterior mientras el VM compartido cambia de pestaña.
    val seccionLista = estado.seccion == seccion
    val pedidos = if (seccionLista) estado.pedidos else emptyList()
    val cargando = !seccionLista || (estado.cargando && pedidos.isEmpty())

    LifecycleResumeEffect(rutaActual, seccion) {
        if (estado.seccion == seccion) {
            alCargar()
        }
        onPauseOrDispose { }
    }

    EstructuraOperativa(
        titulo = stringResource(R.string.reparto),
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
                text = stringResource(R.string.saludo, nombre.ifBlank { stringResource(R.string.reparto) }),
                modifier = Modifier.padding(horizontal = Espaciado.xl, vertical = Espaciado.sm),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                cargando -> {
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
                estado.error != null && pedidos.isEmpty() -> {
                    EstadoVacio(
                        icono = Icons.Default.Warning,
                        titulo = "No se pudo cargar",
                        descripcion = estado.error,
                        etiquetaAccion = stringResource(R.string.reintentar),
                        alPulsarAccion = alCargar
                    )
                }
                pedidos.isEmpty() -> {
                    EstadoVacio(
                        icono = when (seccion) {
                            SeccionReparto.LISTOS -> Icons.Default.Inventory
                            SeccionReparto.ENTREGADOS -> Icons.Default.TaskAlt
                            SeccionReparto.EN_CAMINO -> Icons.Default.DeliveryDining
                        },
                        titulo = when (seccion) {
                            SeccionReparto.LISTOS -> stringResource(R.string.listos_para_entregar)
                            SeccionReparto.ENTREGADOS -> stringResource(R.string.entregados)
                            SeccionReparto.EN_CAMINO -> stringResource(R.string.en_camino)
                        },
                        descripcion = when (seccion) {
                            SeccionReparto.LISTOS -> stringResource(R.string.sin_listos)
                            SeccionReparto.ENTREGADOS -> stringResource(R.string.repartidor_vacio)
                            SeccionReparto.EN_CAMINO -> stringResource(R.string.sin_en_camino)
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
                        pedidos = pedidos,
                        tomandoId = estado.tomandoId,
                        hayMas = estado.hayMas,
                        cargandoMas = estado.cargandoMas,
                        pagina = estado.pagina,
                        alTomar = alTomar,
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
    seccion: SeccionReparto,
    pedidos: List<PedidoReparto>,
    tomandoId: String?,
    hayMas: Boolean,
    cargandoMas: Boolean,
    pagina: Int,
    alTomar: (String) -> Unit,
    alAbrir: (String) -> Unit,
    alCargarMas: () -> Unit
) {
    val lista = rememberLazyListState()
    LaunchedEffect(seccion) {
        lista.scrollToItem(0)
    }
    LaunchedEffect(lista, hayMas, cargandoMas, seccion) {
        snapshotFlow {
            val info = lista.layoutInfo
            val ultimo = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimo >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { cercaDelFinal ->
                if (cercaDelFinal && hayMas && !cargandoMas) {
                    alCargarMas()
                }
            }
    }

    LazyColumn(
        state = lista,
        contentPadding = PaddingValues(horizontal = Espaciado.lg, vertical = Espaciado.sm),
        verticalArrangement = Arrangement.spacedBy(Espaciado.md)
    ) {
        items(pedidos, key = { it.id }) { pedido ->
            when (seccion) {
                SeccionReparto.LISTOS -> TarjetaDisponible(
                    pedido = pedido,
                    tomando = tomandoId == pedido.id,
                    bloqueado = tomandoId != null,
                    alTomar = alTomar
                )
                SeccionReparto.EN_CAMINO -> TarjetaPedido(
                    pedido = pedido,
                    etiqueta = stringResource(R.string.estado_en_camino),
                    tono = TonoChip.Secundario,
                    alAbrir = alAbrir
                )
                SeccionReparto.ENTREGADOS -> TarjetaPedido(
                    pedido = pedido,
                    etiqueta = stringResource(R.string.estado_entregado),
                    tono = TonoChip.Exito,
                    alAbrir = alAbrir
                )
            }
        }
        if (hayMas) {
            item(key = "cargar_mas_${seccion}_$pagina") {
                if (cargandoMas) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Espaciado.md),
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
private fun TarjetaDisponible(
    pedido: PedidoReparto,
    tomando: Boolean,
    bloqueado: Boolean,
    alTomar: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Espaciado.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        textoPedido(pedido.id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        textoFecha(pedido.fecha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChipEstado(stringResource(R.string.estado_listo), TonoChip.Primario)
            }
            Spacer(modifier = Modifier.height(Espaciado.sm))
            Text(
                pedido.totalCentimos.formatearSoles(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Espaciado.md))
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
                        stringResource(R.string.tomar_entrega)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun TarjetaPedido(
    pedido: PedidoReparto,
    etiqueta: String,
    tono: TonoChip,
    alAbrir: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { alAbrir(pedido.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Espaciado.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        textoPedido(pedido.id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        textoFecha(pedido.fecha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChipEstado(etiqueta, tono)
            }
            Spacer(modifier = Modifier.height(Espaciado.sm))
            Text(
                pedido.totalCentimos.formatearSoles(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Espaciado.sm))
            Text(
                text = stringResource(R.string.abrir_entrega),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun textoPedido(id: String): String = "Pedido ${id.take(8)}"
