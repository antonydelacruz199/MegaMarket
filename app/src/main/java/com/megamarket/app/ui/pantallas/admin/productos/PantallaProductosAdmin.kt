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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraBusqueda
import com.megamarket.app.ui.componentes.EstadoVacio
import com.megamarket.app.ui.componentes.EstructuraAdmin
import com.megamarket.app.ui.componentes.TarjetaProductoAdmin
import com.megamarket.app.ui.navegacion.Ruta
import com.megamarket.app.viewmodel.ViewModelCatalogo
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaProductosAdmin(
    viewModel: ViewModelCatalogo,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargarProductos()
        onPauseOrDispose { }
    }
    var consulta by rememberSaveable { mutableStateOf("") }
    var filtroSeleccionado by rememberSaveable { mutableStateOf("Todos") }
    val filtros = listOf("Todos", "Activos", "Ofertas", "Stock bajo")

    EstructuraAdmin(
        rutaActual = Ruta.ProductosAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Productos",
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
            val visibles = estado.productos.filter { producto ->
                val texto = consulta.trim()
                val coincideTexto = texto.isEmpty() ||
                    producto.nombre.contains(texto, ignoreCase = true) ||
                    producto.marca.contains(texto, ignoreCase = true)
                val coincideFiltro = when (filtroSeleccionado) {
                    "Activos" -> producto.activo
                    "Ofertas" -> producto.esOferta
                    "Stock bajo" -> producto.stock in 1..5
                    else -> true
                }
                coincideTexto && coincideFiltro
            }
            if (visibles.isEmpty()) {
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
                        nombre = producto.nombre,
                        marca = producto.marca,
                        precio = producto.precioVigenteCentimos.formatearSoles(),
                        stock = "Stock: ${producto.stock}",
                        estado = when {
                            !producto.activo -> "Inactivo"
                            producto.agotado -> "Sin stock"
                            producto.esOferta -> "Oferta"
                            else -> "Activo"
                        },
                        alEditar = { alNavegar(Ruta.EditarProductoAdmin.crear(producto.id)) }
                    )
                }
            }
        }
    }
}
