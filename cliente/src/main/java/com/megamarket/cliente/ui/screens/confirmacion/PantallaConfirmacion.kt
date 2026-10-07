package com.megamarket.cliente.ui.screens.confirmacion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.ui.components.BarraSuperior
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaConfirmacion(
    viewModel: ConfirmacionViewModel,
    alIrAlInicio: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val pedido = estado.pedido

    Scaffold(topBar = { BarraSuperior(titulo = "Confirmación") }) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                estado.cargando -> CircularProgressIndicator()
                pedido == null -> EstadoVacio(
                    icono = Icons.Default.Warning,
                    titulo = estado.error ?: "No hay una compra para mostrar",
                    descripcion = "Puedes revisar tu carrito desde el inicio.",
                    etiquetaAccion = "Reintentar",
                    alPulsarAccion = viewModel::cargar
                )
                else -> ResumenPedido(pedido)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = alIrAlInicio,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Volver al inicio") }
        }
    }
}

@Composable
private fun ResumenPedido(pedido: Pedido) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Compra registrada",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = "Pedido N.° ${pedido.id}",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Total ${pedido.totalCentimos.formatearSoles()}",
        style = MaterialTheme.typography.titleLarge
    )
    Text("${pedido.cantidadProductos} producto(s)")
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "${pedido.direccion.direccion}, ${pedido.direccion.distrito}",
        textAlign = TextAlign.Center
    )
    Text(
        text = "${pedido.direccion.provincia}, ${pedido.direccion.departamento}",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text("Tel. ${pedido.direccion.telefono}")
}
