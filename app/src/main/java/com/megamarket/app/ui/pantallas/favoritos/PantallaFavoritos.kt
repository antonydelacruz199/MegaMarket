package com.megamarket.app.ui.pantallas.favoritos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.EstructuraCliente
import com.megamarket.app.ui.componentes.TarjetaProducto
import com.megamarket.app.ui.navegacion.Ruta

@Composable
fun PantallaFavoritos(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    EstructuraCliente(
        rutaActual = Ruta.Favoritos.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Mis favoritos"
    ) { relleno ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Solo marcadores visuales: sin favoritos reales
            items(4) {
                TarjetaProducto(
                    alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) }
                )
            }
        }
    }
}
