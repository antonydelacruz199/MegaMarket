package com.megamarket.app.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.ClientScaffold
import com.megamarket.app.ui.components.ProductPlaceholderCard
import com.megamarket.app.ui.navigation.Route

@Composable
fun FavoritesScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    ClientScaffold(
        currentRoute = Route.Favorites.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "Mis favoritos"
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Solo placeholders visuales — sin favoritos reales
            items(4) {
                ProductPlaceholderCard(
                    onClick = { onNavigate(Route.ProductDetail.path) }
                )
            }
        }
    }
}
