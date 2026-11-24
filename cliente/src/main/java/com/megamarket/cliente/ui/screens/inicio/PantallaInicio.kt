package com.megamarket.cliente.ui.screens.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.ui.components.BannerFeriaPrimavera
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.components.TarjetaProducto
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.ui.theme.Espaciado
import com.megamarket.cliente.viewmodel.InicioViewModel

@Composable
fun PantallaInicio(
    viewModel: InicioViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirProducto: (Long) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargarInicio()
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
                icono = Icons.Default.Warning,
                titulo = "No se pudo cargar",
                descripcion = estado.error.orEmpty(),
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Reintentar",
                alPulsarAccion = viewModel::cargarInicio
            )
            !estado.hayProductos -> EstadoVacio(
                icono = Icons.Default.CloudOff,
                titulo = "Sin catálogo local",
                descripcion = "Aún no hay datos descargados. Conéctate a Internet para cargar el catálogo.",
                modifier = Modifier.padding(relleno)
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .verticalScroll(rememberScrollState())
                    .padding(Espaciado.lg)
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Espaciado.xl, bottom = Espaciado.xs)
                )
                Text(
                    text = "Toca un producto y llévatelo con precio de feria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Espaciado.md)
                )
                if (!hayOfertas) {
                    EstadoVacio(
                        icono = Icons.Default.LocalOffer,
                        titulo = "Feria en preparación",
                        descripcion = "La feria abre cuando haya ofertas. Mientras tanto, recorre el catálogo del barrio.",
                        etiquetaAccion = stringResource(R.string.ver_catalogo),
                        alPulsarAccion = { alNavegar(Ruta.Catalogo.ruta) }
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Espaciado.md)) {
                        items(estado.ofertas, key = { it.id }) { producto ->
                            TarjetaProducto(
                                producto = producto,
                                alPulsar = { alAbrirProducto(producto.id) },
                                solicitudImagen = viewModel.solicitudImagen(producto),
                                modifier = Modifier.fillParentMaxWidth(0.86f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Espaciado.xl))
                    Button(
                        onClick = { alNavegar(Ruta.Catalogo.ruta) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.ver_catalogo),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
