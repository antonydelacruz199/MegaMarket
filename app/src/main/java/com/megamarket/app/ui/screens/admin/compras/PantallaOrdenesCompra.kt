package com.megamarket.app.ui.screens.admin.compras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.data.repository.EstadoOrden
import com.megamarket.app.ui.components.CargandoCentrado
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.EstructuraAdmin
import com.megamarket.app.ui.components.TarjetaOrdenCompra
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.viewmodel.OrdenesCompraViewModel

private val FILTROS: List<EstadoOrden?> = listOf(null) + EstadoOrden.entries.filter { it != EstadoOrden.DESCONOCIDO }

@Composable
fun PantallaOrdenesCompra(
    viewModel: OrdenesCompraViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alDetalle: (String) -> Unit,
    alNueva: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargar()
        onPauseOrDispose { }
    }

    EstructuraAdmin(
        rutaActual = Ruta.OrdenesCompraAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Órdenes de compra",
        botonFlotante = {
            FloatingActionButton(
                onClick = alNueva,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Nueva orden de compra")
            }
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FILTROS, key = { it?.name ?: "TODAS" }) { filtro ->
                    FilterChip(
                        selected = estado.filtroEstado == filtro,
                        onClick = { viewModel.seleccionarFiltro(filtro) },
                        label = { Text(filtro?.etiqueta ?: "Todas") }
                    )
                }
            }

            when {
                estado.cargando && estado.ordenes.isEmpty() -> CargandoCentrado(Modifier.weight(1f))

                estado.error != null && estado.ordenes.isEmpty() -> EstadoVacio(
                    icono = Icons.Default.ShoppingCart,
                    titulo = "No se pudo cargar",
                    descripcion = estado.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    etiquetaAccion = "Reintentar",
                    alPulsarAccion = viewModel::cargar
                )

                estado.ordenes.isEmpty() -> EstadoVacio(
                    icono = Icons.Default.ShoppingCart,
                    titulo = if (estado.filtroEstado == null) "Sin órdenes de compra" else "Sin resultados",
                    descripcion = if (estado.filtroEstado == null) {
                        "Crea la primera orden con el botón +"
                    } else {
                        "No hay órdenes en estado ${estado.filtroEstado?.etiqueta?.lowercase()}"
                    },
                    modifier = Modifier.weight(1f)
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    estado.error?.let { mensaje ->
                        item { Text(mensaje, color = MaterialTheme.colorScheme.error) }
                    }
                    items(estado.ordenes, key = { it.id }) { orden ->
                        TarjetaOrdenCompra(orden = orden, alPulsar = { alDetalle(orden.id) })
                    }
                    if (estado.hayMas) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::cargarMas,
                                enabled = !estado.cargandoMas,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (estado.cargandoMas) "Cargando…" else "Cargar más") }
                        }
                    }
                }
            }
        }
    }
}
