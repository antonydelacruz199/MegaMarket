package com.megamarket.app.ui.componentes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

private data class ItemMenuAdmin(
    val etiqueta: String,
    val ruta: String?,
    val icono: ImageVector,
    val esCerrarSesion: Boolean = false,
    val mensaje: String? = null
)

@Composable
fun EstructuraAdmin(
    rutaActual: String,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    titulo: String,
    alMostrarMensaje: (String) -> Unit = {},
    botonFlotante: @Composable () -> Unit = {},
    accionesBarraSuperior: @Composable RowScope.() -> Unit = {},
    contenido: @Composable (PaddingValues) -> Unit
) {
    val estadoMenu = rememberDrawerState(initialValue = DrawerValue.Closed)
    val alcance = rememberCoroutineScope()

    val itemsMenu = listOf(
        ItemMenuAdmin("Panel", Ruta.PanelAdmin.ruta, Icons.Default.Home),
        ItemMenuAdmin("Productos", Ruta.ProductosAdmin.ruta, Icons.Default.List),
        ItemMenuAdmin(
            etiqueta = "Ofertas",
            ruta = Ruta.ProductosAdmin.ruta,
            icono = Icons.Default.Star,
            mensaje = "Ofertas: disponible en la siguiente fase"
        ),
        ItemMenuAdmin(
            etiqueta = "Stock",
            ruta = Ruta.ProductosAdmin.ruta,
            icono = Icons.Default.List,
            mensaje = "Stock: disponible en la siguiente fase"
        ),
        ItemMenuAdmin(
            etiqueta = "Cerrar sesión",
            ruta = null,
            icono = Icons.Default.Close,
            esCerrarSesion = true
        )
    )

    ModalNavigationDrawer(
        drawerState = estadoMenu,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Administración MegaMarket",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                Text(
                    text = "Panel de administración",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                itemsMenu.forEach { item ->
                    if (item.esCerrarSesion) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    NavigationDrawerItem(
                        label = { Text(item.etiqueta) },
                        selected = !item.esCerrarSesion &&
                            item.mensaje == null &&
                            item.ruta == rutaActual,
                        onClick = {
                            alcance.launch { estadoMenu.close() }
                            when {
                                item.esCerrarSesion -> alCerrarSesion()
                                item.mensaje != null -> {
                                    alMostrarMensaje(item.mensaje)
                                    item.ruta?.let(alNavegar)
                                }
                                item.ruta != null -> alNavegar(item.ruta)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icono,
                                contentDescription = item.etiqueta
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                BarraSuperior(
                    titulo = titulo,
                    mostrarIconoMenu = true,
                    alPulsarNavegacion = {
                        alcance.launch { estadoMenu.open() }
                    },
                    acciones = accionesBarraSuperior
                )
            },
            floatingActionButton = botonFlotante,
            content = contenido
        )
    }
}
