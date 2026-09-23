package com.megamarket.app.ui.pantallas.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraBusqueda
import com.megamarket.app.ui.componentes.EncabezadoSeccion
import com.megamarket.app.ui.componentes.EstructuraCliente
import com.megamarket.app.ui.componentes.TarjetaProducto
import com.megamarket.app.ui.navegacion.Ruta
import com.megamarket.app.ui.tema.MegaPrimario

@Composable
fun PantallaInicio(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    var consulta by rememberSaveable { mutableStateOf("") }
    val categorias = listOf("Abarrotes", "Lácteos", "Bebidas", "Limpieza")

    EstructuraCliente(
        rutaActual = Ruta.Inicio.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "MegaMarket",
        accionesBarraSuperior = {
            IconButton(onClick = { /* Solo visual */ }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notificaciones"
                )
            }
            IconButton(onClick = { alNavegar(Ruta.Carrito.ruta) }) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Carrito"
                )
            }
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Ubicación",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Huancayo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            BarraBusqueda(
                consulta = consulta,
                alCambiarConsulta = { consulta = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MegaPrimario)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Ofertas de la semana",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encuentra tus productos favoritos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { alNavegar(Ruta.Catalogo.ruta) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Ver ofertas")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            EncabezadoSeccion(titulo = "Categorías")
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categorias.size) { indice ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (indice == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = 1.dp,
                        modifier = Modifier.clickable { alNavegar(Ruta.Catalogo.ruta) }
                    ) {
                        Text(
                            text = categorias[indice],
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            EncabezadoSeccion(
                titulo = "Ofertas de la semana",
                etiquetaAccion = "Ver más",
                alPulsarAccion = { alNavegar(Ruta.Catalogo.ruta) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaProducto(
                    modifier = Modifier.weight(1f),
                    nombre = "Producto",
                    marca = "Marca",
                    mostrarEtiquetaOferta = true,
                    alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) }
                )
                TarjetaProducto(
                    modifier = Modifier.weight(1f),
                    nombre = "Producto",
                    marca = "Marca",
                    mostrarEtiquetaOferta = true,
                    alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            EncabezadoSeccion(titulo = "Recomendados para ti")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaProducto(
                    modifier = Modifier.weight(1f),
                    alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) }
                )
                TarjetaProducto(
                    modifier = Modifier.weight(1f),
                    alPulsar = { alNavegar(Ruta.DetalleProducto.ruta) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
