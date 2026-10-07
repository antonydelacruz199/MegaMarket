package com.megamarket.app.ui.screens.admin.formulario

import android.graphics.Bitmap
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.model.estado.ProductoFormUiState
import androidx.compose.ui.res.stringResource
import com.megamarket.modelo.Categoria
import com.megamarket.app.R
import com.megamarket.app.ui.components.BarraSuperior
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.ImagenProducto
import com.megamarket.app.ui.theme.MegaContenedorSecundario
import com.megamarket.app.ui.theme.MegaSecundario
import com.megamarket.app.viewmodel.ProductoFormViewModel

@Composable
fun PantallaFormularioProducto(
    viewModel: ProductoFormViewModel,
    alVolver: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val estadoMensaje = remember { SnackbarHostState() }
    val selectorImagen = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.seleccionarImagen(uri)
    }

    LaunchedEffect(estado.error) {
        val mensaje = estado.error ?: return@LaunchedEffect
        estadoMensaje.showSnackbar(mensaje)
        viewModel.consumirError()
    }
    LaunchedEffect(estado.guardadoCorrectamente) {
        if (estado.guardadoCorrectamente) alVolver()
    }

    val titulo = if (estado.esEdicion) "Editar producto" else "Nuevo producto"

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = titulo,
                alPulsarNavegacion = alVolver
            )
        },
        snackbarHost = { SnackbarHost(hostState = estadoMensaje) }
    ) { relleno ->
        when {
            estado.cargando -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            estado.noDisponible -> EstadoVacio(
                icono = Icons.Default.Add,
                titulo = stringResource(R.string.producto_no_disponible),
                descripcion = "No se encontró el producto que intentas editar.",
                modifier = Modifier.padding(relleno),
                etiquetaAccion = "Volver",
                alPulsarAccion = alVolver
            )

            else -> ContenidoFormulario(
                estado = estado,
                viewModel = viewModel,
                alElegirImagen = {
                    selectorImagen.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.padding(relleno)
            )
        }
    }
}

@Composable
private fun ContenidoFormulario(
    estado: ProductoFormUiState,
    viewModel: ProductoFormViewModel,
    alElegirImagen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val etiquetaAccion = if (estado.esEdicion) "Guardar cambios" else "Crear producto"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SeccionFormulario(
            titulo = "Imagen",
            descripcion = "Opcional. El cliente verá esta foto en el catálogo.",
            colorAcento = MaterialTheme.colorScheme.primary
        ) {
            SelectorImagen(
                claveVisible = estado.imagenVisibleKey,
                copiando = estado.procesandoImagen,
                alElegir = alElegirImagen,
                alQuitar = viewModel::quitarImagen,
                cargarImagen = viewModel::cargarImagen
            )
        }
        SeccionFormulario(
            titulo = "Información",
            descripcion = "Datos que identifican el producto en la tienda.",
            colorAcento = MaterialTheme.colorScheme.primary
        ) {
            CampoFormulario(valor = estado.nombre, alCambiarValor = viewModel::actualizarNombre, etiqueta = "Nombre")
            CampoFormulario(valor = estado.marca, alCambiarValor = viewModel::actualizarMarca, etiqueta = "Marca")
            CampoFormulario(
                valor = estado.descripcion,
                alCambiarValor = viewModel::actualizarDescripcion,
                etiqueta = stringResource(R.string.descripcion),
                unaLinea = false
            )
            SelectorCategoria(
                categorias = estado.categorias,
                categoriaId = estado.categoriaId,
                alSeleccionar = viewModel::actualizarCategoriaId,
                habilitado = !estado.ocupado
            )
        }
        SeccionFormulario(
            titulo = "Precio y stock",
            descripcion = "El precio se escribe en soles y el stock en unidades.",
            colorAcento = MegaSecundario
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoFormulario(
                    valor = estado.precio,
                    alCambiarValor = viewModel::actualizarPrecio,
                    etiqueta = "Precio",
                    tipoTeclado = KeyboardType.Decimal,
                    prefijo = "S/",
                    modifier = Modifier.weight(1f)
                )
                CampoFormulario(
                    valor = estado.stock,
                    alCambiarValor = viewModel::actualizarStock,
                    etiqueta = "Stock",
                    tipoTeclado = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            FilaInterruptor(
                etiqueta = "Producto en oferta",
                descripcion = "Muestra un precio rebajado junto al precio normal.",
                activado = estado.esOferta,
                alCambiar = viewModel::actualizarOferta
            )
            if (estado.esOferta) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MegaContenedorSecundario
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        CampoFormulario(
                            valor = estado.descuentoPorcentaje,
                            alCambiarValor = viewModel::actualizarDescuentoPorcentaje,
                            etiqueta = "Descuento (%)",
                            tipoTeclado = KeyboardType.Number,
                            prefijo = "%",
                            colorAcento = MegaSecundario,
                            ayuda = "Entre 1 y 99. El precio rebajado se calcula automáticamente."
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
                activado = estado.activo,
                alCambiar = viewModel::actualizarActivo
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = viewModel::guardarProducto,
            enabled = !estado.procesandoImagen && !estado.guardando,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (estado.guardando) "Guardando..." else etiquetaAccion)
        }
    }
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
    alQuitar: () -> Unit,
    cargarImagen: suspend (clave: String, lado: Int) -> Bitmap?
) {
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
            cargar = { cargarImagen(claveVisible, 720) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCategoria(
    categorias: List<Categoria>,
    categoriaId: Long,
    alSeleccionar: (Long) -> Unit,
    habilitado: Boolean
) {
    var expandido by remember { mutableStateOf(false) }
    val seleccionada = categorias.firstOrNull { it.id == categoriaId }?.nombre.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { if (habilitado) expandido = !expandido }
    ) {
        OutlinedTextField(
            value = seleccionada.ifEmpty { "Selecciona una categoría" },
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.categoria)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            categorias.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.nombre) },
                    onClick = {
                        alSeleccionar(categoria.id)
                        expandido = false
                    }
                )
            }
        }
    }
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
