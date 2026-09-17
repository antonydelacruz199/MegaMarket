package com.megamarket.app.ui.screens.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.CartItemPlaceholder
import com.megamarket.app.ui.components.ClientScaffold
import com.megamarket.app.ui.components.SectionHeader
import com.megamarket.app.ui.navigation.Route

@Composable
fun CartScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    ClientScaffold(
        currentRoute = Route.Cart.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "Mi carrito"
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CartItemPlaceholder(name = "Producto 1", initialQuantity = 1)
            Spacer(modifier = Modifier.height(12.dp))
            CartItemPlaceholder(name = "Producto 2", initialQuantity = 2)
            Spacer(modifier = Modifier.height(12.dp))
            CartItemPlaceholder(name = "Producto 3", initialQuantity = 1)
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Resumen")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryRow("Subtotal", "S/ --.--")
                    SummaryRow("Delivery", "S/ --.--")
                    SummaryRow("Descuento", "S/ --.--")
                    HorizontalDivider()
                    SummaryRow("Total", "S/ --.--", emphasize = true)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onNavigate(Route.Checkout.path) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continuar compra")
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
