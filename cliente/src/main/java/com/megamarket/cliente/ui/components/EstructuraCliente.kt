package com.megamarket.cliente.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.cliente.R
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.ui.theme.Espaciado
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
    val etiquetaCatalogo = stringResource(R.string.catalogo)
    val etiquetaCerrar = stringResource(R.string.cerrar_sesion)
    val etiquetaOfertas = stringResource(R.string.ofertas)
    val menu = listOf(
        ItemMenu("Inicio", Ruta.Inicio.ruta, Icons.Default.Home),
        ItemMenu(etiquetaCatalogo, Ruta.Catalogo.ruta, Icons.Default.ShoppingBag),
        ItemMenu(etiquetaOfertas, Ruta.Ofertas.ruta, Icons.Default.LocalOffer),
        ItemMenu("Favoritos", Ruta.Favoritos.ruta, Icons.Default.Favorite),
        ItemMenu("Carrito", Ruta.Carrito.ruta, Icons.Default.ShoppingCart),
        ItemMenu("Mis Pedidos", Ruta.MisPedidos.ruta, Icons.AutoMirrored.Filled.ReceiptLong),
        ItemMenu("Sustituciones", Ruta.Sustituciones.ruta, Icons.Default.SwapHoriz),
        ItemMenu("Perfil", Ruta.Perfil.ruta, Icons.Default.Person),
        ItemMenu(etiquetaCerrar, Ruta.Sesion.ruta, Icons.AutoMirrored.Filled.Logout, esCerrarSesion = true)
    )

    ModalNavigationDrawer(
        drawerState = estadoMenu,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 28.dp, vertical = Espaciado.xl)
                ) {
                    Text(
                        text = "MegaMarket",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Express",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tu mercado de barrio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = Espaciado.xs)
                    )
                }
                Spacer(modifier = Modifier.height(Espaciado.sm))
                menu.forEach { item ->
                    if (item.esCerrarSesion) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Espaciado.sm))
                    }
                    NavigationDrawerItem(
                        label = {
                            Text(
                                item.etiqueta,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        selected = !item.esCerrarSesion && item.ruta == rutaActual,
                        onClick = {
                            alcance.launch { estadoMenu.close() }
                            if (item.esCerrarSesion) alCerrarSesion() else alNavegar(item.ruta)
                        },
                        icon = {
                            Icon(imageVector = item.icono, contentDescription = item.etiqueta)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                BarraSuperior(
                    titulo = titulo,
                    mostrarIconoMenu = true,
                    alPulsarNavegacion = { alcance.launch { estadoMenu.open() } }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    BarraItem("Inicio", Ruta.Inicio.ruta, Icons.Default.Home, rutaActual, alNavegar)
                    BarraItem(etiquetaCatalogo, Ruta.Catalogo.ruta, Icons.Default.ShoppingBag, rutaActual, alNavegar)
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
        label = { Text(etiqueta, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
