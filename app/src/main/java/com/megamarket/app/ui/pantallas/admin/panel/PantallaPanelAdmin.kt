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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.EncabezadoSeccion
import com.megamarket.app.ui.componentes.EstructuraAdmin
import com.megamarket.app.ui.componentes.TarjetaResumenAdmin
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

@Composable
fun PantallaPanelAdmin(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

    EstructuraAdmin(
        rutaActual = Ruta.PanelAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Administración",
        alMostrarMensaje = { mensaje ->
            alcance.launch { estadoMensaje.showSnackbar(mensaje) }
        }
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
                    valor = "--",
                    icono = Icons.Default.List,
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Ofertas activas",
                    valor = "--",
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
                    valor = "--",
                    icono = Icons.Default.Warning,
                    modifier = Modifier.weight(1f)
                )
                TarjetaResumenAdmin(
                    titulo = "Agotados",
                    valor = "--",
                    icono = Icons.Default.Delete,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            EncabezadoSeccion(titulo = "Gestión rápida")
            Spacer(modifier = Modifier.height(8.dp))
            TarjetaAccionRapida(
                titulo = "Gestionar productos",
                descripcion = "Crear, editar y revisar el catálogo",
                alPulsar = { alNavegar(Ruta.ProductosAdmin.ruta) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            TarjetaAccionRapida(
                titulo = "Gestionar ofertas",
                descripcion = "Disponible en la siguiente fase",
                alPulsar = {
                    alcance.launch {
                        estadoMensaje.showSnackbar("Disponible próximamente")
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            TarjetaAccionRapida(
                titulo = "Revisar stock",
                descripcion = "Disponible en la siguiente fase",
                alPulsar = {
                    alcance.launch {
                        estadoMensaje.showSnackbar("Disponible próximamente")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SnackbarHost(hostState = estadoMensaje)
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
