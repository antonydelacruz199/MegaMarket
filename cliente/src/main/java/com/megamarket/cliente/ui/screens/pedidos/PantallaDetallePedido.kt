package com.megamarket.cliente.ui.screens.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.EtapaProgresoPedido
import com.megamarket.cliente.data.repository.LineaPedidoCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente
import com.megamarket.cliente.data.repository.formatearTiempoRestante
import com.megamarket.cliente.data.repository.importeClienteCentimos
import com.megamarket.cliente.data.repository.milisRestantesHasta
import com.megamarket.cliente.ui.components.ImagenProducto
import com.megamarket.cliente.viewmodel.DetallePedidoViewModel
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetallePedido(
    viewModel: DetallePedidoViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit,
    alPagarQr: (String) -> Unit = {},
    alEscanearQr: (String) -> Unit = {}
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> viewModel.iniciarSeguimiento()
                Lifecycle.Event.ON_STOP -> viewModel.detenerSeguimiento()
                else -> Unit
            }
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose {
            ciclo.lifecycle.removeObserver(obs)
            viewModel.detenerSeguimiento()
        }
    }
    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del pedido") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            estado.cargando && estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
            }
            estado.error != null && estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = viewModel::cargar) { Text("Reintentar") }
                }
            }
            estado.detalle != null -> {
                val d = estado.detalle!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CabeceraPedido(
                        id = d.id,
                        fecha = d.fecha,
                        estado = d.estado,
                        total = d.totalCentimos
                    )
                    if (estado.error != null) {
                        Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                    }

                    SeccionTarjeta("Seguimiento") {
                        TimelinePedido(d.etapasProgreso())
                    }

                    val faltantes = d.lineas.filter { it.estadoPicking.equals("FALTANTE", true) }
                    val disponibles = d.lineas.filter {
                        !it.estadoPicking.equals("FALTANTE", true) &&
                            !it.estadoPicking.equals("ELIMINADO", true)
                    }
                    val eliminados = d.lineas.filter { it.estadoPicking.equals("ELIMINADO", true) }

                    SeccionTarjeta("Productos disponibles") {
                        if (disponibles.isEmpty()) {
                            Text(
                                "Ningún producto armado todavía.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            disponibles.forEachIndexed { i, linea ->
                                FilaProducto(linea)
                                if (i < disponibles.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }

                    if (faltantes.isNotEmpty()) {
                        SeccionTarjeta("Productos faltantes") {
                            Text(
                                "No hay productos en almacén para estos artículos (o aún se busca sustituto).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            faltantes.forEach { Text("• ${it.nombre} (x${it.cantidad})") }
                        }
                    }

                    if (eliminados.isNotEmpty()) {
                        SeccionTarjeta("Sin stock en almacén") {
                            Text(
                                "Estos productos no estaban disponibles. Puedes cancelar el pedido o continuar con el resto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            eliminados.forEach { Text("• ${it.nombre} (x${it.cantidad})") }
                        }
                    }

                    val avisosSinStock = d.sustituciones.filter { it.esAvisoSinStock }
                    val propuestas = d.sustituciones.filter { !it.esAvisoSinStock }
                    if (avisosSinStock.isNotEmpty()) {
                        SeccionTarjeta("Avisos sin stock") {
                            Text(
                                "El picker informó que estos productos no tienen stock ni sustituto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            avisosSinStock.forEach { BloqueAvisoSinStock(it) }
                        }
                    }
                    if (propuestas.isNotEmpty()) {
                        SeccionTarjeta("Reemplazos propuestos") {
                            propuestas.forEach { prop ->
                                Text(
                                    "Cantidad a reemplazar: ${prop.cantidad}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                BloqueSustitucion(
                                    propuesta = prop,
                                    ahora = estado.ahoraEpochMs,
                                    decidiendo = estado.decidiendoId == prop.id,
                                    alAceptar = { viewModel.aceptar(prop.id) },
                                    alRechazar = { viewModel.rechazar(prop.id) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    SeccionTarjeta("Pago") {
                        val pago = d.pago
                        if (pago == null) {
                            Text("Sin información de pago")
                        } else {
                            Text("Modalidad: ${d.modalidadVisible}")
                            Text("Estado: ${pago.estado ?: "-"}")
                            pago.montoCentimos?.let { Text("Monto: ${it.formatearSoles()}") }
                            pago.numeroOperacionBanco?.let { Text("Operación: $it") }
                            if (d.puedePagarConQr) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (pago.modalidad.equals("CONTRAENTREGA", ignoreCase = true)) {
                                        "Puedes pagar con billetera escaneando el QR del repartidor, o él cobrará en efectivo."
                                    } else {
                                        "El pago aún no está confirmado. Completa el cobro para despachar."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SeccionTarjeta("Entrega") {
                        Text(d.franjaVisible, fontWeight = FontWeight.Medium)
                        val dir = d.direccion
                        if (dir != null) {
                            Text(
                                listOfNotNull(dir.direccion, dir.distrito, dir.provincia, dir.departamento)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                            )
                            dir.telefono?.let { Text("Tel: $it") }
                        } else {
                            Text("Sin dirección registrada")
                        }
                    }

                    if (d.puedePagarConQr && d.pago?.modalidad?.equals("CONTRAENTREGA", true) != true) {
                        Button(
                            onClick = { alPagarQr(d.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Realizar pago") }
                    }
                    if (d.puedeEscanearQrEntrega) {
                        Button(
                            onClick = { alEscanearQr(d.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Escanear QR") }
                    }
                    if ((d.puedeCancelar && !estado.cancelado) || avisosSinStock.isNotEmpty() && d.puedeCancelar) {
                        OutlinedButton(
                            onClick = viewModel::cancelarPedido,
                            enabled = !estado.cancelando,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                when {
                                    estado.cancelando -> "Cancelando…"
                                    avisosSinStock.isNotEmpty() -> "Cancelar pedido por falta de stock"
                                    else -> "Cancelar pedido"
                                }
                            )
                        }
                    }
                    if (estado.cancelado || d.estado.equals("CANCELADO", true)) {
                        Text(
                            "Pedido cancelado.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CabeceraPedido(id: String, fecha: String?, estado: String, total: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pedido ${id.take(8)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        fecha?.let { formatearFechaHoraPresentacion(it) }?.takeIf { it != "—" }
                            ?: "Sin fecha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = estado.replace('_', ' '),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                total.formatearSoles(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SeccionTarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            contenido()
        }
    }
}

@Composable
private fun TimelinePedido(etapas: List<EtapaProgresoPedido>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        etapas.forEach { etapa ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                etapa.actual -> MaterialTheme.colorScheme.primary
                                etapa.completada -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (etapa.completada || etapa.actual) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = etapa.nombre.replace('_', ' '),
                    fontWeight = if (etapa.actual) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        etapa.actual -> MaterialTheme.colorScheme.primary
                        etapa.completada -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun FilaProducto(linea: LineaPedidoCliente) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(linea.nombre, fontWeight = FontWeight.Medium)
        Text(
            "Cantidad: ${linea.cantidad} · ${linea.precioUnitarioCentimos.formatearSoles()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Subtotal: ${linea.subtotalCentimos.formatearSoles()}", fontWeight = FontWeight.SemiBold)
        if (!linea.estadoPicking.isNullOrBlank()) {
            Text(
                "Preparación: ${linea.estadoPicking}" +
                    (linea.cantidadArmada?.let { " ($it armada)" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BloqueAvisoSinStock(propuesta: PropuestaSustitucionCliente) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Sin stock en almacén",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenProducto(
                solicitud = propuesta.imagenOriginal,
                modifier = Modifier.size(56.dp),
                descripcion = propuesta.nombreOriginal
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(propuesta.nombreOriginal, fontWeight = FontWeight.Medium)
                Text("Cantidad solicitada: ${propuesta.cantidad}")
            }
        }
    }
}

@Composable
private fun BloqueSustitucion(
    propuesta: PropuestaSustitucionCliente,
    ahora: Long,
    decidiendo: Boolean,
    alAceptar: () -> Unit,
    alRechazar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Estado: ${propuesta.estado}", fontWeight = FontWeight.SemiBold)
        if (propuesta.respuestaAutomatica && EstadoSustitucion.esDefinitivo(propuesta.estado)) {
            Text(
                "Resuelta automáticamente según tu preferencia",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenProducto(
                solicitud = propuesta.imagenOriginal,
                modifier = Modifier.size(56.dp),
                descripcion = propuesta.nombreOriginal
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(propuesta.nombreOriginal, modifier = Modifier.weight(1f))
            Text("→")
            Spacer(modifier = Modifier.width(8.dp))
            ImagenProducto(
                solicitud = propuesta.imagenSustituto,
                modifier = Modifier.size(56.dp),
                descripcion = propuesta.nombreSustituto
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(propuesta.nombreSustituto, modifier = Modifier.weight(1f))
        }
        Text("Cantidad: ${propuesta.cantidad}")
        Text(
            "Máximo a pagar: ${
                importeClienteCentimos(
                    propuesta.precioOriginalCentimos,
                    propuesta.precioSustitutoCentimos,
                    propuesta.cantidad
                ).formatearSoles()
            }"
        )
        if (EstadoSustitucion.esPendiente(propuesta.estado)) {
            val resto = milisRestantesHasta(propuesta.fechaLimite, ahora)
            Text("Tiempo: ${formatearTiempoRestante(resto)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = alAceptar, enabled = !decidiendo) {
                    Text(if (decidiendo) "…" else "Aceptar")
                }
                OutlinedButton(onClick = alRechazar, enabled = !decidiendo) {
                    Text("Rechazar")
                }
            }
        }
    }
}
