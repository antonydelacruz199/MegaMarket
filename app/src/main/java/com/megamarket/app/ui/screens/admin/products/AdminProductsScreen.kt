package com.megamarket.app.ui.screens.admin.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.AdminScaffold
import com.megamarket.app.ui.components.MegaMarketSearchBar
import com.megamarket.app.ui.components.ProductAdminPlaceholderCard
import com.megamarket.app.ui.navigation.Route
import kotlinx.coroutines.launch

@Composable
fun AdminProductsScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("Todos") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val filters = listOf("Todos", "Activos", "Ofertas", "Stock bajo")
    val placeholders = listOf(
        "Producto 1" to "Activo",
        "Producto 2" to "Oferta",
        "Producto 3" to "Activo",
        "Producto 4" to "Stock bajo"
    )

    AdminScaffold(
        currentRoute = Route.AdminProducts.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "Productos",
        onShowMessage = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Route.AdminProductCreate.path) }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo producto"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MegaMarketSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(placeholders) { (name, status) ->
                    ProductAdminPlaceholderCard(
                        name = name,
                        brand = "Marca",
                        price = "S/ --.--",
                        stock = "Stock: --",
                        status = status,
                        onEditClick = { onNavigate(Route.AdminProductEdit.path) }
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
