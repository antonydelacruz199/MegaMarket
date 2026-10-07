package com.megamarket.cliente.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
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
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.launch

private data class ItemMenu(
    val etiqueta: String,
    val ruta: String,
    val icono: ImageVector,
    val esCerrarSesion: Boolean = false
)

@Composable
fun EstructuraCliente(
    rutaActual: String,
    titulo: String,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    contenido: @Composable (PaddingValues) -> Unit
) {
    val estadoMenu = rememberDrawerState(initialValue = DrawerValue.Closed)
    val alcance = rememberCoroutineScope()
    val menu = listOf(
        ItemMenu("Inicio", Ruta.Inicio.ruta, Icons.Default.Home),
        ItemMenu("Catálogo", Ruta.Catalogo.ruta, Icons.AutoMirrored.Filled.List),
        ItemMenu("Ofertas", Ruta.Ofertas.ruta, Icons.Default.Star),
        ItemMenu("Favoritos", Ruta.Favoritos.ruta, Icons.Default.Favorite),
        ItemMenu("Carrito", Ruta.Carrito.ruta, Icons.Default.ShoppingCart),
        ItemMenu("Perfil", Ruta.Perfil.ruta, Icons.Default.Person),
        ItemMenu("Cerrar sesión", Ruta.Sesion.ruta, Icons.Default.Close, esCerrarSesion = true)
    )

    ModalNavigationDrawer(
        drawerState = estadoMenu,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MegaMarket Express",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                menu.forEach { item ->
                    if (item.esCerrarSesion) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    NavigationDrawerItem(
                        label = { Text(item.etiqueta) },
                        selected = !item.esCerrarSesion && item.ruta == rutaActual,
                        onClick = {
                            alcance.launch { estadoMenu.close() }
                            if (item.esCerrarSesion) alCerrarSesion() else alNavegar(item.ruta)
                        },
                        icon = {
                            Icon(imageVector = item.icono, contentDescription = item.etiqueta)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
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
                    alPulsarNavegacion = { alcance.launch { estadoMenu.open() } }
                )
            },
            bottomBar = {
                NavigationBar {
                    BarraItem("Inicio", Ruta.Inicio.ruta, Icons.Default.Home, rutaActual, alNavegar)
                    BarraItem("Catálogo", Ruta.Catalogo.ruta, Icons.AutoMirrored.Filled.List, rutaActual, alNavegar)
                    BarraItem("Favoritos", Ruta.Favoritos.ruta, Icons.Default.Favorite, rutaActual, alNavegar)
                    BarraItem("Carrito", Ruta.Carrito.ruta, Icons.Default.ShoppingCart, rutaActual, alNavegar)
                }
            },
            content = contenido
        )
    }
}

@Composable
private fun RowScope.BarraItem(
    etiqueta: String,
    ruta: String,
    icono: ImageVector,
    rutaActual: String,
    alNavegar: (String) -> Unit
) {
    NavigationBarItem(
        selected = rutaActual == ruta,
        onClick = { alNavegar(ruta) },
        icon = { Icon(imageVector = icono, contentDescription = etiqueta) },
        label = { Text(etiqueta) }
    )
}
