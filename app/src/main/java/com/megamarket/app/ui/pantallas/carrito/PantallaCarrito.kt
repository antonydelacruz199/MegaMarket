package com.megamarket.app.ui.pantallas.carrito

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
import com.megamarket.app.ui.componentes.EncabezadoSeccion
import com.megamarket.app.ui.componentes.EstructuraCliente
import com.megamarket.app.ui.componentes.MarcadorItemCarrito
import com.megamarket.app.ui.navegacion.Ruta

@Composable
fun PantallaCarrito(
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    EstructuraCliente(
        rutaActual = Ruta.Carrito.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Mi carrito"
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MarcadorItemCarrito()
            Spacer(modifier = Modifier.height(12.dp))
            MarcadorItemCarrito()
            Spacer(modifier = Modifier.height(12.dp))
            MarcadorItemCarrito()
            Spacer(modifier = Modifier.height(24.dp))
            EncabezadoSeccion(titulo = "Resumen")
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
                    FilaResumen("Subtotal", "S/ --.--")
                    FilaResumen("Envío", "S/ --.--")
                    FilaResumen("Descuento", "S/ --.--")
                    HorizontalDivider()
                    FilaResumen("Total", "S/ --.--", destacar = true)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { alNavegar(Ruta.Compra.ruta) },
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
private fun FilaResumen(
    etiqueta: String,
    valor: String,
    destacar: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = etiqueta,
            style = if (destacar) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (destacar) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = valor,
            style = if (destacar) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (destacar) FontWeight.Bold else FontWeight.Medium,
            color = if (destacar) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
