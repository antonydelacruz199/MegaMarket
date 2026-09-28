package com.megamarket.cliente.ui.pantallas.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.componentes.EstadoVacio
import com.megamarket.cliente.ui.componentes.EstructuraCliente
import com.megamarket.cliente.ui.componentes.TarjetaProducto
import com.megamarket.cliente.ui.navegacion.Ruta
import com.megamarket.cliente.viewmodel.ViewModelInicio

@Composable
fun PantallaInicio(
    viewModel: ViewModelInicio,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirProducto: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    EstructuraCliente(
        rutaActual = Ruta.Inicio.ruta,
        titulo = "Inicio",
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        when {
            estado.cargando -> Indicador(relleno)
            estado.error != null -> EstadoVacio(
                icono = Icons.AutoMirrored.Filled.List,
                titulo = "No se pudo cargar",
                descripcion = estado.error.orEmpty(),
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Reintentar",
                alPulsarAccion = viewModel::cargar
            )
            !estado.hayProductos -> EstadoVacio(
                icono = Icons.AutoMirrored.Filled.List,
                titulo = "Catálogo vacío",
                descripcion = "Aún no hay productos. Cuando el administrador publique el catálogo, lo verás aquí.",
                modifier = Modifier.padding(relleno)
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Hola",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ofertas de la semana",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                if (estado.ofertas.isEmpty()) {
                    Text(
                        text = "Por ahora no hay ofertas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(estado.ofertas, key = { it.id }) { producto ->
                            TarjetaProducto(
                                producto = producto,
                                alPulsar = { alAbrirProducto(producto.id) },
                                modifier = Modifier.fillParentMaxWidth(0.8f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { alNavegar(Ruta.Catalogo.ruta) }) {
                    Text("Ver catálogo")
                }
            }
        }
    }
}

@Composable
private fun Indicador(relleno: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(relleno),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}
