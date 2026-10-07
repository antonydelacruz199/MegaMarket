package com.megamarket.app.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.CategoriaRepository
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository
import com.megamarket.app.model.ValidacionProducto
import com.megamarket.app.model.aTextoDecimal
import com.megamarket.app.model.estado.ProductoFormUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductoFormViewModel(
    private val estadoGuardado: SavedStateHandle,
    private val productos: ProductoRepository,
    private val categorias: CategoriaRepository,
    private val imagenes: ImagenRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(restaurar() ?: ProductoFormUiState())
    val estado: StateFlow<ProductoFormUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            categorias.observarActivas().collect { lista ->
                _estado.update { actual ->
                    val categoriaId = when {
                        actual.categoriaId > 0L && lista.any { it.id == actual.categoriaId } ->
                            actual.categoriaId
                        lista.isNotEmpty() -> lista.first().id
                        else -> 0L
                    }
                    actual.copy(categorias = lista, categoriaId = categoriaId).also { respaldar(it) }
                }
            }
        }
        val productoId = estadoGuardado.get<Long>(ARG_PRODUCTO_ID) ?: 0L
        if (estadoGuardado.get<Boolean>(CLAVE_RESTAURABLE) != true && productoId != 0L) {
            cargarProducto(productoId)
        }
    }

    fun actualizarNombre(valor: String) = actualizar { it.copy(nombre = valor) }
    fun actualizarMarca(valor: String) = actualizar { it.copy(marca = valor) }
    fun actualizarDescripcion(valor: String) = actualizar { it.copy(descripcion = valor) }
    fun actualizarCategoriaId(valor: Long) = actualizar { it.copy(categoriaId = valor) }
    fun actualizarPrecio(valor: String) = actualizar { it.copy(precio = valor) }
    fun actualizarPrecioOferta(valor: String) = actualizar { it.copy(precioOferta = valor) }
    fun actualizarStock(valor: String) = actualizar { it.copy(stock = valor) }
    fun actualizarOferta(valor: Boolean) = actualizar { it.copy(esOferta = valor) }
    fun actualizarActivo(valor: Boolean) = actualizar { it.copy(activo = valor) }

    fun cargarProducto(id: Long) {
        if (id == 0L) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null, noDisponible = false) }
            val producto = try {
                productos.obtenerPorId(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.update {
                    it.copy(cargando = false, noDisponible = true, error = "No se pudo cargar el producto")
                }
                return@launch
            }
            if (producto == null) {
                _estado.update { it.copy(cargando = false, noDisponible = true) }
                return@launch
            }
            actualizar {
                it.copy(
                    id = producto.id,
                    nombre = producto.nombre,
                    marca = producto.marca,
                    descripcion = producto.descripcion,
                    categoriaId = producto.categoriaId,
                    precio = producto.precioCentimos.aTextoDecimal(),
                    precioOferta = producto.precioOfertaCentimos?.aTextoDecimal().orEmpty(),
                    stock = producto.stock.toString(),
                    esOferta = producto.esOferta,
                    activo = producto.activo,
                    imagenActualKey = producto.imagenKey,
                    imagenQuitada = false,
                    remoteId = producto.remoteId,
                    remoteVersion = producto.remoteVersion,
                    remoteUpdatedAt = producto.remoteUpdatedAt,
                    remoteDeletedAt = producto.remoteDeletedAt,
                    cargando = false
                )
            }
        }
    }

    fun seleccionarImagen(uri: Uri) {
        val actual = _estado.value
        if (actual.procesandoImagen || actual.guardando) return
        viewModelScope.launch {
            _estado.update { it.copy(procesandoImagen = true, error = null) }
            val clave = try {
                imagenes.guardar(uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (clave == null) {
                _estado.update { it.copy(procesandoImagen = false, error = "No se pudo leer la imagen") }
                return@launch
            }
            val previa = _estado.value.imagenPendienteKey
            actualizar { it.copy(imagenPendienteKey = clave, imagenQuitada = false, procesandoImagen = false) }
            if (!previa.isNullOrBlank() && previa != clave) imagenes.eliminarEnSegundoPlano(previa)
        }
    }

    fun quitarImagen() {
        val actual = _estado.value
        if (actual.procesandoImagen || actual.guardando) return
        val pendiente = actual.imagenPendienteKey
        actualizar { it.copy(imagenPendienteKey = null, imagenQuitada = true) }
        if (!pendiente.isNullOrBlank()) imagenes.eliminarEnSegundoPlano(pendiente)
    }

    fun guardarProducto() {
        val actual = _estado.value
        if (actual.ocupado || actual.guardadoCorrectamente || actual.noDisponible) return
        val producto = when (val resultado = ValidacionProducto.validar(actual)) {
            is ValidacionProducto.Resultado.Invalido -> {
                _estado.update { it.copy(error = resultado.mensaje) }
                return
            }
            is ValidacionProducto.Resultado.Valido -> resultado.producto
        }
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val guardado = try {
                if (producto.id == 0L) productos.insertar(producto) > 0 else productos.actualizar(producto)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!guardado) {
                _estado.update { it.copy(guardando = false, error = "No se pudo guardar el producto") }
                return@launch
            }
            val anterior = actual.imagenActualKey
            if (anterior.isNotBlank() && anterior != producto.imagenKey) {
                imagenes.eliminarEnSegundoPlano(anterior)
            }
            actualizar {
                it.copy(
                    guardando = false,
                    guardadoCorrectamente = true,
                    imagenActualKey = producto.imagenKey,
                    imagenPendienteKey = null,
                    imagenQuitada = false
                )
            }
        }
    }

    fun consumirError() {
        _estado.update { it.copy(error = null) }
    }

    suspend fun cargarImagen(clave: String, lado: Int): Bitmap? = imagenes.cargar(clave, lado)

    override fun onCleared() {
        val actual = _estado.value
        val pendiente = actual.imagenPendienteKey
        if (!pendiente.isNullOrBlank() && !actual.guardando && !actual.guardadoCorrectamente) {
            imagenes.eliminarEnSegundoPlano(pendiente)
        }
    }

    private fun actualizar(cambio: (ProductoFormUiState) -> ProductoFormUiState) {
        _estado.update(cambio)
        respaldar(_estado.value)
    }

    private fun respaldar(estado: ProductoFormUiState) {
        estadoGuardado[CLAVE_ID] = estado.id
        estadoGuardado[CLAVE_NOMBRE] = estado.nombre
        estadoGuardado[CLAVE_MARCA] = estado.marca
        estadoGuardado[CLAVE_DESCRIPCION] = estado.descripcion
        estadoGuardado[CLAVE_CATEGORIA_ID] = estado.categoriaId
        estadoGuardado[CLAVE_PRECIO] = estado.precio
        estadoGuardado[CLAVE_PRECIO_OFERTA] = estado.precioOferta
        estadoGuardado[CLAVE_STOCK] = estado.stock
        estadoGuardado[CLAVE_ES_OFERTA] = estado.esOferta
        estadoGuardado[CLAVE_ACTIVO] = estado.activo
        estadoGuardado[CLAVE_IMAGEN_ACTUAL] = estado.imagenActualKey
        estadoGuardado[CLAVE_IMAGEN_PENDIENTE] = estado.imagenPendienteKey
        estadoGuardado[CLAVE_IMAGEN_QUITADA] = estado.imagenQuitada
        estadoGuardado[CLAVE_REMOTE_ID] = estado.remoteId
        estadoGuardado[CLAVE_RESTAURABLE] = true
    }

    private fun restaurar(): ProductoFormUiState? {
        if (estadoGuardado.get<Boolean>(CLAVE_RESTAURABLE) != true) return null
        return ProductoFormUiState(
            id = estadoGuardado[CLAVE_ID] ?: 0L,
            nombre = estadoGuardado[CLAVE_NOMBRE] ?: "",
            marca = estadoGuardado[CLAVE_MARCA] ?: "",
            descripcion = estadoGuardado[CLAVE_DESCRIPCION] ?: "",
            categoriaId = estadoGuardado[CLAVE_CATEGORIA_ID] ?: 0L,
            precio = estadoGuardado[CLAVE_PRECIO] ?: "",
            precioOferta = estadoGuardado[CLAVE_PRECIO_OFERTA] ?: "",
            stock = estadoGuardado[CLAVE_STOCK] ?: "",
            esOferta = estadoGuardado[CLAVE_ES_OFERTA] ?: false,
            activo = estadoGuardado[CLAVE_ACTIVO] ?: true,
            imagenActualKey = estadoGuardado[CLAVE_IMAGEN_ACTUAL] ?: "",
            imagenPendienteKey = estadoGuardado[CLAVE_IMAGEN_PENDIENTE],
            imagenQuitada = estadoGuardado[CLAVE_IMAGEN_QUITADA] ?: false,
            remoteId = estadoGuardado[CLAVE_REMOTE_ID]
        )
    }

    companion object {
        const val ARG_PRODUCTO_ID = "productoId"

        private const val CLAVE_RESTAURABLE = "form_restaurable"
        private const val CLAVE_ID = "form_id"
        private const val CLAVE_NOMBRE = "form_nombre"
        private const val CLAVE_MARCA = "form_marca"
        private const val CLAVE_DESCRIPCION = "form_descripcion"
        private const val CLAVE_CATEGORIA_ID = "form_categoria_id"
        private const val CLAVE_PRECIO = "form_precio"
        private const val CLAVE_PRECIO_OFERTA = "form_precio_oferta"
        private const val CLAVE_STOCK = "form_stock"
        private const val CLAVE_ES_OFERTA = "form_es_oferta"
        private const val CLAVE_ACTIVO = "form_activo"
        private const val CLAVE_IMAGEN_ACTUAL = "form_imagen_actual"
        private const val CLAVE_IMAGEN_PENDIENTE = "form_imagen_pendiente"
        private const val CLAVE_IMAGEN_QUITADA = "form_imagen_quitada"
        private const val CLAVE_REMOTE_ID = "form_remote_id"
    }
}
