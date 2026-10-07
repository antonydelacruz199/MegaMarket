package com.megamarket.cliente.ui.screens.perfil

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.viewmodel.PerfilViewModel

@Composable
fun PantallaPerfil(
    viewModel: PerfilViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    EstructuraCliente(
        rutaActual = Ruta.Perfil.ruta,
        titulo = "Perfil",
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(16.dp)
        ) {
            Text(estado.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(estado.correo, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = estado.rol,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
