package com.megamarket.app.ui.pantallas.admin.productos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.megamarket.app.ui.componentes.EstructuraAdmin
import com.megamarket.app.ui.componentes.TarjetaProductoAdmin
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

@Composable
fun PantallaProductosAdmin(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    var consulta by rememberSaveable { mutableStateOf("") }
    var filtroSeleccionado by rememberSaveable { mutableStateOf("Todos") }
    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val filtros = listOf("Todos", "Activos", "Ofertas", "Stock bajo")

    EstructuraAdmin(
        rutaActual = Ruta.ProductosAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Productos",
        alMostrarMensaje = { mensaje ->
            alcance.launch { estadoMensaje.showSnackbar(mensaje) }
        },
        botonFlotante = {
            FloatingActionButton(
                onClick = { alNavegar(Ruta.CrearProductoAdmin.ruta) }
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Solo marcadores visuales: sin productos reales
                items(4) {
                    TarjetaProductoAdmin(
                        alEditar = { alNavegar(Ruta.EditarProductoAdmin.ruta) }
                    )
                }
            }
            SnackbarHost(hostState = estadoMensaje)
        }
    }
}
