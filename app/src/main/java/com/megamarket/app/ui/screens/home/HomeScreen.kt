package com.megamarket.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.ClientScaffold
import com.megamarket.app.ui.components.MegaMarketSearchBar
import com.megamarket.app.ui.components.ProductPlaceholderCard
import com.megamarket.app.ui.components.SectionHeader
import com.megamarket.app.ui.navigation.Route
import com.megamarket.app.ui.theme.MegaPrimary

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val categories = listOf("Abarrotes", "Lácteos", "Bebidas", "Limpieza")

    ClientScaffold(
        currentRoute = Route.Home.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "MegaMarket",
        topBarActions = {
            IconButton(onClick = { /* Visual only */ }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notificaciones"
                )
            }
            IconButton(onClick = { onNavigate(Route.Cart.path) }) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Carrito"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Ubicación",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Huancayo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            MegaMarketSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MegaPrimary)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Ofertas de la semana",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encuentra tus productos favoritos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onNavigate(Route.Catalog.path) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Ver ofertas")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Categorías")
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories.size) { index ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = 1.dp,
                        modifier = Modifier.clickable { onNavigate(Route.Catalog.path) }
                    ) {
                        Text(
                            text = categories[index],
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(
                title = "Ofertas de la semana",
                actionLabel = "Ver más",
                onActionClick = { onNavigate(Route.Catalog.path) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductPlaceholderCard(
                    modifier = Modifier.weight(1f),
                    name = "Producto",
                    brand = "Marca",
                    showOfferBadge = true,
                    onClick = { onNavigate(Route.ProductDetail.path) }
                )
                ProductPlaceholderCard(
                    modifier = Modifier.weight(1f),
                    name = "Producto",
                    brand = "Marca",
                    showOfferBadge = true,
                    onClick = { onNavigate(Route.ProductDetail.path) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Recomendados para ti")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductPlaceholderCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Route.ProductDetail.path) }
                )
                ProductPlaceholderCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Route.ProductDetail.path) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
