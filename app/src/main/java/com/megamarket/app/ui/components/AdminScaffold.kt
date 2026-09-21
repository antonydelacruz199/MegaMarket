package com.megamarket.app.ui.components

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
import com.megamarket.app.ui.navigation.Route
import kotlinx.coroutines.launch

private data class AdminDrawerItem(
    val label: String,
    val route: String?,
    val icon: ImageVector,
    val isLogout: Boolean = false,
    val snackbarMessage: String? = null
)

@Composable
fun AdminScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    title: String,
    onShowMessage: (String) -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val drawerItems = listOf(
        AdminDrawerItem("Dashboard", Route.AdminDashboard.path, Icons.Default.Home),
        AdminDrawerItem("Productos", Route.AdminProducts.path, Icons.Default.List),
        AdminDrawerItem(
            label = "Ofertas",
            route = Route.AdminProducts.path,
            icon = Icons.Default.Star,
            snackbarMessage = "Ofertas: disponible en la siguiente fase"
        ),
        AdminDrawerItem(
            label = "Stock",
            route = Route.AdminProducts.path,
            icon = Icons.Default.List,
            snackbarMessage = "Stock: disponible en la siguiente fase"
        ),
        AdminDrawerItem(
            label = "Cerrar sesión",
            route = null,
            icon = Icons.Default.Close,
            isLogout = true
        )
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MegaMarket Admin",
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
                drawerItems.forEach { item ->
                    if (item.isLogout) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = !item.isLogout &&
                            item.snackbarMessage == null &&
                            item.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            when {
                                item.isLogout -> onLogout()
                                item.snackbarMessage != null -> {
                                    onShowMessage(item.snackbarMessage)
                                    item.route?.let(onNavigate)
                                }
                                item.route != null -> onNavigate(item.route)
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
            floatingActionButton = floatingActionButton,
            content = content
        )
    }
}
