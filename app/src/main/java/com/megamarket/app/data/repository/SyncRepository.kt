package com.megamarket.app.data.repository

import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.dao.MovimientoInventarioDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.dao.SyncMetadataDao
import com.megamarket.app.data.local.entities.CategoriaEntity
import com.megamarket.app.data.local.entities.OperacionPendienteEntity
import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.app.data.local.entities.SyncMetadataEntity
import com.megamarket.app.data.remote.RetrofitProvider
import com.megamarket.app.data.remote.api.CategoriaApi
import com.megamarket.app.data.remote.api.InventarioApi
import com.megamarket.app.data.remote.api.ProductoApi
import com.megamarket.app.data.remote.dto.GuardarProductoRequest
import com.megamarket.app.data.remote.dto.MovimientoInventarioRequest
import com.megamarket.app.data.session.SessionStore
import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class SyncRepository(
    private val operacionDao: OperacionPendienteDao,
    private val productoDao: ProductoDao,
    private val categoriaDao: CategoriaDao,
    private val movimientoDao: MovimientoInventarioDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val sessionStore: SessionStore,
    private val baseUrlApi: String,
    private val remote: RetrofitProvider? = null
) {
    private val productoApi: ProductoApi? by lazy {
        remote?.productoApi ?: RetrofitProvider.crear(baseUrlApi)?.productoApi
    }
    private val inventarioApi: InventarioApi? by lazy {
        remote?.inventarioApi ?: RetrofitProvider.crear(baseUrlApi)?.inventarioApi
    }
    private val categoriaApi: CategoriaApi? by lazy {
        remote?.categoriaApi ?: RetrofitProvider.crear(baseUrlApi)?.categoriaApi
    }

    data class ResumenSync(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?
    )

    fun observarResumen(): Flow<ResumenSync> = combine(
        operacionDao.observarPendientesActivos(),
        operacionDao.observarErrores(),
        syncMetadataDao.observar()
    ) { pendientes, errores, meta ->
        ResumenSync(
            pendientes = pendientes,
            errores = errores,
            ultimaExitosa = meta?.ultimaSincronizacionExitosa,
            ultimoError = meta?.ultimoErrorGeneral
        )
    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        registrarIntento(ahora, null)

        val pendientes = operacionDao.obtenerParaEnviar()
        if (baseUrlApi.isBlank() || productoApi == null) {
            registrarIntento(ahora, "API REST no configurada (MEGAMARKET_API_BASE_URL).")
            return@withContext ResultadoSincronizacion.Reintentar(
                "Servidor no configurado. Las operaciones permanecen pendientes."
            )
        }
        if (sessionStore.accessToken.isNullOrBlank()) {
            registrarIntento(ahora, "Sesión remota requerida (HTTP 401).")
            return@withContext ResultadoSincronizacion.Reintentar("Autenticación requerida.")
        }

        when (val pull = pullCategorias()) {
            is Envio.Reintentable -> {
                registrarIntento(ahora, pull.mensaje)
                return@withContext ResultadoSincronizacion.Reintentar(pull.mensaje)
            }
            is Envio.ErrorDefinitivo -> {
                if (pull.mensaje.contains("401")) sessionStore.marcarSesionInvalidaRemota()
                registrarIntento(ahora, pull.mensaje)
                return@withContext ResultadoSincronizacion.Reintentar(pull.mensaje)
            }
            else -> Unit
        }

        if (pendientes.isEmpty()) {
            syncMetadataDao.guardar(
                SyncMetadataEntity(
                    id = SyncMetadataEntity.FILA_UNICA,
                    ultimaSincronizacionExitosa = System.currentTimeMillis(),
                    ultimoIntento = System.currentTimeMillis(),
                    ultimoErrorGeneral = null
                )
            )
            return@withContext ResultadoSincronizacion.Exito
        }

        var huboExito = false
        var debeReintentar = false
        var ultimoMensaje: String? = null

        val ordenadas = pendientes.sortedWith(
            compareBy(
                { prioridad(it.operacion) },
                { it.fechaCreacion }
            )
        )

        for (op in ordenadas) {
            operacionDao.marcarEnviando(op.id, System.currentTimeMillis())
            when (val r = enviarOperacion(op)) {
                is Envio.Exito -> {
                    operacionDao.marcarSincronizado(op.id, System.currentTimeMillis())
                    huboExito = true
                }
                is Envio.ErrorDefinitivo -> {
                    if (r.mensaje.contains("401")) sessionStore.marcarSesionInvalidaRemota()
                    operacionDao.marcarError(op.id, r.mensaje, System.currentTimeMillis())
                    ultimoMensaje = r.mensaje
                }
                is Envio.Reintentable -> {
                    operacionDao.marcarError(op.id, r.mensaje, System.currentTimeMillis())
                    debeReintentar = true
                    ultimoMensaje = r.mensaje
                }
            }
        }

        if (huboExito) {
            syncMetadataDao.guardar(
                SyncMetadataEntity(
                    id = SyncMetadataEntity.FILA_UNICA,
                    ultimaSincronizacionExitosa = System.currentTimeMillis(),
                    ultimoIntento = System.currentTimeMillis(),
                    ultimoErrorGeneral = if (debeReintentar) ultimoMensaje else null
                )
            )
        } else {
            registrarIntento(System.currentTimeMillis(), ultimoMensaje)
        }

        when {
            debeReintentar -> ResultadoSincronizacion.Reintentar(ultimoMensaje)
            huboExito -> ResultadoSincronizacion.Exito
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }

    private fun prioridad(operacion: String): Int = when (operacion) {
        TipoOperacionPendiente.CREAR_PRODUCTO -> 0
        TipoOperacionPendiente.ACTUALIZAR_PRODUCTO -> 1
        TipoOperacionPendiente.ELIMINAR_PRODUCTO -> 2
        TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO -> 3
        else -> 9
    }

    private suspend fun pullCategorias(): Envio {
        val api = categoriaApi ?: return Envio.Reintentable("CategoriaApi no disponible")
        return try {
            val respuesta = api.obtenerCategorias()
            if (respuesta.code() == 401) {
                return Envio.ErrorDefinitivo("HTTP 401: sesión inválida / auth necesaria")
            }
            if (!respuesta.isSuccessful) {
                return clasificarHttp(respuesta.code(), respuesta.message())
            }
            for (dto in respuesta.body().orEmpty()) {
                val porRemote = categoriaDao.obtenerPorRemoteId(dto.id)
                val porNombre = categoriaDao.obtenerPorNombre(dto.nombre)
                val base = porRemote ?: porNombre
                if (base != null) {
                    categoriaDao.actualizar(
                        base.copy(
                            remoteId = dto.id,
                            nombre = dto.nombre,
                            remoteVersion = dto.version,
                            remoteUpdatedAt = dto.updatedAt,
                            remoteDeletedAt = dto.deletedAt
                        )
                    )
                } else {
                    categoriaDao.insertar(
                        CategoriaEntity(
                            remoteId = dto.id,
                            nombre = dto.nombre,
                            remoteVersion = dto.version,
                            remoteUpdatedAt = dto.updatedAt,
                            remoteDeletedAt = dto.deletedAt
                        )
                    )
                }
            }
            Envio.Exito
        } catch (e: SocketTimeoutException) {
            Envio.Reintentable("Timeout: ${e.message}")
        } catch (e: IOException) {
            Envio.Reintentable("Red: ${e.message}")
        } catch (e: Exception) {
            Envio.Reintentable(e.message ?: "Error pull categorías")
        }
    }

    private suspend fun enviarOperacion(op: OperacionPendienteEntity): Envio {
        return try {
            when (op.operacion) {
                TipoOperacionPendiente.CREAR_PRODUCTO -> enviarCrearProducto(op)
                TipoOperacionPendiente.ACTUALIZAR_PRODUCTO -> enviarActualizarProducto(op)
                TipoOperacionPendiente.ELIMINAR_PRODUCTO -> enviarEliminarProducto(op)
                TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO -> enviarMovimiento(op)
                else -> Envio.ErrorDefinitivo("Operación no soportada: ${op.operacion}")
            }
        } catch (e: SocketTimeoutException) {
            Envio.Reintentable("Timeout: ${e.message}")
        } catch (e: IOException) {
            Envio.Reintentable("Red: ${e.message}")
        } catch (e: HttpException) {
            clasificarHttp(e.code(), e.message())
        } catch (e: Exception) {
            Envio.Reintentable(e.message ?: "Error desconocido")
        }
    }

    private suspend fun enviarCrearProducto(op: OperacionPendienteEntity): Envio {
        val api = productoApi ?: return Envio.Reintentable("ProductoApi no disponible")
        val producto = cargarProducto(op) ?: return Envio.ErrorDefinitivo("Producto local no existe")
        val categoriaRemota = categoriaDao.obtenerPorId(producto.categoriaId)?.remoteId
        if (categoriaRemota.isNullOrBlank()) {
            return Envio.Reintentable("Falta remoteId de categoría ${producto.categoriaId}")
        }
        val body = construirRequest(producto, categoriaRemota, op.uuidOperacion, incluirStock = true)
        val respuesta = api.crear(body)
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        respuesta.body()?.let { cuerpo ->
            productoDao.actualizar(
                producto.copy(
                    remoteId = cuerpo.id,
                    remoteVersion = cuerpo.version,
                    remoteUpdatedAt = cuerpo.updatedAt
                )
            )
        }
        return Envio.Exito
    }

    private suspend fun enviarActualizarProducto(op: OperacionPendienteEntity): Envio {
        val api = productoApi ?: return Envio.Reintentable("ProductoApi no disponible")
        val producto = cargarProducto(op) ?: return Envio.ErrorDefinitivo("Producto local no existe")
        val remoteId = producto.remoteId
            ?: return Envio.Reintentable("Falta remoteId del producto ${producto.id}")
        if (producto.remoteVersion == null) {
            return Envio.Reintentable("Falta remoteVersion del producto ${producto.id}")
        }
        val categoriaRemota = categoriaDao.obtenerPorId(producto.categoriaId)?.remoteId
        if (categoriaRemota.isNullOrBlank()) {
            return Envio.Reintentable("Falta remoteId de categoría ${producto.categoriaId}")
        }
        val body = construirRequest(producto, categoriaRemota, op.uuidOperacion, incluirStock = false)
        val respuesta = api.actualizar(remoteId, body)
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        respuesta.body()?.let { cuerpo ->
            productoDao.actualizar(
                producto.copy(
                    remoteVersion = cuerpo.version,
                    remoteUpdatedAt = cuerpo.updatedAt
                )
            )
        }
        return Envio.Exito
    }

    private suspend fun enviarEliminarProducto(op: OperacionPendienteEntity): Envio {
        val api = productoApi ?: return Envio.Reintentable("ProductoApi no disponible")
        val producto = cargarProducto(op) ?: return Envio.ErrorDefinitivo("Producto local no existe")
        val remoteId = producto.remoteId
            ?: return Envio.ErrorDefinitivo("Falta remoteId para eliminar producto ${producto.id}")
        val respuesta = api.eliminar(
            remoteId = remoteId,
            clientUuid = op.uuidOperacion,
            version = producto.remoteVersion
        )
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        return Envio.Exito
    }

    private suspend fun enviarMovimiento(op: OperacionPendienteEntity): Envio {
        val api = inventarioApi ?: return Envio.Reintentable("InventarioApi no disponible")
        val json = JSONObject(op.payload)
        val productoLocalId = json.getLong("productoIdLocal")
        val tipo = json.getString("tipo")
        val cantidad = json.getInt("cantidad")
        val remoteProducto = productoDao.obtenerPorId(productoLocalId)?.remoteId
        if (remoteProducto.isNullOrBlank()) {
            return Envio.Reintentable(
                "Falta remoteId del producto $productoLocalId para movimiento."
            )
        }
        val body = MovimientoInventarioRequest(
            uuidOperacion = op.uuidOperacion,
            productoId = remoteProducto,
            tipo = tipo,
            cantidad = cantidad,
            pedidoUuid = null
        )
        val respuesta = api.crearMovimiento(body)
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        val mov = movimientoDao.obtenerPorUuid(op.uuidOperacion)
        respuesta.body()?.let { cuerpo ->
            if (mov != null) {
                movimientoDao.actualizar(
                    mov.copy(
                        remoteId = cuerpo.id,
                        remoteVersion = cuerpo.version
                    )
                )
            }
        }
        return Envio.Exito
    }

    private suspend fun cargarProducto(op: OperacionPendienteEntity): ProductoEntity? {
        val json = JSONObject(op.payload)
        val id = json.optLong("productoIdLocal", op.entidadIdLocal)
        return productoDao.obtenerPorId(id)
    }

    private fun construirRequest(
        producto: ProductoEntity,
        categoriaRemota: String,
        clientUuid: String,
        incluirStock: Boolean
    ): GuardarProductoRequest {
        val descuento = if (producto.esOferta) producto.descuentoPorcentaje else 0
        return GuardarProductoRequest(
            clientUuid = clientUuid,
            nombre = producto.nombre,
            marca = producto.marca,
            descripcion = producto.descripcion,
            categoriaId = categoriaRemota,
            precioCentimos = producto.precioCentimos,
            descuentoPorcentaje = descuento.coerceIn(0, 100),
            esOferta = producto.esOferta && PrecioDescuento.esOfertaActiva(producto.esOferta, descuento),
            imagenKey = producto.imagenKey,
            activo = producto.activo,
            stock = if (incluirStock) producto.stock else null,
            version = producto.remoteVersion
        )
    }

    private fun clasificarHttp(code: Int, message: String?): Envio = when (code) {
        408, 429 -> Envio.Reintentable("HTTP $code: ${message.orEmpty()}")
        in 500..599 -> Envio.Reintentable("HTTP $code servidor: ${message.orEmpty()}")
        401 -> Envio.ErrorDefinitivo("HTTP 401: sesión inválida / auth necesaria")
        403 -> Envio.ErrorDefinitivo("HTTP 403: permiso denegado")
        404 -> Envio.ErrorDefinitivo("HTTP 404: recurso no encontrado")
        409 -> Envio.ErrorDefinitivo("HTTP 409 conflicto: ${message.orEmpty()}")
        in 400..499 -> Envio.ErrorDefinitivo("HTTP $code: ${message.orEmpty()}")
        else -> Envio.Reintentable("HTTP $code: ${message.orEmpty()}")
    }

    private suspend fun registrarIntento(ahora: Long, error: String?) {
        val actual = syncMetadataDao.obtener()
        syncMetadataDao.guardar(
            SyncMetadataEntity(
                id = SyncMetadataEntity.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = ahora,
                ultimoErrorGeneral = error ?: actual?.ultimoErrorGeneral
            )
        )
    }

    private sealed class Envio {
        data object Exito : Envio()
        data class Reintentable(val mensaje: String) : Envio()
        data class ErrorDefinitivo(val mensaje: String) : Envio()
    }
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
