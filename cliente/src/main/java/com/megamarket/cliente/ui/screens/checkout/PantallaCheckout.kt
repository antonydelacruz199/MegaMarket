package com.megamarket.cliente.ui.screens.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.model.FranjaEntrega
import com.megamarket.cliente.model.LineaCarrito
import com.megamarket.cliente.model.ModalidadPagoCheckout
import com.megamarket.cliente.state.CheckoutUiState
import com.megamarket.cliente.state.PasoCheckout
import com.megamarket.cliente.ui.components.BarraSuperior
import com.megamarket.cliente.viewmodel.CheckoutViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaCheckout(
    viewModel: CheckoutViewModel,
    alVolver: () -> Unit,
    alConfirmar: (pedidoId: Long) -> Unit,
    alAnadirProductos: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.pedidoCreadoId) {
        val pedidoId = estado.pedidoCreadoId ?: return@LaunchedEffect
        viewModel.consumirNavegacion()
        alConfirmar(pedidoId)
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = when (estado.paso) {
                    PasoCheckout.DIRECCION -> stringResource(R.string.direccion_entrega)
                    PasoCheckout.FRANJA -> "Franja de entrega"
                    PasoCheckout.PAGO -> "Forma de pago"
                    PasoCheckout.RESUMEN -> "Confirmar pedido"
                },
                alPulsarNavegacion = {
                    if (estado.paso == PasoCheckout.DIRECCION) alVolver()
                    else viewModel.volverPaso()
                }
            )
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            IndicadorPasos(paso = estado.paso)
            Spacer(modifier = Modifier.height(16.dp))
            when (estado.paso) {
                PasoCheckout.DIRECCION -> PasoDireccion(estado, viewModel)
                PasoCheckout.FRANJA -> PasoFranja(estado, viewModel)
                PasoCheckout.PAGO -> PasoPago(estado, viewModel)
                PasoCheckout.RESUMEN -> PasoResumen(estado, viewModel, alAnadirProductos)
            }
            if (estado.error != null) {
                Text(
                    text = estado.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AccionesPaso(estado, viewModel)
        }
    }
}

