package com.megamarket.app.ui.components

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
import com.megamarket.app.ui.navigation.ClientBottomDestinations
import com.megamarket.app.ui.navigation.Route
import kotlinx.coroutines.launch

private data class DrawerDestination(
    val label: String,
    val route: String?,
    val icon: ImageVector,
    val isLogout: Boolean = false
)

private data class BottomDestination(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun ClientScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    title: String,
    topBarActions: @Composable RowScope.() -> Unit = {},
    showBottomBar: Boolean = currentRoute in ClientBottomDestinations.routes,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val drawerItems = listOf(
        DrawerDestination("Inicio", Route.Home.path, Icons.Default.Home),
        DrawerDestination("Catálogo", Route.Catalog.path, Icons.Default.List),
        DrawerDestination("Ofertas", Route.Catalog.path, Icons.Default.Star),
        DrawerDestination("Favoritos", Route.Favorites.path, Icons.Default.Favorite),
        DrawerDestination("Mi carrito", Route.Cart.path, Icons.Default.ShoppingCart),
        DrawerDestination("Mi perfil", Route.Profile.path, Icons.Default.Person),
        DrawerDestination("Cerrar sesión", null, Icons.Default.Close, isLogout = true)
    )

    val bottomItems = listOf(
        BottomDestination("Inicio", Route.Home.path, Icons.Filled.Home, Icons.Outlined.Home),
        BottomDestination("Catálogo", Route.Catalog.path, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
        BottomDestination("Favoritos", Route.Favorites.path, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
        BottomDestination("Carrito", Route.Cart.path, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                drawerItems.forEach { item ->
                    if (item.isLogout) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = !item.isLogout && item.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item.isLogout) {
                                onLogout()
                            } else if (item.route != null) {
                                onNavigate(item.route)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
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
                MegaMarketTopBar(
                    title = title,
                    showMenuIcon = true,
                    onNavigationClick = {
                        scope.launch { drawerState.open() }
                    },
                    actions = topBarActions
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigate(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            },
            content = content
        )
    }
}
