package com.megamarket.app.ui.screens.admin.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.AdminScaffold
import com.megamarket.app.ui.components.AdminSummaryCard
import com.megamarket.app.ui.components.SectionHeader
import com.megamarket.app.ui.navigation.Route
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AdminScaffold(
        currentRoute = Route.AdminDashboard.path,
        onNavigate = onNavigate,
        onLogout = onLogout,
        title = "MegaMarket Admin",
        onShowMessage = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminSummaryCard(
                    title = "Productos",
                    value = "--",
                    icon = Icons.Default.List,
                    modifier = Modifier.weight(1f)
                )
                AdminSummaryCard(
                    title = "Ofertas activas",
                    value = "--",
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminSummaryCard(
                    title = "Stock bajo",
                    value = "--",
                    icon = Icons.Default.Warning,
                    modifier = Modifier.weight(1f)
                )
                AdminSummaryCard(
                    title = "Agotados",
                    value = "--",
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Gestión rápida")
            Spacer(modifier = Modifier.height(8.dp))
            QuickActionCard(
                title = "Gestionar productos",
                description = "Crear, editar y revisar el catálogo",
                onClick = { onNavigate(Route.AdminProducts.path) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QuickActionCard(
                title = "Gestionar ofertas",
                description = "Disponible en la siguiente fase",
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Disponible próximamente")
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QuickActionCard(
                title = "Revisar stock",
                description = "Disponible en la siguiente fase",
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Disponible próximamente")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
