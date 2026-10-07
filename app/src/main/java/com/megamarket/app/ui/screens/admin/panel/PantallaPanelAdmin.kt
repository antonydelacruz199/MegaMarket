package com.megamarket.app.ui.screens.admin.panel

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.ui.components.EncabezadoSeccion
import com.megamarket.app.ui.components.EstructuraAdmin
import com.megamarket.app.ui.components.ImagenProducto
import com.megamarket.app.ui.components.TarjetaResumenAdmin
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.ui.theme.MegaAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorError
import com.megamarket.app.ui.theme.MegaError
import com.megamarket.app.viewmodel.ProductoViewModel
import com.megamarket.modelo.Producto
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaPanelAdmin(
    viewModel: ProductoViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargarProductos()
        onPauseOrDispose { }
    }

    EstructuraAdmin(
        rutaActual = Ruta.PanelAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Administración"
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Resumen del catálogo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (estado.error != null) {
                Text(
                    text = estado.error.orEmpty(),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaResumenAdmin(
                    titulo = "Productos",
                    valor = estado.totalProductos.toString(),
                    icono = Icons.Default.List,
                    colorContenedor = MaterialTheme.colorScheme.primaryContainer,
                    colorAcento = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Ofertas activas",
                    valor = estado.ofertasActivas.toString(),
                    icono = Icons.Default.Star,
                    colorContenedor = MaterialTheme.colorScheme.secondaryContainer,
                    colorAcento = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaResumenAdmin(
                    titulo = "Stock bajo",
                    valor = estado.stockBajo.toString(),
                    icono = Icons.Default.Warning,
                    colorContenedor = MegaContenedorAdvertencia,
                    colorAcento = MegaAdvertencia,
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Agotados",
                    valor = estado.agotados.toString(),
                    icono = Icons.Default.Delete,
                    colorContenedor = MegaContenedorError,
                    colorAcento = MegaError,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            EncabezadoSeccion(
                titulo = "Productos con stock bajo",
                etiquetaAccion = "Ver productos",
                alPulsarAccion = { alNavegar(Ruta.ProductosAdmin.ruta) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (estado.productosStockBajo.isEmpty()) {
                TarjetaInventarioEstable()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    estado.productosStockBajo.forEach { producto ->
                        TarjetaStockBajo(
                            producto = producto,
                            alPulsar = { alNavegar(Ruta.EditarProductoAdmin.crear(producto.id)) },
                            cargarImagen = viewModel::cargarImagen
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            TarjetaAccionRapida(
                titulo = "Gestionar productos",
                descripcion = "Crear y revisar el catálogo",
                alPulsar = { alNavegar(Ruta.ProductosAdmin.ruta) }
            )
        }
    }
}

@Composable
private fun TarjetaStockBajo(
    producto: Producto,
    alPulsar: () -> Unit,
    cargarImagen: suspend (clave: String, lado: Int) -> Bitmap?
) {
    Card(
        onClick = alPulsar,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MegaAdvertencia)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImagenProducto(
                    identificador = producto.imagenKey,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(56.dp),
                    descripcion = producto.nombre,
                    cargar = { cargarImagen(producto.imagenKey, 200) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = producto.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = producto.marca,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = producto.precioVigenteCentimos.formatearSoles(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MegaContenedorAdvertencia
                ) {
                    Text(
                        text = "${producto.stock} uds.",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MegaAdvertencia
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaInventarioEstable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = "Inventario estable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Ningún producto tiene entre 1 y 5 unidades.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TarjetaAccionRapida(
    titulo: String,
    descripcion: String,
    alPulsar: () -> Unit
) {
    Card(
        onClick = alPulsar,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
