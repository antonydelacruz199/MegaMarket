package com.megamarket.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.app.data.repository.EstadoOrden
import com.megamarket.app.data.repository.EstadoTransferencia
import com.megamarket.app.data.repository.OrdenCompra
import com.megamarket.app.ui.theme.MegaAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorError
import com.megamarket.app.ui.theme.MegaContenedorNeutro
import com.megamarket.app.ui.theme.MegaContenedorPrimario
import com.megamarket.app.ui.theme.MegaContenedorSecundario
import com.megamarket.app.ui.theme.MegaError
import com.megamarket.app.ui.theme.MegaNeutro
import com.megamarket.app.ui.theme.MegaPrimario
import com.megamarket.app.ui.theme.MegaSecundario
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles

@Composable
fun InsigniaEstado(
    texto: String,
    contenedor: Color,
    acento: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = contenedor
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = acento
        )
    }
}

@Composable
fun InsigniaEstadoOrden(estado: EstadoOrden, modifier: Modifier = Modifier) {
    val (contenedor, acento) = when (estado) {
        EstadoOrden.SUGERIDA -> MegaContenedorNeutro to MegaNeutro
        EstadoOrden.APROBADA -> MegaContenedorSecundario to MegaSecundario
        EstadoOrden.PAGADA -> MegaContenedorAdvertencia to MegaAdvertencia
        EstadoOrden.RECIBIDA -> MegaContenedorPrimario to MegaPrimario
        EstadoOrden.CANCELADA -> MegaContenedorError to MegaError
        EstadoOrden.DESCONOCIDO -> MegaContenedorNeutro to MegaNeutro
    }
    InsigniaEstado(estado.etiqueta, contenedor, acento, modifier)
}

@Composable
fun InsigniaEstadoTransferencia(estado: EstadoTransferencia, modifier: Modifier = Modifier) {
    val (contenedor, acento) = when (estado) {
        EstadoTransferencia.CONFIRMADA -> MegaContenedorPrimario to MegaPrimario
        EstadoTransferencia.RECHAZADA -> MegaContenedorError to MegaError
        EstadoTransferencia.PENDIENTE -> MegaContenedorAdvertencia to MegaAdvertencia
        EstadoTransferencia.DESCONOCIDA -> MegaContenedorNeutro to MegaNeutro
    }
    InsigniaEstado(estado.etiqueta, contenedor, acento, modifier)
}

@Composable
fun CargandoCentrado(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun TarjetaOrdenCompra(
    orden: OrdenCompra,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    mostrarProveedor: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = orden.numeroOrden,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                InsigniaEstadoOrden(orden.estado)
            }
            if (mostrarProveedor) {
                orden.proveedor?.let {
                    Text(it.razonSocial, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatearFechaHoraPresentacion(orden.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = orden.totalCentimos.formatearSoles(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
