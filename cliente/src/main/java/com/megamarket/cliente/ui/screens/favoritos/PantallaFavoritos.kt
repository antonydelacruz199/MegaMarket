package com.megamarket.cliente.ui.screens.favoritos

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.components.TarjetaProducto
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.viewmodel.FavoritosViewModel

@Composable
fun PantallaFavoritos(
    viewModel: FavoritosViewModel,
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

            estado.error != null && estado.productos.isEmpty() -> EstadoVacio(
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
                if (estado.error != null) {
                    item {
                        Text(
                            text = estado.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                items(estado.productos, key = { it.id }) { producto ->
                    TarjetaProducto(
                        producto = producto,
                        esFavorito = true,
                        alPulsar = { alAbrirProducto(producto.id) },
                        cargarImagen = viewModel::cargarImagen,
                        alAlternarFavorito = { viewModel.alternar(producto.id) }
                    )
                }
            }
        }
    }
}
