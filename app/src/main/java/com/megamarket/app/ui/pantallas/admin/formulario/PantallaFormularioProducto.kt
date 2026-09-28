package com.megamarket.app.ui.pantallas.admin.formulario

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.componentes.BarraSuperior
import com.megamarket.modelo.Producto
import kotlinx.coroutines.launch

@Composable
fun PantallaFormularioProducto(
    producto: Producto? = null,
    alGuardar: (Producto) -> Unit,
    alVolver: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(producto?.nombre.orEmpty()) }
    var marca by rememberSaveable { mutableStateOf(producto?.marca.orEmpty()) }
    var descripcion by rememberSaveable { mutableStateOf(producto?.descripcion.orEmpty()) }
    var categoria by rememberSaveable {
        mutableStateOf(if (producto == null || producto.categoriaId == 0L) "" else producto.categoriaId.toString())
    }
    var precio by rememberSaveable { mutableStateOf(producto?.precioCentimos?.aTextoDecimal().orEmpty()) }
    var precioOferta by rememberSaveable {
        mutableStateOf(producto?.precioOfertaCentimos?.aTextoDecimal().orEmpty())
    }
    var stock by rememberSaveable { mutableStateOf(producto?.stock?.toString().orEmpty()) }
    var activo by rememberSaveable { mutableStateOf(producto?.activo ?: true) }
    var enOferta by rememberSaveable { mutableStateOf(producto?.esOferta ?: false) }

    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val titulo = if (producto == null) "Nuevo producto" else "Editar producto"
    val etiquetaAccion = if (producto == null) "Crear producto" else "Guardar cambios"

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
                    val mensaje = validarProducto(
                        nombre = nombre,
                        marca = marca,
                        precio = precio,
                        precioOferta = precioOferta,
                        stock = stock,
                        enOferta = enOferta
                    )
                    val guardado = if (mensaje != null) null else armarProducto(
                        id = producto?.id ?: 0L,
                        imagenKey = producto?.imagenKey.orEmpty(),
                        nombre = nombre,
                        marca = marca,
                        descripcion = descripcion,
                        categoria = categoria,
                        precio = precio,
                        precioOferta = precioOferta,
                        stock = stock,
                        enOferta = enOferta,
                        activo = activo
                    )
                    if (guardado == null) {
                        alcance.launch {
                            estadoMensaje.showSnackbar(mensaje ?: "Revisa los datos del producto")
                        }
                    } else {
                        alGuardar(guardado)
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

private fun validarProducto(
    nombre: String,
    marca: String,
    precio: String,
    precioOferta: String,
    stock: String,
    enOferta: Boolean
): String? {
    val precioCentimos = precio.aCentimos()
    val ofertaCentimos = precioOferta.aCentimos()
    val unidades = stock.trim().toIntOrNull()
    return when {
        nombre.isBlank() || marca.isBlank() -> "Completa nombre y marca"
        precioCentimos == null -> "Ingresa un precio válido"
        unidades == null || unidades < 0 -> "Ingresa un stock válido"
        enOferta && ofertaCentimos == null -> "Ingresa el precio de oferta"
        enOferta && ofertaCentimos != null && ofertaCentimos >= precioCentimos ->
            "La oferta debe ser menor al precio"
        else -> null
    }
}

private fun armarProducto(
    id: Long,
    imagenKey: String,
    nombre: String,
    marca: String,
    descripcion: String,
    categoria: String,
    precio: String,
    precioOferta: String,
    stock: String,
    enOferta: Boolean,
    activo: Boolean
): Producto? {
    val precioCentimos = precio.aCentimos()
    val unidades = stock.trim().toIntOrNull()
    if (nombre.isBlank() || precioCentimos == null || unidades == null || unidades < 0) return null
    return Producto(
        id = id,
        nombre = nombre.trim(),
        marca = marca.trim(),
        descripcion = descripcion.trim(),
        categoriaId = categoria.trim().toLongOrNull() ?: 0L,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = if (enOferta) precioOferta.aCentimos() else null,
        stock = unidades,
        imagenKey = imagenKey,
        esOferta = enOferta,
        activo = activo
    )
}

private fun Long.aTextoDecimal(): String {
    val soles = this / 100
    val centimos = (this % 100).toString().padStart(2, '0')
    return "$soles.$centimos"
}

private fun String.aCentimos(): Long? {
    val valor = trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (valor < 0) return null
    return (valor * 100).toLong()
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
