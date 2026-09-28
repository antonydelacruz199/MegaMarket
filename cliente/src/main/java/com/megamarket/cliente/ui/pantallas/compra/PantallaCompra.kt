package com.megamarket.cliente.ui.pantallas.compra

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.componentes.BarraSuperior
import com.megamarket.cliente.viewmodel.ViewModelCompra

@Composable
fun PantallaCompra(
    viewModel: ViewModelCompra,
    alVolver: () -> Unit,
    alConfirmar: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.confirmado) {
        if (estado.confirmado) alConfirmar()
    }

    Scaffold(topBar = { BarraSuperior(titulo = "Dirección de entrega", alPulsarNavegacion = alVolver) }) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Campo("Departamento", estado.departamento, viewModel::actualizarDepartamento)
            Campo("Provincia", estado.provincia, viewModel::actualizarProvincia)
            Campo("Distrito", estado.distrito, viewModel::actualizarDistrito)
            Campo("Dirección", estado.direccion, viewModel::actualizarDireccion)
            Campo(
                etiqueta = "Teléfono",
                valor = estado.telefono,
                alCambiar = viewModel::actualizarTelefono,
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
                onClick = viewModel::confirmar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Confirmar compra") }
        }
    }
}

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado)
    )
}
