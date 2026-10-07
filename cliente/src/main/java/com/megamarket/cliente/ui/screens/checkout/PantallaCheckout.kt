package com.megamarket.cliente.ui.screens.checkout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.ui.components.BarraSuperior
import com.megamarket.cliente.viewmodel.CheckoutViewModel

@Composable
fun PantallaCheckout(
    viewModel: CheckoutViewModel,
    alVolver: () -> Unit,
    alConfirmar: (pedidoId: Long) -> Unit
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
                titulo = stringResource(R.string.direccion_entrega),
                alPulsarNavegacion = alVolver
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
            Campo(stringResource(R.string.departamento), estado.departamento, viewModel::actualizarDepartamento, !estado.cargando)
            Campo(stringResource(R.string.provincia), estado.provincia, viewModel::actualizarProvincia, !estado.cargando)
            Campo(stringResource(R.string.distrito), estado.distrito, viewModel::actualizarDistrito, !estado.cargando)
            Campo(stringResource(R.string.direccion), estado.direccion, viewModel::actualizarDireccion, !estado.cargando)
            Campo(
                etiqueta = stringResource(R.string.telefono),
                valor = estado.telefono,
                alCambiar = viewModel::actualizarTelefono,
                habilitado = !estado.cargando,
                teclado = KeyboardType.Phone
            )
            if (estado.error != null) {
                Text(
                    text = estado.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::confirmarCompra,
                enabled = !estado.cargando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (estado.cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.confirmar_compra))
                }
            }
        }
    }
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
