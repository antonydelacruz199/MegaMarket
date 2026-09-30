package com.megamarket.app.ui.pantallas.admin.formulario

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.megamarket.app.ui.tema.MegaContenedorSecundario
import com.megamarket.app.ui.tema.MegaSecundario
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megamarket.app.data.local.AlmacenImagenes
import com.megamarket.app.ui.componentes.BarraSuperior
import com.megamarket.app.ui.componentes.ImagenProducto
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaFormularioProducto(
    producto: Producto? = null,
    alGuardar: (Producto, (Boolean) -> Unit) -> Unit,
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
    var clavePendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var imagenQuitada by rememberSaveable { mutableStateOf(false) }
    var copiando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val conservarPendiente = remember { mutableStateOf(false) }

    val contexto = LocalContext.current
    val estadoMensaje = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val selectorImagen = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            copiando = true
            val clave = withContext(Dispatchers.IO) {
                AlmacenImagenes.guardar(contexto, uri)
            }
            val previa = clavePendiente
            if (clave == null) {
                copiando = false
                estadoMensaje.showSnackbar("No se pudo leer la imagen")
                return@launch
            }
            if (!previa.isNullOrBlank() && previa != clave) {
                withContext(Dispatchers.IO) { AlmacenImagenes.eliminar(contexto, previa) }
            }
            clavePendiente = clave
            imagenQuitada = false
            copiando = false
        }
    }
    val pendienteActual by rememberUpdatedState(clavePendiente)
    DisposableEffect(Unit) {
        onDispose {
            val actividad = contexto as? Activity
            val pendiente = pendienteActual
            if (
                !pendiente.isNullOrBlank() &&
                !conservarPendiente.value &&
                actividad?.isChangingConfigurations != true
            ) {
                AlmacenImagenes.eliminar(contexto, pendiente)
            }
        }
    }
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
            val claveVisible = clavePendiente?.takeIf { it.isNotBlank() }
                ?: if (imagenQuitada) "" else producto?.imagenKey.orEmpty()
            SeccionFormulario(
                titulo = "Imagen",
                descripcion = "Opcional. El cliente verá esta foto en el catálogo.",
                colorAcento = MaterialTheme.colorScheme.primary
            ) {
                SelectorImagen(
                    claveVisible = claveVisible,
                    copiando = copiando,
                    alElegir = {
                        selectorImagen.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    alQuitar = {
                        val pendiente = clavePendiente
                        clavePendiente = null
                        imagenQuitada = true
                        if (!pendiente.isNullOrBlank()) {
                            alcance.launch(Dispatchers.IO) {
                                AlmacenImagenes.eliminar(contexto, pendiente)
                            }
                        }
                    }
                )
            }
            SeccionFormulario(
                titulo = "Información",
                descripcion = "Datos que identifican el producto en la tienda.",
                colorAcento = MaterialTheme.colorScheme.primary
            ) {
                CampoFormulario(valor = nombre, alCambiarValor = { nombre = it }, etiqueta = "Nombre")
                CampoFormulario(valor = marca, alCambiarValor = { marca = it }, etiqueta = "Marca")
                CampoFormulario(
                    valor = descripcion,
                    alCambiarValor = { descripcion = it },
                    etiqueta = "Descripción",
                    unaLinea = false
                )
                CampoFormulario(
                    valor = categoria,
                    alCambiarValor = { categoria = it },
                    etiqueta = "Categoría",
                    tipoTeclado = KeyboardType.Number,
                    ayuda = "Número de categoría. Déjalo vacío si aún no aplica."
                )
            }
            SeccionFormulario(
                titulo = "Precio y stock",
                descripcion = "El precio se escribe en soles y el stock en unidades.",
                colorAcento = MegaSecundario
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoFormulario(
                        valor = precio,
                        alCambiarValor = { precio = it },
                        etiqueta = "Precio",
                        tipoTeclado = KeyboardType.Decimal,
                        prefijo = "S/",
                        modifier = Modifier.weight(1f)
                    )
                    CampoFormulario(
                        valor = stock,
                        alCambiarValor = { stock = it },
                        etiqueta = "Stock",
                        tipoTeclado = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                FilaInterruptor(
                    etiqueta = "Producto en oferta",
                    descripcion = "Muestra un precio rebajado junto al precio normal.",
                    activado = enOferta,
                    alCambiar = { enOferta = it }
                )
                if (enOferta) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MegaContenedorSecundario
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            CampoFormulario(
                                valor = precioOferta,
                                alCambiarValor = { precioOferta = it },
                                etiqueta = "Precio de oferta",
                                tipoTeclado = KeyboardType.Decimal,
                                prefijo = "S/",
                                colorAcento = MegaSecundario,
                                ayuda = "Debe ser menor que el precio normal."
                            )
                        }
                    }
                }
            }
            SeccionFormulario(
                titulo = "Publicación",
                descripcion = "Controla si el producto aparece en el catálogo.",
                colorAcento = MaterialTheme.colorScheme.primary
            ) {
                FilaInterruptor(
                    etiqueta = "Producto activo",
                    descripcion = "Si está apagado, no se publica para el cliente.",
                    activado = activo,
                    alCambiar = { activo = it }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    if (copiando || guardando) return@Button
                    val mensaje = validarProducto(
                        nombre = nombre,
                        marca = marca,
                        precio = precio,
                        precioOferta = precioOferta,
                        stock = stock,
                        enOferta = enOferta
                    )
                    if (mensaje != null) {
                        alcance.launch { estadoMensaje.showSnackbar(mensaje) }
                        return@Button
                    }
                    val claveActual = producto?.imagenKey.orEmpty()
                    val claveImagen = clavePendiente?.takeIf { it.isNotBlank() }
                        ?: if (imagenQuitada) "" else claveActual
                    val armado = armarProducto(
                        id = producto?.id ?: 0L,
                        imagenKey = claveImagen,
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
                    if (armado == null) {
                        alcance.launch { estadoMensaje.showSnackbar("Revisa los datos del producto") }
                        return@Button
                    }
                    guardando = true
                    conservarPendiente.value = true
                    alGuardar(armado) { guardado ->
                        guardando = false
                        if (guardado) {
                            if (claveActual.isNotBlank() && claveActual != claveImagen) {
                                AlmacenImagenes.eliminar(contexto, claveActual)
                            }
                            clavePendiente = null
                        } else {
                            conservarPendiente.value = false
                            alcance.launch {
                                estadoMensaje.showSnackbar("No se pudo registrar el producto")
                            }
                        }
                    }
                },
                enabled = !copiando && !guardando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (guardando) "Guardando..." else etiquetaAccion)
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

@Composable
private fun SeccionFormulario(
    titulo: String,
    descripcion: String,
    colorAcento: Color,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colorAcento)
            )
            Text(
                text = titulo,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = descripcion,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = contenido
            )
        }
    }
}

