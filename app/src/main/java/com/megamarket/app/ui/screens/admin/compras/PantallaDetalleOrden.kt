package com.megamarket.app.ui.screens.admin.compras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.data.repository.DetalleOrden
import com.megamarket.app.data.repository.EstadoOrden
import com.megamarket.app.data.repository.EstadoTransferencia
import com.megamarket.app.data.repository.OrdenCompra
import com.megamarket.app.state.AccionOrden
import com.megamarket.app.state.DetalleOrdenUiState
import com.megamarket.app.ui.components.BarraSuperior
import com.megamarket.app.ui.components.CargandoCentrado
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.InsigniaEstadoOrden
import com.megamarket.app.ui.components.InsigniaEstadoTransferencia
import com.megamarket.app.ui.theme.MegaAdvertencia
import com.megamarket.app.ui.theme.MegaContenedorAdvertencia
import com.megamarket.app.viewmodel.OrdenesCompraViewModel
import com.megamarket.app.viewmodel.validarRecepcion
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles

private enum class DialogoOrden { APROBAR, CANCELAR, PAGAR, RECIBIR }

const val AVISO_SIMULADOR =
    "Simulador académico \"Banco Andino\": este pago NO mueve dinero real ni contacta un banco."

@Composable
fun PantallaDetalleOrden(
    viewModel: OrdenesCompraViewModel,
    ordenId: String,
    alVolver: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val detalle = estado.detalle
    val orden = detalle.orden
    val mensajes = remember { SnackbarHostState() }
    var dialogo by remember { mutableStateOf<DialogoOrden?>(null) }

    LaunchedEffect(ordenId) { viewModel.cargarDetalle(ordenId) }
    LaunchedEffect(detalle.mensaje) {
        val mensaje = detalle.mensaje ?: return@LaunchedEffect
        mensajes.showSnackbar(mensaje)
        viewModel.consumirMensajeDetalle()
    }

    if (orden != null) {
        DialogosOrden(
            dialogo = dialogo,
            orden = orden,
            detalle = detalle,
            alCerrar = { dialogo = null },
            alAprobar = { dialogo = null; viewModel.aprobar() },
            alCancelar = { dialogo = null; viewModel.cancelar() },
            alPagar = { dialogo = null; viewModel.pagar() },
            alRecibir = { dialogo = null; viewModel.recibir() }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperior(titulo = orden?.numeroOrden ?: "Orden de compra", alPulsarNavegacion = alVolver) },
        snackbarHost = { SnackbarHost(hostState = mensajes) }
    ) { relleno ->
        when {
            orden == null && detalle.cargando -> CargandoCentrado(Modifier.padding(relleno))
            orden == null -> EstadoVacio(
                icono = Icons.Default.ShoppingCart,
                titulo = "No se pudo cargar",
                descripcion = detalle.error ?: "No se encontró la orden.",
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Reintentar",
                alPulsarAccion = { viewModel.cargarDetalle(ordenId) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (detalle.procesando || detalle.cargando) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                detalle.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                CabeceraOrden(orden)
                LineasOrden(orden)
                SeccionPago(orden, detalle, alConsultar = viewModel::consultarTransferencia)
                SeccionAcciones(
                    orden = orden,
                    detalle = detalle,
                    alPedirDialogo = { dialogo = it }
                )
                if (orden.estado == EstadoOrden.PAGADA) {
                    SeccionRecepcion(
                        orden = orden,
                        detalle = detalle,
                        alCambiarCantidad = viewModel::actualizarCantidadRecepcion,
                        alRegistrar = {
                            if (viewModel.validarFormularioRecepcion()) dialogo = DialogoOrden.RECIBIR
                        }
                    )
                }
                HistorialRecepcion(detalle)
            }
        }
    }
}

// ---------- Diálogos de confirmación ----------

@Composable
private fun DialogosOrden(
    dialogo: DialogoOrden?,
    orden: OrdenCompra,
    detalle: DetalleOrdenUiState,
    alCerrar: () -> Unit,
    alAprobar: () -> Unit,
    alCancelar: () -> Unit,
    alPagar: () -> Unit,
    alRecibir: () -> Unit
) {
    when (dialogo) {
        null -> Unit
        DialogoOrden.APROBAR -> DialogoConfirmacion(
            titulo = "Aprobar orden",
            confirmar = "Aprobar",
            alConfirmar = alAprobar,
            alCerrar = alCerrar
        ) {
            Text("¿Aprobar la orden ${orden.numeroOrden} por ${orden.totalCentimos.formatearSoles()}?")
            Text(
                "Una vez aprobada podrá pagarse al proveedor.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        DialogoOrden.CANCELAR -> DialogoConfirmacion(
            titulo = "Cancelar orden",
            confirmar = "Cancelar orden",
            alConfirmar = alCancelar,
            alCerrar = alCerrar,
            destructivo = true
        ) {
            Text("¿Cancelar la orden ${orden.numeroOrden}? Esta acción no se puede deshacer.")
        }

        DialogoOrden.PAGAR -> DialogoConfirmacion(
            titulo = "Autorizar pago simulado",
            confirmar = "Confirmar pago",
            alConfirmar = alPagar,
            alCerrar = alCerrar
        ) {
            AvisoSimulador()
            Text("Monto: ${orden.totalCentimos.formatearSoles()}", fontWeight = FontWeight.SemiBold)
            Text("Proveedor: ${orden.proveedor?.razonSocial ?: "—"}")
            Text("Cuenta destino: ${orden.proveedor?.cuentaBancaria ?: "—"}")
        }

        DialogoOrden.RECIBIR -> {
            val validacion = validarRecepcion(orden.detalles, detalle.cantidadesRecepcion)
            val porId = orden.detalles.associateBy { it.id }
            DialogoConfirmacion(
                titulo = "Registrar recepción",
                confirmar = "Registrar",
                alConfirmar = alRecibir,
                alCerrar = alCerrar
            ) {
                Text("Se sumará al stock del catálogo:")
                validacion.items.forEach { item ->
                    Text("• ${porId[item.detalleId]?.nombreProducto ?: "Producto"}: +${item.cantidad}")
                }
                Text(
                    "El ingreso de stock no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DialogoConfirmacion(
    titulo: String,
    confirmar: String,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
    destructivo: Boolean = false,
    contenido: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() } },
        confirmButton = {
            TextButton(
                onClick = alConfirmar,
                colors = if (destructivo) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) { Text(confirmar) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Volver") } }
    )
}

@Composable
private fun AvisoSimulador() {
    Card(colors = CardDefaults.cardColors(containerColor = MegaContenedorAdvertencia)) {
        Text(
            text = AVISO_SIMULADOR,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MegaAdvertencia,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------- Secciones ----------

@Composable
private fun CabeceraOrden(orden: OrdenCompra) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(orden.numeroOrden, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InsigniaEstadoOrden(orden.estado)
            }
            orden.proveedor?.let {
                Text(it.razonSocial, style = MaterialTheme.typography.titleSmall)
                Text("RUC ${it.ruc}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                orden.totalCentimos.formatearSoles(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            orden.createdAt?.let { FilaFecha("Creada", it) }
            orden.fechaAprobacion?.let { FilaFecha("Aprobada", it) }
            orden.fechaPago?.let { FilaFecha("Pagada (simulada)", it) }
            orden.fechaRecepcion?.let { FilaFecha("Recibida", it) }
            orden.numeroOperacionBanco?.let {
                Text(
                    "Operación simulada: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilaFecha(etiqueta: String, fecha: String) {
    Text(
        "$etiqueta: ${formatearFechaHoraPresentacion(fecha)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LineasOrden(orden: OrdenCompra) {
    Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            orden.detalles.forEachIndexed { indice, linea ->
                if (indice > 0) HorizontalDivider()
                LineaDetalle(linea, mostrarRecepcion = orden.estado == EstadoOrden.PAGADA ||
                    orden.estado == EstadoOrden.RECIBIDA)
            }
            if (orden.detalles.isEmpty()) Text("La orden no tiene líneas.")
        }
    }
}

@Composable
private fun LineaDetalle(linea: DetalleOrden, mostrarRecepcion: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(linea.nombreProducto, fontWeight = FontWeight.SemiBold)
        Text(
            "${linea.cantidad} × ${linea.precioUnitarioCentimos.formatearSoles()} = ${linea.subtotalCentimos.formatearSoles()}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (mostrarRecepcion) {
            Text(
                "Solicitada ${linea.cantidad} · Recibida ${linea.cantidadRecibida} · Pendiente ${linea.cantidadPendiente}",
                style = MaterialTheme.typography.bodySmall,
                color = if (linea.cantidadPendiente > 0) MegaAdvertencia else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SeccionPago(
    orden: OrdenCompra,
    detalle: DetalleOrdenUiState,
    alConsultar: () -> Unit
) {
    val transferencia = orden.transferencia
    val estadoPago = detalle.estadoPago ?: transferencia?.estado
    if (estadoPago == null) {
        if (orden.estado == EstadoOrden.APROBADA) {
            AvisoSimulador()
        }
        return
    }
    Text("Pago a proveedor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InsigniaEstadoTransferencia(estadoPago)
            AvisoSimulador()
            (detalle.mensajePago ?: transferencia?.mensaje)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            detalle.avisoPago?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            transferencia?.numeroOperacionBanco?.let {
                Text("Nº de operación simulada: $it", style = MaterialTheme.typography.bodySmall)
            }
            when (estadoPago) {
                EstadoTransferencia.CONFIRMADA -> Text(
                    "Transferencia simulada confirmada.",
                    style = MaterialTheme.typography.bodySmall
                )
                EstadoTransferencia.RECHAZADA -> Text(
                    "El simulador rechazó la transferencia. La orden sigue aprobada: puedes intentar un nuevo pago.",
                    style = MaterialTheme.typography.bodySmall
                )
                EstadoTransferencia.PENDIENTE, EstadoTransferencia.DESCONOCIDA -> {
                    val situacion = if (estadoPago == EstadoTransferencia.PENDIENTE) {
                        "La transferencia quedó pendiente."
                    } else {
                        "El resultado es desconocido y la orden NO se marcó como pagada."
                    }
                    Text(
                        text = "$situacion No emitas otro pago nuevo: consulta el estado o reintenta la misma operación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MegaAdvertencia
                    )
                    OutlinedButton(
                        onClick = alConsultar,
                        enabled = !detalle.procesando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (detalle.accion == AccionOrden.CONSULTAR_TRANSFERENCIA) "Consultando…"
                            else "Consultar estado de la transferencia"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeccionAcciones(
    orden: OrdenCompra,
    detalle: DetalleOrdenUiState,
    alPedirDialogo: (DialogoOrden) -> Unit
) {
    if (!orden.puedeAprobar && !orden.puedeCancelar && !orden.puedePagar) return
    val ocupado = detalle.procesando
    val transferenciaAbierta = (detalle.estadoPago ?: orden.transferencia?.estado)?.esAbierta == true
    val sinCuenta = orden.proveedor?.cuentaBancaria == null

    Text("Acciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (orden.puedeAprobar) {
            Button(
                onClick = { alPedirDialogo(DialogoOrden.APROBAR) },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (detalle.accion == AccionOrden.APROBAR) "Aprobando…" else "Aprobar orden") }
        }
        if (orden.puedePagar) {
            Button(
                onClick = { alPedirDialogo(DialogoOrden.PAGAR) },
                enabled = !ocupado && !sinCuenta,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        detalle.accion == AccionOrden.PAGAR -> "Procesando pago…"
                        transferenciaAbierta -> "Reintentar la misma operación de pago"
                        else -> "Pagar al proveedor (simulado)"
                    }
                )
            }
            if (sinCuenta) {
                Text(
                    "El proveedor no tiene cuenta bancaria registrada. Edítalo para poder pagar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (orden.puedeCancelar) {
            OutlinedButton(
                onClick = { alPedirDialogo(DialogoOrden.CANCELAR) },
                // Con una transferencia abierta no se cancela: primero hay que conocer su resultado.
                enabled = !ocupado && !transferenciaAbierta,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (detalle.accion == AccionOrden.CANCELAR) "Cancelando…" else "Cancelar orden") }
        }
    }
}

@Composable
private fun SeccionRecepcion(
    orden: OrdenCompra,
    detalle: DetalleOrdenUiState,
    alCambiarCantidad: (String, String) -> Unit,
    alRegistrar: () -> Unit
) {
    if (!orden.puedeRecibir) return
    Text("Recepción de mercadería", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Indica cuánto llegó de cada producto. Puedes recibir parcialmente.",
                style = MaterialTheme.typography.bodySmall
            )
            orden.detalles.filter { it.cantidadPendiente > 0 }.forEach { linea ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(linea.nombreProducto, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Solicitada ${linea.cantidad} · Recibida ${linea.cantidadRecibida} · Pendiente ${linea.cantidadPendiente}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = detalle.cantidadesRecepcion[linea.id].orEmpty(),
                        onValueChange = { alCambiarCantidad(linea.id, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cantidad a recibir (máx. ${linea.cantidadPendiente})") },
                        singleLine = true,
                        enabled = !detalle.procesando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            detalle.errorRecepcion?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = alRegistrar,
                enabled = !detalle.procesando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (detalle.accion == AccionOrden.RECIBIR) "Registrando…" else "Registrar recepción") }
        }
    }
}

@Composable
private fun HistorialRecepcion(detalle: DetalleOrdenUiState) {
    if (detalle.recepciones.isEmpty()) return
    Text("Historial de recepción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detalle.recepciones.forEachIndexed { indice, mov ->
                if (indice > 0) HorizontalDivider()
                Column {
                    Text("${mov.nombreProducto ?: "Producto"}: +${mov.cantidad}", fontWeight = FontWeight.Medium)
                    Text(
                        formatearFechaHoraPresentacion(mov.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
