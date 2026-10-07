package com.megamarket.app.ui.screens.admin.productos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.model.FiltroProducto
import com.megamarket.app.ui.components.BarraBusqueda
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.EstructuraAdmin
import com.megamarket.app.ui.components.TarjetaProductoAdmin
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.ui.theme.MegaAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorNeutro
import com.megamarket.app.ui.theme.MegaContenedorPrimario
import com.megamarket.app.ui.theme.MegaContenedorSecundario
import com.megamarket.app.ui.theme.MegaNeutro
import com.megamarket.app.ui.theme.MegaPrimario
import com.megamarket.app.ui.theme.MegaSecundario
import com.megamarket.app.ui.theme.MegaSobrePrimario
import com.megamarket.app.viewmodel.ProductoViewModel

private data class EstiloFiltro(
    val contenedor: Color,
    val acento: Color
)

private fun FiltroProducto.estilo(): EstiloFiltro = when (this) {
    FiltroProducto.TODOS -> EstiloFiltro(MegaContenedorNeutro, MegaNeutro)
    FiltroProducto.ACTIVOS -> EstiloFiltro(MegaContenedorPrimario, MegaPrimario)
    FiltroProducto.OFERTAS -> EstiloFiltro(MegaContenedorSecundario, MegaSecundario)
    FiltroProducto.STOCK_BAJO -> EstiloFiltro(MegaContenedorAdvertencia, MegaAdvertencia)
}

@Composable
fun PantallaProductosAdmin(
    viewModel: ProductoViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargarProductos()
        onPauseOrDispose { }
    }

    EstructuraAdmin(
        rutaActual = Ruta.ProductosAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Productos",
        botonFlotante = {
            FloatingActionButton(
                onClick = { alNavegar(Ruta.CrearProductoAdmin.ruta) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo producto"
                )
            }
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                BarraBusqueda(
                    consulta = estado.consulta,
                    alCambiarConsulta = viewModel::actualizarConsulta
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FiltroProducto.entries, key = { it.name }) { filtro ->
                        ChipFiltro(
                            filtro = filtro,
                            seleccionado = estado.filtro == filtro,
                            alPulsar = { viewModel.seleccionarFiltro(filtro) }
                        )
                    }
                }
            }
            val visibles = estado.visibles
            if (estado.error != null && estado.productos.isEmpty()) {
                EstadoVacio(
                    icono = Icons.Default.Add,
                    titulo = "No se pudo cargar",
                    descripcion = estado.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    etiquetaAccion = "Reintentar",
                    alPulsarAccion = viewModel::cargarProductos
                )
            } else if (visibles.isEmpty()) {
                EstadoVacio(
                    icono = Icons.Default.Add,
                    titulo = if (estado.productos.isEmpty()) "Sin productos" else "Sin resultados",
                    descripcion = if (estado.productos.isEmpty()) {
                        "Registra el primer producto con el botón +"
                    } else {
                        "No hay productos con ese filtro"
                    },
                    modifier = Modifier.weight(1f)
                )
            } else LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibles, key = { it.id }) { producto ->
                    TarjetaProductoAdmin(
                        producto = producto,
                        alEditar = { alNavegar(Ruta.EditarProductoAdmin.crear(producto.id)) },
                        cargarImagen = viewModel::cargarImagen
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipFiltro(
    filtro: FiltroProducto,
    seleccionado: Boolean,
    alPulsar: () -> Unit
) {
    val estilo = filtro.estilo()
    FilterChip(
        selected = seleccionado,
        onClick = alPulsar,
        label = {
            Text(
                text = filtro.etiqueta,
                fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = estilo.contenedor,
            labelColor = estilo.acento,
            selectedContainerColor = estilo.acento,
            selectedLabelColor = MegaSobrePrimario
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = seleccionado,
            borderColor = estilo.acento.copy(alpha = 0.35f),
            selectedBorderColor = estilo.acento
        )
    )
}