@Composable
private fun SelectorImagen(
    claveVisible: String,
    copiando: Boolean,
    alElegir: () -> Unit,
    alQuitar: () -> Unit
) {
    val contexto = LocalContext.current
    if (copiando) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (claveVisible.isNotBlank()) {
        ImagenProducto(
            identificador = claveVisible,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            descripcion = "Imagen seleccionada",
            cargar = { AlmacenImagenes.bitmap(contexto, claveVisible, 720) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = alElegir, modifier = Modifier.weight(1f)) {
                Text("Cambiar imagen")
            }
            TextButton(onClick = alQuitar) {
                Text("Quitar")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sin imagen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = alElegir) {
                Text("Elegir de la galería")
            }
        }
    }
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
    modifier: Modifier = Modifier,
    unaLinea: Boolean = true,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    prefijo: String? = null,
    ayuda: String? = null,
    colorAcento: Color = MaterialTheme.colorScheme.primary
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiarValor,
        modifier = modifier.fillMaxWidth(),
        label = { Text(etiqueta) },
        prefix = prefijo?.let { texto -> { Text(texto) } },
        supportingText = ayuda?.let { texto -> { Text(texto) } },
        singleLine = unaLinea,
        minLines = if (unaLinea) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorAcento,
            focusedLabelColor = colorAcento,
            cursorColor = colorAcento
        )
    )
}

@Composable
private fun FilaInterruptor(
    etiqueta: String,
    activado: Boolean,
    alCambiar: (Boolean) -> Unit,
    descripcion: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = etiqueta, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) {
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = activado, onCheckedChange = alCambiar)
    }
}
