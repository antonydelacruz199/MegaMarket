package com.megamarket.app.data.repository

import com.megamarket.app.data.local.SyncMetadata
import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.dao.SyncMetadataDao
import com.megamarket.app.data.local.OperacionPendiente
import com.megamarket.app.data.mapper.aActualizarProductoRequest
import com.megamarket.app.data.mapper.aCrearProductoRequest
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.EliminarProductoRequest
import com.megamarket.app.data.remote.ErrorApiDto
import com.megamarket.app.data.remote.MovimientoInventarioRequest
import com.megamarket.app.data.remote.MovimientoInventarioResponse
import com.megamarket.app.data.remote.ProductoResponse
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

class SincronizacionRepository(
    private val api: ApiService,
    private val operacionDao: OperacionPendienteDao,
    private val productoDao: ProductoDao,
    private val categoriaDao: CategoriaDao,
    private val metadatosSincronizacionDao: SyncMetadataDao,
    private val catalogoRemoto: CatalogoRemotoRepository,
    private val conectado: Flow<Boolean> = flowOf(false)
) {
    private val gson = Gson()
    private val mutex = Mutex()

    data class ResumenSincronizacion(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?,
        val conectado: Boolean = false
    )

    fun observarResumen(): Flow<ResumenSincronizacion> = combine(
        operacionDao.observarPendientesActivos(),
        operacionDao.observarErrores(),
        metadatosSincronizacionDao.observarMetadatos(),
        conectado
    ) { pendientes, errores, meta, hayInternet ->
        ResumenSincronizacion(
            pendientes = pendientes,
            errores = errores,
            ultimaExitosa = meta?.ultimaSincronizacionExitosa,
            ultimoError = meta?.ultimoErrorGeneral,
            conectado = hayInternet
        )
    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = mutex.withLock { withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        registrarIntentoDeSincronizacion(ahora)

        val pendientes = operacionDao.obtenerOperacionesPorSincronizar()
        if (pendientes.isEmpty()) {
            val descarga=catalogoRemoto.descargarYGuardarCatalogo()
            return@withContext if(descarga is ResultadoDescargaCatalogo.Error) ResultadoSincronizacion.Reintentar(descarga.mensaje) else ResultadoSincronizacion.SinTrabajo
        }

        var huboExito = false
        var reintentar: String? = null

        for (op in pendientes) {
            operacionDao.marcarOperacionEnviando(op.id, System.currentTimeMillis())
            when (val resultado = enviarOperacion(op)) {
                is ResultadoEnvio.Ok -> {
                    operacionDao.marcarOperacionSincronizada(op.id, System.currentTimeMillis())
                    huboExito = true
                }
                is ResultadoEnvio.ErrorDefinitivo -> {
                    operacionDao.marcarOperacionError(
                        op.id,
                        resultado.mensaje,
                        System.currentTimeMillis()
                    )
                    guardarErrorGeneral(resultado.mensaje)
                }
                is ResultadoEnvio.Reintentar -> {
                    operacionDao.actualizarOperacionPendiente(
                        op.copy(
                            estado = EstadoSincronizacion.PENDIENTE,
                            ultimoError = resultado.mensaje,
                            fechaActualizacion = System.currentTimeMillis()
                        )
                    )
                    reintentar = resultado.mensaje
                    break
                }
            }
        }

        val descarga=catalogoRemoto.descargarYGuardarCatalogo()
        if(descarga is ResultadoDescargaCatalogo.Error) return@withContext ResultadoSincronizacion.Reintentar(descarga.mensaje)

        when {
            reintentar != null -> ResultadoSincronizacion.Reintentar(reintentar)
            huboExito -> {
                marcarExito(System.currentTimeMillis())
                ResultadoSincronizacion.Exito
            }
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }

    }

    private suspend fun enviarOperacion(op: OperacionPendiente): ResultadoEnvio {
        return try {
            when (op.operacion) {
                TipoOperacionPendiente.CREAR_PRODUCTO -> enviarCrearProducto(op)
                TipoOperacionPendiente.ACTUALIZAR_PRODUCTO -> enviarActualizarProducto(op)
                TipoOperacionPendiente.ELIMINAR_PRODUCTO -> enviarEliminarProducto(op)
                TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO -> enviarMovimiento(op)
                else -> ResultadoEnvio.ErrorDefinitivo("Operación no soportada: ${op.operacion}")
            }
        } catch (error: IOException) {
            ResultadoEnvio.Reintentar(error.message ?: "Error de red")
        } catch (error: Exception) {
            ResultadoEnvio.Reintentar(error.message ?: "Error inesperado")
        }
    }

    private suspend fun enviarCrearProducto(op: OperacionPendiente): ResultadoEnvio {
        val producto = productoDao.obtenerPorId(op.entidadIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto local no encontrado")
        val categoria = categoriaDao.obtenerPorId(producto.categoriaId)
        val categoriaRemota = categoria?.remoteId
            ?: return ResultadoEnvio.ErrorDefinitivo("La categoría aún no tiene remoteId")
        val respuesta = api.crearProducto(
            producto.aCrearProductoRequest(categoriaRemota, op.uuidOperacion)
        )
        return cuandoRespuestaProducto(respuesta) { cuerpo ->
            val remoto = cuerpo.producto
            productoDao.actualizarProducto(
                producto.copy(
                    remoteId = remoto.id,
                    remoteVersion = remoto.version,
                    remoteUpdatedAt = remoto.updatedAt,
                    activo = remoto.activo,
                    stock = remoto.stock
                )
            )
            ResultadoEnvio.Ok
        }
    }

    private suspend fun enviarActualizarProducto(op: OperacionPendiente): ResultadoEnvio {
        val producto = productoDao.obtenerPorId(op.entidadIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto local no encontrado")
        val remoteId = producto.remoteId
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto sin remoteId para actualizar")
        val categoria = categoriaDao.obtenerPorId(producto.categoriaId)
        val categoriaRemota = categoria?.remoteId
            ?: return ResultadoEnvio.ErrorDefinitivo("La categoría aún no tiene remoteId")
        val respuesta = api.actualizarProducto(
            remoteId,
            producto.aActualizarProductoRequest(categoriaRemota, op.uuidOperacion)
        )
        return cuandoRespuestaProducto(respuesta) { cuerpo ->
            val remoto = cuerpo.producto
            productoDao.actualizarProducto(
                producto.copy(
                    remoteVersion = remoto.version,
                    remoteUpdatedAt = remoto.updatedAt,
                    activo = remoto.activo,
                    nombre = remoto.nombre,
                    marca = remoto.marca,
                    descripcion = remoto.descripcion.orEmpty()
                )
            )
            ResultadoEnvio.Ok
        }
    }

    private suspend fun enviarEliminarProducto(op: OperacionPendiente): ResultadoEnvio {
        val producto = productoDao.obtenerPorId(op.entidadIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto local no encontrado")
        val remoteId = producto.remoteId
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto sin remoteId para eliminar")
        val respuesta = api.eliminarProducto(
            remoteId,
            EliminarProductoRequest(
                version = producto.remoteVersion,
                uuidOperacion = op.uuidOperacion
            )
        )
        return clasificar(respuesta) {
            productoDao.actualizarProducto(
                producto.copy(
                    activo = false,
                    remoteDeletedAt = producto.remoteDeletedAt
                        ?: System.currentTimeMillis().toString()
                )
            )
            ResultadoEnvio.Ok
        }
    }

    private suspend fun enviarMovimiento(op: OperacionPendiente): ResultadoEnvio {
        val payload = JSONObject(op.payload)
        val productoIdLocal = payload.optLong("productoIdLocal", op.entidadIdLocal)
        val tipo = payload.optString("tipo")
        val cantidad = payload.optInt("cantidad")
        val producto = productoDao.obtenerPorId(productoIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto local no encontrado para movimiento")
        val remoteId = producto.remoteId
            ?: return ResultadoEnvio.ErrorDefinitivo("Producto sin remoteId para movimiento")

        val respuesta = api.registrarMovimiento(
            MovimientoInventarioRequest(
                uuidOperacion = op.uuidOperacion,
                productoId = remoteId,
                tipo = tipo,
                cantidad = cantidad
            )
        )
        return clasificar<MovimientoInventarioResponse>(respuesta) {
            val version = respuesta.body()?.producto?.version
            if (version != null) {
                productoDao.actualizarProducto(producto.copy(remoteVersion = version))
            }
            ResultadoEnvio.Ok
        }
    }

    private suspend fun cuandoRespuestaProducto(
        respuesta: Response<ProductoResponse>,
        onOk: suspend (ProductoResponse) -> ResultadoEnvio
    ): ResultadoEnvio {
        return clasificar(respuesta) {
            val cuerpo = respuesta.body()
                ?: return@clasificar ResultadoEnvio.ErrorDefinitivo("Respuesta vacía")
            onOk(cuerpo)
        }
    }

    private suspend fun <T> clasificar(
        respuesta: Response<T>,
        onOk: suspend () -> ResultadoEnvio
    ): ResultadoEnvio {
        val codigo = respuesta.code()
        if (respuesta.isSuccessful) return onOk()
        val mensaje = mensajeError(respuesta) ?: "Error HTTP $codigo"
        return when (codigo) {
            408, 429 -> ResultadoEnvio.Reintentar(mensaje)
            in 500..599 -> ResultadoEnvio.Reintentar(mensaje)
            409 -> ResultadoEnvio.ErrorDefinitivo("Conflicto: $mensaje")
            401 -> ResultadoEnvio.ErrorDefinitivo("Sesión expirada. Inicia sesión de nuevo.")
            403, 404, 400 -> ResultadoEnvio.ErrorDefinitivo(mensaje)
            else -> ResultadoEnvio.ErrorDefinitivo(mensaje)
        }
    }

    private fun <T> mensajeError(respuesta: Response<T>): String? {
        val raw = respuesta.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            gson.fromJson(raw, ErrorApiDto::class.java)?.error ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private suspend fun registrarIntentoDeSincronizacion(ahora: Long) {
        val actual = metadatosSincronizacionDao.obtener()
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = ahora,
                ultimoErrorGeneral = actual?.ultimoErrorGeneral
            )
        )
    }

    private suspend fun marcarExito(ahora: Long) {
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = ahora,
                ultimoIntento = ahora,
                ultimoErrorGeneral = null
            )
        )
    }

    private suspend fun guardarErrorGeneral(mensaje: String) {
        val actual = metadatosSincronizacionDao.obtener()
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = System.currentTimeMillis(),
                ultimoErrorGeneral = mensaje
            )
        )
    }

    private sealed class ResultadoEnvio {
        data object Ok : ResultadoEnvio()
        data class ErrorDefinitivo(val mensaje: String) : ResultadoEnvio()
        data class Reintentar(val mensaje: String) : ResultadoEnvio()
    }
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
