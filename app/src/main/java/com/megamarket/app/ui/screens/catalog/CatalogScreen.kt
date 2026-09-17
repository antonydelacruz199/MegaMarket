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
                // Solo placeholders visuales — sin lista de productos reales
                items(4) { index ->
                    ProductPlaceholderCard(
                        showOfferBadge = index % 2 == 0,
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
