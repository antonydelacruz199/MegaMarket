package com.megamarket.app.ui.screens.admin.proveedores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.ui.components.BarraSuperior
import com.megamarket.app.ui.components.CargandoCentrado
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.InsigniaEstado
import com.megamarket.app.ui.components.TarjetaOrdenCompra
import com.megamarket.app.ui.theme.MegaContenedorError
import com.megamarket.app.ui.theme.MegaContenedorPrimario
import com.megamarket.app.ui.theme.MegaError
import com.megamarket.app.ui.theme.MegaPrimario
import com.megamarket.app.viewmodel.ProveedoresViewModel
import androidx.compose.material3.Scaffold

@Composable
fun PantallaDetalleProveedor(
    viewModel: ProveedoresViewModel,
    proveedorId: String,
    alVolver: () -> Unit,
    alEditar: (String) -> Unit,
    alNuevaOrden: (String) -> Unit,
    alDetalleOrden: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Se recarga al volver de editar para reflejar los cambios.
    LifecycleResumeEffect(proveedorId) {
        viewModel.cargarDetalle(proveedorId)
        onPauseOrDispose { }
    }
    val proveedor = estado.detalle

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BarraSuperior(
                titulo = "Proveedor",
                alPulsarNavegacion = alVolver,
                acciones = {
                    if (proveedor != null) {
                        IconButton(onClick = { alEditar(proveedor.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar proveedor")
                        }
                    }
                }
            )
        }
    ) { relleno ->
        when {
            proveedor == null && estado.cargandoDetalle -> CargandoCentrado(Modifier.padding(relleno))
            proveedor == null -> EstadoVacio(
                icono = Icons.Default.LocalShipping,
                titulo = "No se pudo cargar",
                descripcion = estado.errorDetalle ?: "No se encontró el proveedor.",
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Reintentar",
                alPulsarAccion = { viewModel.cargarDetalle(proveedorId) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatosProveedor(proveedor)
                estado.errorDetalle?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = { alNuevaOrden(proveedor.id) },
                    enabled = proveedor.activo,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Nueva orden de compra") }
                if (!proveedor.activo) {
                    Text(
                        "El proveedor está inactivo: no se pueden crear órdenes nuevas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "Órdenes recientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                estado.errorOrdenesProveedor?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.cargarDetalle(proveedorId) }) { Text("Reintentar") }
                }
                when {
                    estado.cargandoDetalle -> Text("Cargando…", style = MaterialTheme.typography.bodyMedium)
                    estado.ordenesProveedor.isEmpty() && estado.errorOrdenesProveedor == null ->
                        Text("Este proveedor todavía no tiene órdenes.", style = MaterialTheme.typography.bodyMedium)
                }
                estado.ordenesProveedor.forEach { orden ->
                    TarjetaOrdenCompra(
                        orden = orden,
                        alPulsar = { alDetalleOrden(orden.id) },
                        mostrarProveedor = false
                    )
                }
            }
        }
    }
}

@Composable
private fun DatosProveedor(proveedor: Proveedor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = proveedor.razonSocial,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                if (proveedor.activo) {
                    InsigniaEstado("Activo", MegaContenedorPrimario, MegaPrimario)
                } else {
                    InsigniaEstado("Inactivo", MegaContenedorError, MegaError)
                }
            }
            Dato("RUC", proveedor.ruc)
            Dato("Teléfono", proveedor.telefono)
            Dato("Correo", proveedor.correo)
            Dato("Cuenta bancaria", proveedor.cuentaBancaria ?: "Sin cuenta (no se podrá pagar)")
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String?) {
    Column {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(valor?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}
