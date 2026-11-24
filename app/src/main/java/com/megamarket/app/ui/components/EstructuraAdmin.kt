package com.megamarket.app.ui.components

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.R
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.ui.theme.Espaciado
import kotlinx.coroutines.launch

private data class ItemMenuAdmin(
    val etiqueta: String,
    val ruta: String?,
    val icono: ImageVector,
    val esCerrarSesion: Boolean = false
)

@Composable
fun EstructuraAdmin(
    rutaActual: String,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    titulo: String,
    botonFlotante: @Composable () -> Unit = {},
    accionesBarraSuperior: @Composable RowScope.() -> Unit = {},
    contenido: @Composable (PaddingValues) -> Unit
) {
    val estadoMenu = rememberDrawerState(initialValue = DrawerValue.Closed)
    val alcance = rememberCoroutineScope()

    val itemsMenu = listOf(
        ItemMenuAdmin("Panel", Ruta.PanelAdmin.ruta, Icons.Default.Dashboard),
        ItemMenuAdmin("Productos", Ruta.ProductosAdmin.ruta, Icons.Default.Inventory2),
        ItemMenuAdmin("Billetera", Ruta.BilleteraAdmin.ruta, Icons.Default.AccountBalanceWallet),
        ItemMenuAdmin(
            etiqueta = stringResource(R.string.cerrar_sesion),
            ruta = null,
            icono = Icons.AutoMirrored.Filled.Logout,
            esCerrarSesion = true
        )
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
                        text = "Administración",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Catálogo, stock y cobros",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = Espaciado.xs)
                    )
                }
                Spacer(modifier = Modifier.height(Espaciado.sm))
                itemsMenu.forEach { item ->
                    if (item.esCerrarSesion) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Espaciado.sm))
                    }
                    NavigationDrawerItem(
                        label = {
                            Text(item.etiqueta, style = MaterialTheme.typography.labelLarge)
                        },
                        selected = !item.esCerrarSesion && item.ruta == rutaActual,
                        onClick = {
                            alcance.launch { estadoMenu.close() }
                            when {
                                item.esCerrarSesion -> alCerrarSesion()
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
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
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
