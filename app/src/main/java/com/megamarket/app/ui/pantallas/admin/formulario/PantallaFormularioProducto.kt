package com.megamarket.app.ui.pantallas.admin.formulario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraSuperior
import com.megamarket.app.ui.tema.MegaMarcadorImagen
import kotlinx.coroutines.launch

@Composable
fun PantallaFormularioProducto(
    esEdicion: Boolean,
    alVolver: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var marca by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf("") }
    var precio by rememberSaveable { mutableStateOf("") }
    var precioOferta by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var activo by rememberSaveable { mutableStateOf(true) }
    var enOferta by rememberSaveable { mutableStateOf(false) }

    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val titulo = if (esEdicion) "Editar producto" else "Nuevo producto"
    val etiquetaAccion = if (esEdicion) "Guardar cambios" else "Crear producto"

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = titulo,
                alPulsarNavegacion = alVolver
            )
        },
        snackbarHost = { SnackbarHost(hostState = estadoMensaje) }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MegaMarcadorImagen)
                    .clickable { /* Selector de imagen pendiente */ },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar imagen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Agregar imagen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            CampoFormulario(valor = nombre, alCambiarValor = { nombre = it }, etiqueta = "Nombre")
            CampoFormulario(valor = marca, alCambiarValor = { marca = it }, etiqueta = "Marca")
            CampoFormulario(
                valor = descripcion,
                alCambiarValor = { descripcion = it },
                etiqueta = "Descripción",
                unaLinea = false
            )
            CampoFormulario(valor = categoria, alCambiarValor = { categoria = it }, etiqueta = "Categoría")
            CampoFormulario(
                valor = precio,
                alCambiarValor = { precio = it },
                etiqueta = "Precio",
                tipoTeclado = KeyboardType.Decimal
            )
            CampoFormulario(
                valor = precioOferta,
                alCambiarValor = { precioOferta = it },
                etiqueta = "Precio de oferta",
                tipoTeclado = KeyboardType.Decimal
            )
            CampoFormulario(
                valor = stock,
                alCambiarValor = { stock = it },
                etiqueta = "Stock",
                tipoTeclado = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilaInterruptor(
                etiqueta = "Producto activo",
                activado = activo,
                alCambiar = { activo = it }
            )
            FilaInterruptor(
                etiqueta = "Producto en oferta",
                activado = enOferta,
                alCambiar = { enOferta = it }
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    alcance.launch {
                        estadoMensaje.showSnackbar(
                            "Funcionalidad disponible al integrar la capa de datos"
                        )
                        alVolver()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(etiquetaAccion)
            }
        }
    }
}

@Composable
private fun CampoFormulario(
    valor: String,
    alCambiarValor: (String) -> Unit,
    etiqueta: String,
    unaLinea: Boolean = true,
    tipoTeclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiarValor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        label = { Text(etiqueta) },
        singleLine = unaLinea,
        minLines = if (unaLinea) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado)
    )
}

@Composable
private fun FilaInterruptor(
    etiqueta: String,
    activado: Boolean,
    alCambiar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = etiqueta, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = activado, onCheckedChange = alCambiar)
    }
}
