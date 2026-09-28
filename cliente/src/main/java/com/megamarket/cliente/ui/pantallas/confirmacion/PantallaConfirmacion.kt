package com.megamarket.cliente.ui.pantallas.confirmacion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
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
import com.megamarket.cliente.ui.componentes.BarraSuperior
import com.megamarket.cliente.viewmodel.ViewModelConfirmacion
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaConfirmacion(
    viewModel: ViewModelConfirmacion,
    alIrAlInicio: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val resumen = estado.resumen

    Scaffold(topBar = { BarraSuperior(titulo = "Confirmación") }) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (resumen == null) {
                Text("No hay una compra para mostrar", textAlign = TextAlign.Center)
            } else {
                Text(
                    text = "Compra registrada",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total ${resumen.totalCentimos.formatearSoles()}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text("${resumen.cantidadProductos} producto(s)")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${resumen.direccion.direccion}, ${resumen.direccion.distrito}",
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${resumen.direccion.provincia}, ${resumen.direccion.departamento}",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Tel. ${resumen.direccion.telefono}")
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
