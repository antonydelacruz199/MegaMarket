package com.megamarket.app.ui.screens.admin.proveedores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.state.FormularioProveedorUiState
import com.megamarket.app.ui.components.BarraSuperior
import com.megamarket.app.ui.components.CargandoCentrado
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.viewmodel.ProveedoresViewModel

@Composable
fun PantallaFormularioProveedor(
    viewModel: ProveedoresViewModel,
    proveedorId: String?,
    alVolver: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val formulario = estado.formulario
    val mensajes = remember { SnackbarHostState() }

    LaunchedEffect(proveedorId) { viewModel.iniciarFormulario(proveedorId) }
    LaunchedEffect(formulario.error) {
        val mensaje = formulario.error ?: return@LaunchedEffect
        if (!formulario.noEncontrado) {
            mensajes.showSnackbar(mensaje)
            viewModel.consumirErrorFormulario()
        }
    }
    LaunchedEffect(formulario.guardado) {
        if (formulario.guardado) alVolver()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BarraSuperior(
                titulo = if (formulario.esEdicion) "Editar proveedor" else "Nuevo proveedor",
                alPulsarNavegacion = alVolver
            )
        },
        snackbarHost = { SnackbarHost(hostState = mensajes) }
    ) { relleno ->
        when {
            formulario.cargando -> CargandoCentrado(Modifier.padding(relleno))
            formulario.noEncontrado -> EstadoVacio(
                icono = Icons.Default.LocalShipping,
                titulo = "Proveedor no disponible",
                descripcion = formulario.error ?: "No se encontró el proveedor que intentas editar.",
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Volver",
                alPulsarAccion = alVolver
            )
            else -> ContenidoFormulario(
                formulario = formulario,
                viewModel = viewModel,
                modifier = Modifier.padding(relleno)
            )
        }
    }
}

@Composable
private fun ContenidoFormulario(
    formulario: FormularioProveedorUiState,
    viewModel: ProveedoresViewModel,
    modifier: Modifier = Modifier
) {
    val bloqueado = formulario.guardando
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = formulario.ruc,
            onValueChange = viewModel::actualizarRuc,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("RUC (11 dígitos)") },
            singleLine = true,
            enabled = !bloqueado,
            isError = formulario.errores.ruc != null,
            supportingText = formulario.errores.ruc?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = formulario.razonSocial,
            onValueChange = viewModel::actualizarRazonSocial,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Razón social") },
            singleLine = true,
            enabled = !bloqueado,
            isError = formulario.errores.razonSocial != null,
            supportingText = formulario.errores.razonSocial?.let { { Text(it) } }
        )
        OutlinedTextField(
            value = formulario.telefono,
            onValueChange = viewModel::actualizarTelefono,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Teléfono (opcional)") },
            singleLine = true,
            enabled = !bloqueado,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        OutlinedTextField(
            value = formulario.correo,
            onValueChange = viewModel::actualizarCorreo,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Correo (opcional)") },
            singleLine = true,
            enabled = !bloqueado,
            isError = formulario.errores.correo != null,
            supportingText = formulario.errores.correo?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = formulario.cuentaBancaria,
            onValueChange = viewModel::actualizarCuentaBancaria,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cuenta bancaria (necesaria para pagar)") },
            singleLine = true,
            enabled = !bloqueado,
            supportingText = { Text("Se usa como destino en el pago simulado.") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Proveedor activo", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = formulario.activo,
                onCheckedChange = viewModel::actualizarActivo,
                enabled = !bloqueado
            )
        }
        Button(
            onClick = viewModel::guardar,
            enabled = !bloqueado,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (formulario.guardando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (formulario.esEdicion) "Guardar cambios" else "Registrar proveedor")
            }
        }
    }
}
