package com.megamarket.cliente.ui.screens.catalogo

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.ui.components.BannerFeriaPrimavera
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.components.TarjetaProducto
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.viewmodel.CatalogoViewModel

@Composable
fun PantallaCatalogo(
    viewModel: CatalogoViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirProducto: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargar()
        onPauseOrDispose { }
    }
    val ruta = if (estado.soloOfertas) Ruta.Ofertas.ruta else Ruta.Catalogo.ruta
    val titulo = if (estado.soloOfertas) {
        stringResource(R.string.ofertas)
    } else {
        stringResource(R.string.catalogo)
    }

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
                titulo = stringResource(R.string.catalogo_vacio_titulo),
                descripcion = stringResource(R.string.catalogo_vacio),
                modifier = Modifier.padding(relleno)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
            ) {
                if (estado.soloOfertas) {
                    BannerFeriaPrimavera(
                        compacto = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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
                    items(estado.categorias, key = { it.id }) { categoria ->
                        FilterChip(
                            selected = estado.categoriaId == categoria.id,
                            onClick = { viewModel.seleccionarCategoria(categoria.id) },
                            label = { Text(categoria.nombre) }
                        )
                    }
                }
                if (estado.sinResultados) {
                    EstadoVacio(
                        icono = Icons.Default.Search,
                        titulo = "Sin resultados",
                        descripcion = stringResource(R.string.sin_resultados),
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
                                alPulsar = { alAbrirProducto(producto.id) },
                                cargarImagen = viewModel::cargarImagen
                            )
                        }
                    }
                }
            }
        }
    }
}
