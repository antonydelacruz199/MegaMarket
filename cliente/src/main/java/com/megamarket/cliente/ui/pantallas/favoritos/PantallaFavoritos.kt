package com.megamarket.cliente.ui.pantallas.favoritos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.componentes.EstadoVacio
import com.megamarket.cliente.ui.componentes.EstructuraCliente
import com.megamarket.cliente.ui.componentes.TarjetaProducto
import com.megamarket.cliente.ui.navegacion.Ruta
import com.megamarket.cliente.viewmodel.ViewModelFavoritos

@Composable
fun PantallaFavoritos(
    viewModel: ViewModelFavoritos,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirProducto: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    EstructuraCliente(
        rutaActual = Ruta.Favoritos.ruta,
        titulo = "Favoritos",
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        when {
            estado.cargando -> Column(
                modifier = Modifier.fillMaxSize().padding(relleno),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            estado.error != null -> EstadoVacio(
                icono = Icons.Default.Favorite,
                titulo = "No se pudieron leer los favoritos",
                descripcion = estado.error.orEmpty(),
                modifier = Modifier.padding(relleno)
            )

            estado.productos.isEmpty() -> EstadoVacio(
                icono = Icons.Default.Favorite,
                titulo = "Sin favoritos",
                descripcion = "Todavía no tienes favoritos",
                modifier = Modifier.padding(relleno)
            )

            else -> LazyColumn(
                modifier = Modifier.padding(relleno),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.productos, key = { it.id }) { producto ->
                    TarjetaProducto(
                        producto = producto,
                        esFavorito = true,
                        alPulsar = { alAbrirProducto(producto.id) },
                        alAlternarFavorito = { viewModel.alternar(producto.id) }
                    )
                }
            }
        }
    }
}
