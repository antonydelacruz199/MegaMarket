package com.megamarket.app.ui.componentes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.megamarket.app.ui.navegacion.DestinosInferioresCliente
import com.megamarket.app.ui.navegacion.Ruta
import kotlinx.coroutines.launch

private data class DestinoMenu(
    val etiqueta: String,
    val ruta: String?,
    val icono: ImageVector,
    val esCerrarSesion: Boolean = false
)

private data class DestinoInferior(
    val etiqueta: String,
    val ruta: String,
    val iconoSeleccionado: ImageVector,
    val iconoNoSeleccionado: ImageVector
)

@Composable
fun EstructuraCliente(
    rutaActual: String,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    titulo: String,
    accionesBarraSuperior: @Composable RowScope.() -> Unit = {},
    mostrarBarraInferior: Boolean = rutaActual in DestinosInferioresCliente.rutas,
    contenido: @Composable (PaddingValues) -> Unit
) {
    val estadoMenu = rememberDrawerState(initialValue = DrawerValue.Closed)
    val alcance = rememberCoroutineScope()

    val itemsMenu = listOf(
        DestinoMenu("Inicio", Ruta.Inicio.ruta, Icons.Default.Home),
        DestinoMenu("Catálogo", Ruta.Catalogo.ruta, Icons.Default.List),
        DestinoMenu("Ofertas", Ruta.Catalogo.ruta, Icons.Default.Star),
        DestinoMenu("Favoritos", Ruta.Favoritos.ruta, Icons.Default.Favorite),
        DestinoMenu("Mi carrito", Ruta.Carrito.ruta, Icons.Default.ShoppingCart),
        DestinoMenu("Mi perfil", Ruta.Perfil.ruta, Icons.Default.Person),
        DestinoMenu("Cerrar sesión", null, Icons.Default.Close, esCerrarSesion = true)
    )

    val itemsInferiores = listOf(
        DestinoInferior("Inicio", Ruta.Inicio.ruta, Icons.Filled.Home, Icons.Outlined.Home),
        DestinoInferior("Catálogo", Ruta.Catalogo.ruta, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
        DestinoInferior("Favoritos", Ruta.Favoritos.ruta, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
        DestinoInferior("Carrito", Ruta.Carrito.ruta, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart)
    )

    ModalNavigationDrawer(
        drawerState = estadoMenu,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MegaMarket",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                Text(
                    text = "Express · Huancayo",
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
                        selected = !item.esCerrarSesion && item.ruta == rutaActual,
                        onClick = {
                            alcance.launch { estadoMenu.close() }
                            if (item.esCerrarSesion) {
                                alCerrarSesion()
                            } else if (item.ruta != null) {
                                alNavegar(item.ruta)
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
            bottomBar = {
                if (mostrarBarraInferior) {
                    NavigationBar {
                        itemsInferiores.forEach { item ->
                            val seleccionado = rutaActual == item.ruta
                            NavigationBarItem(
                                selected = seleccionado,
                                onClick = { alNavegar(item.ruta) },
                                icon = {
                                    Icon(
                                        imageVector = if (seleccionado) {
                                            item.iconoSeleccionado
                                        } else {
                                            item.iconoNoSeleccionado
                                        },
                                        contentDescription = item.etiqueta
                                    )
                                },
                                label = { Text(item.etiqueta) }
                            )
                        }
                    }
                }
            },
            content = contenido
        )
    }
}
