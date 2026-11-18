package com.megamarket.app.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.CategoriaRepository
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository
import com.megamarket.app.data.repository.ResultadoSubidaImagen
import com.megamarket.app.data.repository.ResultadoSincronizacion
import com.megamarket.app.data.repository.SincronizacionRepository
import com.megamarket.app.model.ValidacionProducto
import com.megamarket.app.model.aTextoDecimal
import com.megamarket.app.state.FormularioProductoUiState
import com.megamarket.modelo.ClavesImagen
import com.megamarket.modelo.SolicitudImagenProducto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FormularioProductoViewModel(
    private val estadoGuardado: SavedStateHandle,
    private val productos: ProductoRepository,
    private val categorias: CategoriaRepository,
    private val imagenes: ImagenRepository,
    private val sincronizacion: SincronizacionRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(restaurar() ?: FormularioProductoUiState())
    val estado: StateFlow<FormularioProductoUiState> = _estado.asStateFlow()

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

    fun actualizarNombre(valor: String) = actualizarFormulario { it.copy(nombre = valor) }
    fun actualizarMarca(valor: String) = actualizarFormulario { it.copy(marca = valor) }
    fun actualizarDescripcion(valor: String) = actualizarFormulario { it.copy(descripcion = valor) }
    fun actualizarCategoriaId(valor: Long) = actualizarFormulario { it.copy(categoriaId = valor) }
    fun actualizarPrecio(valor: String) = actualizarFormulario { it.copy(precio = valor) }
    fun actualizarDescuentoPorcentaje(valor: String) = actualizarFormulario { it.copy(descuentoPorcentaje = valor) }
    fun actualizarStock(valor: String) = actualizarFormulario { it.copy(stock = valor) }
    fun actualizarCodigoBarras(valor: String) = actualizarFormulario { it.copy(codigoBarras = valor) }
    fun actualizarPasillo(valor: String) = actualizarFormulario { it.copy(pasillo = valor) }
    fun actualizarOferta(valor: Boolean) = actualizarFormulario { it.copy(esOferta = valor) }
    fun actualizarActivo(valor: Boolean) = actualizarFormulario { it.copy(activo = valor) }

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
            actualizarFormulario {
                it.copy(
                    id = producto.id,
                    nombre = producto.nombre,
                    marca = producto.marca,
                    descripcion = producto.descripcion,
                    categoriaId = producto.categoriaId,
                    precio = producto.precioCentimos.aTextoDecimal(),
                    descuentoPorcentaje = if (producto.descuentoPorcentaje > 0) {
                        producto.descuentoPorcentaje.toString()
                    } else if (producto.esOferta) {
                        producto.descuentoEfectivo.toString()
                    } else {
                        ""
                    },
                    stock = producto.stock.toString(),
                    codigoBarras = producto.codigoBarras.orEmpty(),
                    pasillo = producto.pasillo.orEmpty(),
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
                imagenes.guardarImagen(uri)
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
            actualizarFormulario { it.copy(imagenPendienteKey = clave, imagenQuitada = false, procesandoImagen = false) }
            if (!previa.isNullOrBlank() && previa != clave) imagenes.eliminarEnSegundoPlano(previa)
        }
    }

    fun quitarImagen() {
        val actual = _estado.value
        if (actual.procesandoImagen || actual.guardando) return
        val pendiente = actual.imagenPendienteKey
        actualizarFormulario { it.copy(imagenPendienteKey = null, imagenQuitada = true) }
        if (!pendiente.isNullOrBlank()) imagenes.eliminarEnSegundoPlano(pendiente)
    }

    fun cancelarDuplicado() { _estado.update { it.copy(duplicadoId=null) } }
    fun editarDuplicado() {
        val id=_estado.value.duplicadoId ?: return
        val actual=_estado.value
        viewModelScope.launch {
            try {
                if(actual.id>0 && actual.remoteId.isNullOrBlank()) productos.descartarDuplicadoLocal(actual.id)
                cancelarDuplicado()
                cargarProducto(id)
            } catch(e: Exception) { _estado.update { it.copy(duplicadoId=null,error=e.message) } }
        }
    }

    fun solicitudImagen(remoteId: String?, imagenKey: String): SolicitudImagenProducto? =
        imagenes.solicitud(remoteId, imagenKey)

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
        val pendienteLocal = actual.imagenPendienteKey?.takeIf { ClavesImagen.esLocal(it) }
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val guardado = try {
                val enRoom = if (producto.id != 0L) productos.obtenerPorId(producto.id) else null
                val claveGuardada = claveParaRoom(actual, pendienteLocal, enRoom?.imagenKey)
                val productoLocal = producto.copy(
                    imagenKey = claveGuardada,
                    remoteId = producto.remoteId ?: enRoom?.remoteId,
                    remoteVersion = producto.remoteVersion ?: enRoom?.remoteVersion,
                    remoteUpdatedAt = producto.remoteUpdatedAt ?: enRoom?.remoteUpdatedAt,
                    remoteDeletedAt = producto.remoteDeletedAt ?: enRoom?.remoteDeletedAt
                )
                val duplicado = productos.buscarDuplicado(productoLocal.nombre, productoLocal.id)
                if (duplicado != null) {
                    _estado.update { it.copy(guardando = false, duplicadoId = duplicado.id) }
                    return@launch
                }
                val idLocal = if (productoLocal.id == 0L) {
                    val nuevo = productos.crearProducto(productoLocal)
                    if (nuevo <= 0L) 0L else nuevo
                } else if (productos.actualizarProducto(productoLocal)) {
                    productoLocal.id
                } else {
                    0L
                }
                if (idLocal == 0L) {
                    _estado.update { it.copy(guardando = false, error = "No se pudo guardar el producto") }
                    return@launch
                }
                actualizarFormulario {
                    it.copy(
                        id = idLocal,
                        remoteId = productoLocal.remoteId,
                        remoteVersion = productoLocal.remoteVersion,
                        remoteUpdatedAt = productoLocal.remoteUpdatedAt,
                        remoteDeletedAt = productoLocal.remoteDeletedAt
                    )
                }
                if (pendienteLocal == null && actual.imagenQuitada && ClavesImagen.esLocal(actual.imagenActualKey)) {
                    imagenes.eliminarEnSegundoPlano(actual.imagenActualKey)
                }
                idLocal to claveGuardada
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.update {
                    it.copy(guardando = false, error = error.message ?: "No se pudo guardar el producto")
                }
                return@launch
            }
            val (idLocal, claveGuardada) = guardado
            // Siempre intenta sincronizar ya: código de barras y pasillo deben llegar a Neon
            // antes de que el picker los use. Si solo se encola WorkManager, el guardado local
            // parece exitoso pero el servidor sigue sin ubicación.
            val avisoSync = try {
                when (val resultado = sincronizacion.sincronizarPendientes()) {
                    is ResultadoSincronizacion.Reintentar -> resultado.motivo
                    else -> null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.message
            }
            val fresco = productos.obtenerPorId(idLocal)
            val remoteId = fresco?.remoteId?.trim().orEmpty()
            if (fresco != null) {
                actualizarFormulario {
                    it.copy(
                        remoteId = fresco.remoteId,
                        remoteVersion = fresco.remoteVersion,
                        remoteUpdatedAt = fresco.remoteUpdatedAt,
                        remoteDeletedAt = fresco.remoteDeletedAt,
                        codigoBarras = fresco.codigoBarras.orEmpty(),
                        pasillo = fresco.pasillo.orEmpty()
                    )
                }
            }
            if (remoteId.isEmpty()) {
                val detalle = productos.ultimoErrorProducto(idLocal) ?: avisoSync
                _estado.update {
                    it.copy(
                        guardando = false,
                        error = detalle ?: "El producto todavía no está en el servidor. Reintenta cuando haya conexión."
                    )
                }
                return@launch
            }
            val errorUbicacion = productos.ultimoErrorProducto(idLocal)
            if (errorUbicacion != null && pendienteLocal == null) {
                _estado.update {
                    it.copy(
                        guardando = false,
                        error = "Guardado local ok, pero no se pudo sincronizar con el servidor: $errorUbicacion"
                    )
                }
                return@launch
            }
            val fotoLocal = pendienteLocal
            if (fotoLocal == null) {
                if (avisoSync != null) {
                    _estado.update {
                        it.copy(
                            guardando = false,
                            error = "Guardado local ok, pero la sincronización quedó pendiente: $avisoSync"
                        )
                    }
                } else {
                    finalizarGuardado(fresco?.imagenKey ?: claveGuardada)
                }
                return@launch
            }
            when (val subida = imagenes.subirProducto(remoteId, fotoLocal)) {
                is ResultadoSubidaImagen.Error -> {
                    _estado.update { it.copy(guardando = false, error = subida.mensaje) }
                }
                is ResultadoSubidaImagen.Ok -> {
                    val registrada = productos.registrarImagenRemota(
                        idLocal,
                        subida.imagenKey,
                        subida.version,
                        subida.updatedAt
                    )
                    if (!registrada) {
                        _estado.update {
                            it.copy(
                                guardando = false,
                                error = "La imagen se subió, pero no se pudo guardar la referencia local"
                            )
                        }
                        return@launch
                    }
                    imagenes.eliminarEnSegundoPlano(fotoLocal)
                    if (ClavesImagen.esLocal(actual.imagenActualKey)) {
                        imagenes.eliminarEnSegundoPlano(actual.imagenActualKey)
                    }
                    actualizarFormulario {
                        it.copy(
                            guardando = false,
                            guardadoCorrectamente = true,
                            imagenActualKey = subida.imagenKey,
                            imagenPendienteKey = null,
                            imagenQuitada = false,
                            remoteId = remoteId,
                            remoteVersion = subida.version ?: it.remoteVersion,
                            remoteUpdatedAt = subida.updatedAt ?: it.remoteUpdatedAt
                        )
                    }
                }
            }
        }
    }

    private fun claveParaRoom(
        actual: FormularioProductoUiState,
        pendienteLocal: String?,
        claveEnRoom: String?
    ): String {
        val remota = actual.imagenActualKey.takeIf { ClavesImagen.esRemotaProducto(it) }
            ?: claveEnRoom?.takeIf { ClavesImagen.esRemotaProducto(it) }
        return when {
            actual.imagenQuitada -> ""
            pendienteLocal != null -> remota.orEmpty()
            remota != null -> remota
            ClavesImagen.esLocal(actual.imagenActualKey) -> actual.imagenActualKey
            else -> ""
        }
    }

    private fun finalizarGuardado(clave: String) {
        actualizarFormulario {
            it.copy(
                guardando = false,
                guardadoCorrectamente = true,
                imagenActualKey = clave,
                imagenPendienteKey = null,
                imagenQuitada = false
            )
        }
    }

    fun consumirError() {
        _estado.update { it.copy(error = null) }
    }

    suspend fun cargarImagen(clave: String, lado: Int): Bitmap? = imagenes.cargarImagen(clave, lado)

    override fun onCleared() {
        val actual = _estado.value
        val pendiente = actual.imagenPendienteKey
        if (!pendiente.isNullOrBlank() && !actual.guardando && !actual.guardadoCorrectamente) {
            imagenes.eliminarEnSegundoPlano(pendiente)
        }
    }

    private fun actualizarFormulario(cambio: (FormularioProductoUiState) -> FormularioProductoUiState) {
        _estado.update(cambio)
        respaldar(_estado.value)
    }

    private fun respaldar(estado: FormularioProductoUiState) {
        estadoGuardado[CLAVE_ID] = estado.id
        estadoGuardado[CLAVE_NOMBRE] = estado.nombre
        estadoGuardado[CLAVE_MARCA] = estado.marca
        estadoGuardado[CLAVE_DESCRIPCION] = estado.descripcion
        estadoGuardado[CLAVE_CATEGORIA_ID] = estado.categoriaId
        estadoGuardado[CLAVE_PRECIO] = estado.precio
        estadoGuardado[CLAVE_DESCUENTO] = estado.descuentoPorcentaje
        estadoGuardado[CLAVE_STOCK] = estado.stock
        estadoGuardado[CLAVE_CODIGO] = estado.codigoBarras
        estadoGuardado[CLAVE_PASILLO] = estado.pasillo
        estadoGuardado[CLAVE_ES_OFERTA] = estado.esOferta
        estadoGuardado[CLAVE_ACTIVO] = estado.activo
        estadoGuardado[CLAVE_IMAGEN_ACTUAL] = estado.imagenActualKey
        estadoGuardado[CLAVE_IMAGEN_PENDIENTE] = estado.imagenPendienteKey
        estadoGuardado[CLAVE_IMAGEN_QUITADA] = estado.imagenQuitada
        estadoGuardado[CLAVE_REMOTE_ID] = estado.remoteId
        estadoGuardado[CLAVE_REMOTE_VERSION] = estado.remoteVersion
        estadoGuardado[CLAVE_REMOTE_UPDATED] = estado.remoteUpdatedAt
        estadoGuardado[CLAVE_RESTAURABLE] = true
    }

    private fun restaurar(): FormularioProductoUiState? {
        if (estadoGuardado.get<Boolean>(CLAVE_RESTAURABLE) != true) return null
        return FormularioProductoUiState(
            id = estadoGuardado[CLAVE_ID] ?: 0L,
            nombre = estadoGuardado[CLAVE_NOMBRE] ?: "",
            marca = estadoGuardado[CLAVE_MARCA] ?: "",
            descripcion = estadoGuardado[CLAVE_DESCRIPCION] ?: "",
            categoriaId = estadoGuardado[CLAVE_CATEGORIA_ID] ?: 0L,
            precio = estadoGuardado[CLAVE_PRECIO] ?: "",
            descuentoPorcentaje = estadoGuardado[CLAVE_DESCUENTO] ?: "",
            stock = estadoGuardado[CLAVE_STOCK] ?: "",
            codigoBarras = estadoGuardado[CLAVE_CODIGO] ?: "",
            pasillo = estadoGuardado[CLAVE_PASILLO] ?: "",
            esOferta = estadoGuardado[CLAVE_ES_OFERTA] ?: false,
            activo = estadoGuardado[CLAVE_ACTIVO] ?: true,
            imagenActualKey = estadoGuardado[CLAVE_IMAGEN_ACTUAL] ?: "",
            imagenPendienteKey = estadoGuardado[CLAVE_IMAGEN_PENDIENTE],
            imagenQuitada = estadoGuardado[CLAVE_IMAGEN_QUITADA] ?: false,
            remoteId = estadoGuardado[CLAVE_REMOTE_ID],
            remoteVersion = estadoGuardado[CLAVE_REMOTE_VERSION],
            remoteUpdatedAt = estadoGuardado[CLAVE_REMOTE_UPDATED]
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
        private const val CLAVE_DESCUENTO = "form_descuento_porcentaje"
        private const val CLAVE_STOCK = "form_stock"
        private const val CLAVE_CODIGO = "form_codigo_barras"
        private const val CLAVE_PASILLO = "form_pasillo"
        private const val CLAVE_ES_OFERTA = "form_es_oferta"
        private const val CLAVE_ACTIVO = "form_activo"
        private const val CLAVE_IMAGEN_ACTUAL = "form_imagen_actual"
        private const val CLAVE_IMAGEN_PENDIENTE = "form_imagen_pendiente"
        private const val CLAVE_IMAGEN_QUITADA = "form_imagen_quitada"
        private const val CLAVE_REMOTE_ID = "form_remote_id"
        private const val CLAVE_REMOTE_VERSION = "form_remote_version"
        private const val CLAVE_REMOTE_UPDATED = "form_remote_updated_at"
    }
}
