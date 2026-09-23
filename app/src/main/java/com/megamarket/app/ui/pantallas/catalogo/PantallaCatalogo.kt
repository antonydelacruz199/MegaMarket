package com.megamarket.app.ui.pantallas.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraBusqueda
import com.megamarket.app.ui.componentes.EstructuraCliente
import com.megamarket.app.ui.componentes.TarjetaProducto
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

@Composable
fun PantallaCatalogo(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    var consulta by rememberSaveable { mutableStateOf("") }
    var filtroSeleccionado by rememberSaveable { mutableStateOf("Todos") }
    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val filtros = listOf("Todos", "Abarrotes", "Lácteos", "Bebidas", "Limpieza", "Ofertas")

    EstructuraCliente(
        rutaActual = Ruta.Catalogo.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Productos"
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                BarraBusqueda(
                    consulta = consulta,
                    alCambiarConsulta = { consulta = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtros.size) { indice ->
                        val filtro = filtros[indice]
                        FilterChip(
                            selected = filtroSeleccionado == filtro,
                            onClick = { filtroSeleccionado = filtro },
                            label = { Text(filtro) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Solo marcadores visuales: sin lista de productos reales
                items(4) { indice ->
                    TarjetaProducto(
                        mostrarEtiquetaOferta = indice % 2 == 0,
                        alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) },
                        alPulsarFavorito = {
                            alcance.launch {
                                estadoMensaje.showSnackbar("Disponible próximamente")
                            }
                        },
                        alPulsarCarrito = {
                            alcance.launch {
                                estadoMensaje.showSnackbar("Disponible próximamente")
                            }
                        }
                    )
                }
            }
            SnackbarHost(hostState = estadoMensaje)
        }
    }
}
