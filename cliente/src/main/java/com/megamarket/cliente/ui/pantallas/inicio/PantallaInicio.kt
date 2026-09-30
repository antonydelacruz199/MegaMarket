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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.componentes.BannerFeriaPrimavera
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
    LifecycleResumeEffect(Unit) {
        viewModel.cargar()
        onPauseOrDispose { }
    }

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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val hayOfertas = estado.ofertas.isNotEmpty()
                BannerFeriaPrimavera(
                    etiquetaAccion = if (hayOfertas) "Ver ofertas de la feria" else "Recorrer el catálogo",
                    alPulsar = {
                        alNavegar(if (hayOfertas) Ruta.Ofertas.ruta else Ruta.Catalogo.ruta)
                    }
                )
                Text(
                    text = "La yapa de esta semana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                )
                Text(
                    text = "Toca un producto y llévatelo con precio de feria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!hayOfertas) {
                    Text(
                        text = "La feria abre cuando haya ofertas. Mientras tanto, recorre el catálogo del barrio.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(estado.ofertas, key = { it.id }) { producto ->
                            TarjetaProducto(
                                producto = producto,
                                alPulsar = { alAbrirProducto(producto.id) },
                                modifier = Modifier.fillParentMaxWidth(0.86f)
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
