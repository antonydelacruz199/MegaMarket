package com.megamarket.cliente.ui.pantallas.catalogo

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.modelo.nombreCategoria
import com.megamarket.cliente.ui.componentes.EstadoVacio
import com.megamarket.cliente.ui.componentes.EstructuraCliente
import com.megamarket.cliente.ui.componentes.TarjetaProducto
import com.megamarket.cliente.ui.navegacion.Ruta
import com.megamarket.cliente.viewmodel.ViewModelCatalogo

@Composable
fun PantallaCatalogo(
    viewModel: ViewModelCatalogo,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirProducto: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ruta = if (estado.soloOfertas) Ruta.Ofertas.ruta else Ruta.Catalogo.ruta
    val titulo = if (estado.soloOfertas) "Ofertas" else "Catálogo"

    EstructuraCliente(
        rutaActual = ruta,
        titulo = titulo,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        when {
            estado.cargando -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            estado.error != null -> EstadoVacio(
                icono = Icons.Default.Search,
                titulo = "No se pudo cargar",
                descripcion = estado.error.orEmpty(),
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Reintentar",
                alPulsarAccion = viewModel::cargar
            )

            estado.vacio -> EstadoVacio(
                icono = Icons.Default.Search,
                titulo = "Catálogo vacío",
                descripcion = "Aún no hay productos. Cuando el administrador publique el catálogo, lo verás aquí.",
                modifier = Modifier.padding(relleno)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
            ) {
                OutlinedTextField(
                    value = estado.consulta,
                    onValueChange = viewModel::actualizarConsulta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Buscar por nombre o marca") },
                    singleLine = true
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = estado.categoriaId == null,
                            onClick = { viewModel.seleccionarCategoria(null) },
                            label = { Text("Todas") }
                        )
                    }
                    items(estado.categorias) { categoriaId ->
                        FilterChip(
                            selected = estado.categoriaId == categoriaId,
                            onClick = { viewModel.seleccionarCategoria(categoriaId) },
                            label = { Text(nombreCategoria(categoriaId)) }
                        )
                    }
                }
                if (estado.sinResultados) {
                    EstadoVacio(
                        icono = Icons.Default.Search,
                        titulo = "Sin resultados",
                        descripcion = "No se encontraron productos con esa búsqueda o filtro.",
                        etiquetaAccion = "Limpiar filtros",
                        alPulsarAccion = viewModel::limpiarFiltros
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(estado.visibles, key = { it.id }) { producto ->
                            TarjetaProducto(
                                producto = producto,
                                alPulsar = { alAbrirProducto(producto.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
