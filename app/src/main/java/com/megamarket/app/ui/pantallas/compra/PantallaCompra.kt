package com.megamarket.app.ui.pantallas.compra

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraSuperior
import com.megamarket.app.ui.componentes.EncabezadoSeccion

@Composable
fun PantallaCompra(
    alVolver: () -> Unit,
    alConfirmarCompra: () -> Unit
) {
    var departamento by rememberSaveable { mutableStateOf("") }
    var provincia by rememberSaveable { mutableStateOf("") }
    var distrito by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var referencia by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var intentoEnvio by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "Finalizar compra",
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
            EncabezadoSeccion(titulo = "Dirección de entrega")
            Spacer(modifier = Modifier.height(8.dp))
            CampoCompra(
                valor = departamento,
                alCambiarValor = { departamento = it },
                etiqueta = "Departamento",
                mostrarError = intentoEnvio && departamento.isBlank()
            )
            CampoCompra(
                valor = provincia,
                alCambiarValor = { provincia = it },
                etiqueta = "Provincia",
                mostrarError = intentoEnvio && provincia.isBlank()
            )
            CampoCompra(
                valor = distrito,
                alCambiarValor = { distrito = it },
                etiqueta = "Distrito",
                mostrarError = intentoEnvio && distrito.isBlank()
            )
            CampoCompra(
                valor = direccion,
                alCambiarValor = { direccion = it },
                etiqueta = "Dirección",
                mostrarError = intentoEnvio && direccion.isBlank()
            )
            CampoCompra(
                valor = referencia,
                alCambiarValor = { referencia = it },
                etiqueta = "Referencia",
                mostrarError = false
            )
            CampoCompra(
                valor = telefono,
                alCambiarValor = { telefono = it },
                etiqueta = "Teléfono",
                tipoTeclado = KeyboardType.Phone,
                mostrarError = intentoEnvio && telefono.isBlank()
            )

            Spacer(modifier = Modifier.height(20.dp))
            EncabezadoSeccion(titulo = "Resumen del pedido")
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
                    LineaResumen("Subtotal", "S/ --.--")
                    LineaResumen("Envío", "S/ --.--")
                    LineaResumen("Total", "S/ --.--", destacar = true)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    intentoEnvio = true
                    val esValido = listOf(departamento, provincia, distrito, direccion, telefono)
                        .all { it.isNotBlank() }
                    if (esValido) {
                        alConfirmarCompra()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirmar compra")
            }
        }
    }
}

@Composable
private fun CampoCompra(
    valor: String,
    alCambiarValor: (String) -> Unit,
    etiqueta: String,
    mostrarError: Boolean,
    tipoTeclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiarValor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        label = { Text(etiqueta) },
        singleLine = true,
        isError = mostrarError,
        supportingText = if (mostrarError) {
            { Text("Campo requerido") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado)
    )
}

@Composable
private fun LineaResumen(
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
