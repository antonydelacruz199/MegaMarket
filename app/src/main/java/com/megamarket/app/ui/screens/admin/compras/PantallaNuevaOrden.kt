package com.megamarket.app.ui.screens.admin.compras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.megamarket.app.data.repository.ProductoOpcion
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.state.LineaOrdenEditable
import com.megamarket.app.state.NuevaOrdenUiState
import com.megamarket.app.ui.components.BarraBusqueda
import com.megamarket.app.ui.components.BarraSuperior
import com.megamarket.app.viewmodel.OrdenesCompraViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaNuevaOrden(
    viewModel: OrdenesCompraViewModel,
    proveedorIdInicial: String?,
    alVolver: () -> Unit,
    alCreada: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val nueva = estado.nueva
    val mensajes = remember { SnackbarHostState() }
    var selectorProveedor by remember { mutableStateOf(false) }
    var selectorProducto by remember { mutableStateOf(false) }

    LaunchedEffect(proveedorIdInicial) { viewModel.iniciarNuevaOrden(proveedorIdInicial) }
    LaunchedEffect(nueva.error) {
        val mensaje = nueva.error ?: return@LaunchedEffect
        mensajes.showSnackbar(mensaje)
        viewModel.consumirErrorNueva()
    }
    LaunchedEffect(nueva.ordenCreadaId) {
        nueva.ordenCreadaId?.let(alCreada)
    }

    if (selectorProveedor) {
        SelectorProveedorDialog(
            nueva = nueva,
            alBuscar = viewModel::buscarProveedores,
            alElegir = {
                viewModel.seleccionarProveedor(it)
                selectorProveedor = false
            },
            alCerrar = { selectorProveedor = false }
        )
    }
    if (selectorProducto) {
        SelectorProductoDialog(
            nueva = nueva,
            alBuscar = viewModel::buscarProductos,
            alElegir = viewModel::agregarProducto,
            alCerrar = { selectorProducto = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperior(titulo = "Nueva orden de compra", alPulsarNavegacion = alVolver) },
        snackbarHost = { SnackbarHost(hostState = mensajes) }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Proveedor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TarjetaProveedorElegido(
                proveedor = nueva.proveedorSeleccionado,
                cargandoInicial = nueva.proveedorId != null && nueva.proveedorSeleccionado == null,
                deshabilitado = nueva.guardando,
                alElegir = {
                    viewModel.buscarProveedores("")
                    selectorProveedor = true
                }
            )

            Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (nueva.lineas.isEmpty()) {
                Text(
                    "Agrega los productos que quieres comprar, con cantidad y costo unitario.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            nueva.lineas.forEach { linea ->
                TarjetaLinea(
                    linea = linea,
                    deshabilitado = nueva.guardando,
                    alCambiarCantidad = { viewModel.actualizarCantidad(linea.producto.id, it) },
                    alCambiarCosto = { viewModel.actualizarCosto(linea.producto.id, it) },
                    alQuitar = { viewModel.quitarLinea(linea.producto.id) }
                )
            }
            OutlinedButton(
                onClick = {
                    viewModel.buscarProductos("")
                    selectorProducto = true
                },
                enabled = !nueva.guardando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Agregar producto")
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(
                    nueva.totalCentimos.formatearSoles(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "La orden se crea como SUGERIDA. Después debes aprobarla, pagarla y recibirla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = viewModel::crearOrden,
                enabled = !nueva.guardando && nueva.ordenCreadaId == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (nueva.guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear orden")
                }
            }
        }
    }
}

@Composable
private fun TarjetaProveedorElegido(
    proveedor: Proveedor?,
    cargandoInicial: Boolean,
    deshabilitado: Boolean,
    alElegir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when {
                    proveedor != null -> {
                        Text(proveedor.razonSocial, fontWeight = FontWeight.SemiBold)
                        Text("RUC ${proveedor.ruc}", style = MaterialTheme.typography.bodySmall)
                        if (proveedor.cuentaBancaria == null) {
                            Text(
                                "Sin cuenta bancaria: no podrá pagarse hasta registrarla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    cargandoInicial -> Text("Cargando proveedor…")
                    else -> Text("Ningún proveedor seleccionado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = alElegir, enabled = !deshabilitado) {
                Text(if (proveedor == null) "Elegir" else "Cambiar")
            }
        }
    }
}

@Composable
private fun TarjetaLinea(
    linea: LineaOrdenEditable,
    deshabilitado: Boolean,
    alCambiarCantidad: (String) -> Unit,
    alCambiarCosto: (String) -> Unit,
    alQuitar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(linea.producto.nombre, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${linea.producto.marca} · Stock ${linea.producto.stock}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = alQuitar, enabled = !deshabilitado) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar ${linea.producto.nombre}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linea.cantidadTexto,
                    onValueChange = alCambiarCantidad,
                    modifier = Modifier.weight(1f),
                    label = { Text("Cantidad") },
                    singleLine = true,
                    enabled = !deshabilitado,
                    isError = linea.cantidad == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = linea.costoTexto,
                    onValueChange = alCambiarCosto,
                    modifier = Modifier.weight(1f),
                    label = { Text("Costo unit. (S/)") },
                    singleLine = true,
                    enabled = !deshabilitado,
                    isError = linea.costoTexto.isNotEmpty() && linea.costoCentimos == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Text(
                "Subtotal: ${linea.subtotalCentimos.formatearSoles()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun SelectorProveedorDialog(
    nueva: NuevaOrdenUiState,
    alBuscar: (String) -> Unit,
    alElegir: (Proveedor) -> Unit,
    alCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Elegir proveedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarraBusqueda(
                    consulta = nueva.consultaProveedor,
                    alCambiarConsulta = alBuscar,
                    marcador = "RUC o razón social"
                )
                when {
                    nueva.cargandoProveedores -> Text("Buscando…")
                    nueva.errorProveedores != null ->
                        Text(nueva.errorProveedores, color = MaterialTheme.colorScheme.error)
                    nueva.proveedores.isEmpty() -> Text("No hay proveedores activos con ese criterio.")
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(nueva.proveedores, key = { it.id }) { proveedor ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { alElegir(proveedor) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(proveedor.razonSocial, fontWeight = FontWeight.SemiBold)
                                Text("RUC ${proveedor.ruc}", style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } }
    )
}

@Composable
private fun SelectorProductoDialog(
    nueva: NuevaOrdenUiState,
    alBuscar: (String) -> Unit,
    alElegir: (ProductoOpcion) -> Unit,
    alCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Agregar producto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarraBusqueda(
                    consulta = nueva.consultaProducto,
                    alCambiarConsulta = alBuscar,
                    marcador = "Nombre o marca"
                )
                when {
                    nueva.buscandoProductos -> Text("Buscando…")
                    nueva.errorProductos != null ->
                        Text(nueva.errorProductos, color = MaterialTheme.colorScheme.error)
                    nueva.productos.isEmpty() -> Text("No se encontraron productos activos.")
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(nueva.productos, key = { it.id }) { producto ->
                            val yaAgregado = nueva.lineas.any { it.producto.id == producto.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !yaAgregado) { alElegir(producto) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(producto.nombre, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${producto.marca} · Stock ${producto.stock} · Venta ${producto.precioVentaCentimos.formatearSoles()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    if (yaAgregado) "Agregado" else "Agregar",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (yaAgregado) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Listo") } }
    )
}
