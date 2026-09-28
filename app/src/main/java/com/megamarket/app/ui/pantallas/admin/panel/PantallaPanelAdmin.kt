package com.megamarket.app.ui.pantallas.admin.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.EstructuraAdmin
import com.megamarket.app.ui.componentes.TarjetaResumenAdmin
import com.megamarket.app.ui.navegacion.Ruta
import com.megamarket.app.viewmodel.ViewModelCatalogo

@Composable
fun PantallaPanelAdmin(
    viewModel: ViewModelCatalogo,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaResumenAdmin(
                    titulo = "Productos",
                    valor = estado.totalProductos.toString(),
                    icono = Icons.Default.List,
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Ofertas activas",
                    valor = estado.ofertasActivas.toString(),
                    icono = Icons.Default.Star,
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
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Agotados",
                    valor = estado.agotados.toString(),
                    icono = Icons.Default.Delete,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            TarjetaAccionRapida(
                titulo = "Gestionar productos",
                descripcion = "Crear y revisar el catálogo",
                alPulsar = { alNavegar(Ruta.ProductosAdmin.ruta) }
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
