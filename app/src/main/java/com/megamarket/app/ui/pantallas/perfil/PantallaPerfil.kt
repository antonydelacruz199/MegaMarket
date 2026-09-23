package com.megamarket.app.ui.pantallas.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.EstructuraCliente
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

@Composable
fun PantallaPerfil(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

    val opciones = listOf(
        OpcionPerfil("Datos personales", Icons.Default.Person),
        OpcionPerfil("Mis direcciones", Icons.Default.Home),
        OpcionPerfil("Mis pedidos", Icons.Default.List),
        OpcionPerfil("Mis favoritos", Icons.Default.Favorite),
        OpcionPerfil("Configuración", Icons.Default.Settings),
        OpcionPerfil("Ayuda", Icons.Default.Info)
    )

    EstructuraCliente(
        rutaActual = Ruta.Perfil.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Mi cuenta",
        mostrarBarraInferior = false
    ) { relleno ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Foto de perfil",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Mi cuenta",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cliente MegaMarket",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                opciones.forEach { opcion ->
                    FilaOpcionPerfil(
                        titulo = opcion.titulo,
                        icono = opcion.icono,
                        alPulsar = {
                            if (opcion.titulo == "Mis favoritos") {
                                alNavegar(Ruta.Favoritos.ruta)
                            } else {
                                alcance.launch {
                                    estadoMensaje.showSnackbar("Disponible próximamente")
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                FilaOpcionPerfil(
                    titulo = "Cerrar sesión",
                    icono = Icons.Default.Close,
                    alPulsar = alCerrarSesion,
                    esDestructiva = true
                )
            }
            SnackbarHost(
                hostState = estadoMensaje,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private data class OpcionPerfil(
    val titulo: String,
    val icono: ImageVector
)

@Composable
private fun FilaOpcionPerfil(
    titulo: String,
    icono: ImageVector,
    alPulsar: () -> Unit,
    esDestructiva: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icono,
                contentDescription = titulo,
                tint = if (esDestructiva) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = if (esDestructiva) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (!esDestructiva) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
