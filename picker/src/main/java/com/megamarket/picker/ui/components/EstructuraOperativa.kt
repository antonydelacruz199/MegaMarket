package com.megamarket.picker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.picker.R

data class DestinoOperativo(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstructuraOperativa(
    titulo: String,
    rutaActual: String,
    destinos: List<DestinoOperativo>,
    alNavegar: (String) -> Unit,
    alRefrescar: () -> Unit,
    alCerrarSesion: () -> Unit,
    contenido: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = titulo, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = alRefrescar) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.reintentar)
                        )
                    }
                    IconButton(onClick = alCerrarSesion) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.cerrar_sesion)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                destinos.forEach { destino ->
                    ItemBarra(
                        destino = destino,
                        seleccionado = rutaActual == destino.ruta,
                        alNavegar = alNavegar
                    )
                }
            }
        },
        content = contenido
    )
}

@Composable
private fun RowScope.ItemBarra(
    destino: DestinoOperativo,
    seleccionado: Boolean,
    alNavegar: (String) -> Unit
) {
    NavigationBarItem(
        selected = seleccionado,
        onClick = { alNavegar(destino.ruta) },
        icon = { Icon(destino.icono, contentDescription = destino.etiqueta) },
        label = {
            Text(destino.etiqueta, style = MaterialTheme.typography.labelMedium)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
