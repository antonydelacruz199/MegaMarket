package com.megamarket.app.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
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
import com.megamarket.app.ui.components.ClientScaffold
import com.megamarket.app.ui.components.MegaMarketSearchBar
import com.megamarket.app.ui.components.ProductPlaceholderCard
import com.megamarket.app.ui.navigation.Route
import kotlinx.coroutines.launch

private data class CatalogPlaceholder(
    val id: Int,
    val name: String,
    val offer: Boolean
)

@Composable
fun CatalogScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("Todos") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val filters = listOf("Todos", "Abarrotes", "Lácteos", "Bebidas", "Limpieza", "Ofertas")
    val placeholders = listOf(
        CatalogPlaceholder(1, "Producto 1", true),
        CatalogPlaceholder(2, "Producto 2", false),
        CatalogPlaceholder(3, "Producto 3", true),
        CatalogPlaceholder(4, "Producto 4", false),
        CatalogPlaceholder(5, "Producto 5", false),
        CatalogPlaceholder(6, "Producto 6", true)
    )

    ClientScaffold(
        currentRoute = Route.Catalog.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "Productos"
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
                    items(filters.size) { index ->
                        val filter = filters[index]
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(placeholders, key = { it.id }) { item ->
                    ProductPlaceholderCard(
                        name = item.name,
                        brand = "Marca",
                        showOfferBadge = item.offer,
                        onClick = { onNavigate(Route.ProductDetail.path) },
                        onFavoriteClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Disponible próximamente")
                            }
                        },
                        onCartClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Disponible próximamente")
                            }
                        }
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
