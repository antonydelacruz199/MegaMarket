package com.megamarket.cliente.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megamarket.cliente.viewmodel.SyncUiState

@Composable
fun PanelSincronizacion(
    estado: SyncUiState,
    alSincronizar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            Text("Pendientes: ${estado.pendientes}")
            Text("Errores: ${estado.errores}")
            Text("Última sincronización: ${estado.ultimaSyncTexto}")
            if (!estado.conectado) {
                Text(
                    "Sin conexión. Las operaciones se enviarán al recuperar red.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            estado.ultimoError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = alSincronizar,
                    enabled = !estado.sincronizando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (estado.sincronizando) "Sincronizando..." else "Sincronizar ahora")
                }
            }
        }
    }
}
