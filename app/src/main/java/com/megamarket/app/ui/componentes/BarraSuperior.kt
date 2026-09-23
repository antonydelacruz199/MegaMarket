package com.megamarket.app.ui.componentes

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    modifier: Modifier = Modifier,
    centrada: Boolean = false,
    alPulsarNavegacion: (() -> Unit)? = null,
    mostrarIconoMenu: Boolean = false,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    val colores = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface
    )

    val iconoNavegacion: @Composable () -> Unit = {
        when {
            mostrarIconoMenu && alPulsarNavegacion != null -> {
                IconButton(onClick = alPulsarNavegacion) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Abrir menú"
                    )
                }
            }
            alPulsarNavegacion != null -> {
                IconButton(onClick = alPulsarNavegacion) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            }
            else -> Unit
        }
    }

    val contenidoTitulo: @Composable () -> Unit = {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (centrada) {
        CenterAlignedTopAppBar(
            title = contenidoTitulo,
            modifier = modifier,
            navigationIcon = iconoNavegacion,
            actions = acciones,
            colors = colores
        )
    } else {
        TopAppBar(
            title = contenidoTitulo,
            modifier = modifier,
            navigationIcon = iconoNavegacion,
            actions = acciones,
            colors = colores
        )
    }
}