@Composable
private fun IndicadorPasos(paso: PasoCheckout) {
    val pasos = listOf(
        PasoCheckout.DIRECCION to "Dirección",
        PasoCheckout.FRANJA to "Franja",
        PasoCheckout.PAGO to "Pago",
        PasoCheckout.RESUMEN to "Resumen"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pasos.forEach { (valor, etiqueta) ->
            val activo = paso == valor
            val hecho = valor.ordinal < paso.ordinal
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = when {
                    activo -> MaterialTheme.colorScheme.primaryContainer
                    hecho -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = etiqueta,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PasoDireccion(estado: CheckoutUiState, viewModel: CheckoutViewModel) {
    Text(
        text = "¿Dónde entregamos tu pedido?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Campo("Departamento", estado.departamento, viewModel::actualizarDepartamento, !estado.cargando)
    Campo("Provincia", estado.provincia, viewModel::actualizarProvincia, !estado.cargando)
    Campo("Distrito", estado.distrito, viewModel::actualizarDistrito, !estado.cargando)
    Campo("Dirección", estado.direccion, viewModel::actualizarDireccion, !estado.cargando)
    Campo(
        etiqueta = "Teléfono",
        valor = estado.telefono,
        alCambiar = viewModel::actualizarTelefono,
        habilitado = !estado.cargando,
        teclado = KeyboardType.Phone
    )
}

@Composable
private fun PasoFranja(estado: CheckoutUiState, viewModel: CheckoutViewModel) {
    Text(
        text = "Elige fecha y horario",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = "Solo se muestran franjas de hoy y mañana con el cupo informado por el servidor.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    when {
        estado.cargandoFranjas -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(" Cargando franjas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        estado.errorFranjas != null && estado.franjas.isEmpty() -> {
            Text(estado.errorFranjas.orEmpty(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::cargarFranjas) { Text("Reintentar") }
        }
        else -> {
            if (estado.errorFranjas != null) {
                Text(estado.errorFranjas.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.fechasDisponibles.forEach { fecha ->
                    FilterChip(
                        selected = estado.fechaSeleccionada == fecha,
                        onClick = { viewModel.seleccionarFecha(fecha) },
                        label = { Text(FranjaEntrega.formatearFecha(fecha)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (estado.franjasDeFecha.isEmpty()) {
                Text("No hay horarios para esta fecha.")
            } else {
                estado.franjasDeFecha.forEach { franja ->
                    TarjetaFranja(
                        franja = franja,
                        seleccionada = estado.franjaSeleccionadaId == franja.id,
                        alElegir = { viewModel.seleccionarFranja(franja.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            TextButton(onClick = viewModel::cargarFranjas) { Text("Actualizar cupos") }
        }
    }
}

@Composable
private fun TarjetaFranja(
    franja: FranjaEntrega,
    seleccionada: Boolean,
    alElegir: () -> Unit
) {
    val habilitada = franja.disponible
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = seleccionada,
                enabled = habilitada,
                role = Role.RadioButton,
                onClick = alElegir
            ),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (seleccionada) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (seleccionada) 2.dp else 1.dp,
            color = when {
                seleccionada -> MaterialTheme.colorScheme.primary
                !habilitada -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        color = when {
            !habilitada -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            seleccionada -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = seleccionada,
                onClick = null,
                enabled = habilitada
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(franja.etiquetaHorario, fontWeight = FontWeight.SemiBold)
                Text(
                    text = franja.etiquetaCupo,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (habilitada) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun PasoPago(estado: CheckoutUiState, viewModel: CheckoutViewModel) {
    Text(
        text = "¿Cómo pagarás?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = "Esta elección no confirma el pago. Si eliges pago en línea, lo harás justo después de confirmar el pedido.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    ModalidadPagoCheckout.entries.forEach { modalidad ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .selectable(
                    selected = estado.modalidadPago == modalidad,
                    role = Role.RadioButton,
                    onClick = { viewModel.seleccionarModalidad(modalidad) }
                ),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = if (estado.modalidadPago == modalidad) 2.dp else 1.dp,
                color = if (estado.modalidadPago == modalidad) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                RadioButton(
                    selected = estado.modalidadPago == modalidad,
                    onClick = null
                )
                Column {
                    Text(modalidad.titulo, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = modalidad.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PasoResumen(
    estado: CheckoutUiState,
    viewModel: CheckoutViewModel,
    alAnadirProductos: () -> Unit
) {
    Text(
        text = "Revisa tu pedido",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    BloqueResumen("Productos") {
        if (estado.lineasCarrito.isEmpty()) {
            Text("No hay productos en el carrito.", color = MaterialTheme.colorScheme.error)
        } else {
            estado.lineasCarrito.forEach { linea ->
                LineaProductoResumen(
                    linea = linea,
                    alAumentar = { viewModel.aumentarCantidad(linea.producto.id, linea.cantidad) },
                    alDisminuir = { viewModel.disminuirCantidad(linea.producto.id, linea.cantidad) },
                    alQuitar = { viewModel.quitarProducto(linea.producto.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Total ${estado.totalCarritoCentimos.formatearSoles()}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        TextButton(onClick = alAnadirProductos) { Text("Añadir productos") }
    }
    BloqueResumen("Dirección") {
        Text("${estado.direccion}, ${estado.distrito}")
        Text("${estado.provincia}, ${estado.departamento}")
        Text("Tel. ${estado.telefono}")
        TextButton(onClick = { viewModel.irAPaso(PasoCheckout.DIRECCION) }) { Text("Editar") }
    }
    BloqueResumen("Entrega") {
        val franja = estado.franjaSeleccionada
        if (franja != null) {
            Text(franja.etiquetaCompleta, fontWeight = FontWeight.Medium)
            Text(franja.etiquetaCupo, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Sin franja seleccionada", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { viewModel.irAPaso(PasoCheckout.FRANJA) }) { Text("Cambiar franja") }
    }
    BloqueResumen("Pago") {
        Text(estado.modalidadPago?.titulo ?: "Sin modalidad", fontWeight = FontWeight.Medium)
        Text(
            text = estado.modalidadPago?.descripcion.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { viewModel.irAPaso(PasoCheckout.PAGO) }) { Text("Cambiar") }
    }
}

@Composable
private fun LineaProductoResumen(
    linea: LineaCarrito,
    alAumentar: () -> Unit,
    alDisminuir: () -> Unit,
    alQuitar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontWeight = FontWeight.SemiBold)
                Text(
                    text = linea.producto.precioVigenteCentimos.formatearSoles(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = alQuitar) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar producto")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alDisminuir, enabled = linea.cantidad > 1) { Text("−") }
            Text("${linea.cantidad}", fontWeight = FontWeight.Medium)
            IconButton(
                onClick = alAumentar,
                enabled = linea.cantidad < linea.producto.stock
            ) { Text("+") }
            Text(
                text = linea.subtotalCentimos.formatearSoles(),
                modifier = Modifier.padding(start = 12.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BloqueResumen(titulo: String, contenido: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            contenido()
        }
    }
}

@Composable
private fun AccionesPaso(estado: CheckoutUiState, viewModel: CheckoutViewModel) {
    when (estado.paso) {
        PasoCheckout.DIRECCION -> BotonPrincipal("Continuar", !estado.cargando) {
            viewModel.irAPaso(PasoCheckout.FRANJA)
        }
        PasoCheckout.FRANJA -> BotonPrincipal(
            "Continuar",
            !estado.cargando && !estado.cargandoFranjas && estado.franjaSeleccionada?.disponible == true
        ) {
            viewModel.irAPaso(PasoCheckout.PAGO)
        }
        PasoCheckout.PAGO -> BotonPrincipal(
            "Continuar",
            !estado.cargando && estado.modalidadPago != null
        ) {
            viewModel.irAPaso(PasoCheckout.RESUMEN)
        }
        PasoCheckout.RESUMEN -> {
            Text(
                text = if (estado.modalidadPago == ModalidadPagoCheckout.ONLINE) {
                    "Al confirmar, el pedido pasa a preparación. Cuando el picker lo confirme podrás pagar con QR."
                } else {
                    "Al confirmar verás el resumen final. Podrás cancelar mientras el pedido siga confirmado."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = viewModel::confirmarCompra,
                enabled = !estado.cargando && estado.lineasCarrito.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (estado.cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.confirmar_compra))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::volverPaso,
                enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Volver") }
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, habilitado: Boolean, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) { Text(texto) }
}

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    habilitado: Boolean,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        enabled = habilitado,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado)
    )
}
