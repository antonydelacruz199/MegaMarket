package com.megamarket.app.data.repository

import android.content.ContentResolver
import androidx.room.withTransaction
import com.megamarket.app.data.local.AppDatabase
import com.megamarket.app.data.local.dao.MovimientoInventarioDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.MovimientoInventario
import com.megamarket.app.data.local.OperacionPendiente
import com.megamarket.app.data.mapper.toEntity
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.ClavesImagen
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.Producto
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ProductoRepository(
    private val database: AppDatabase,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoInventarioDao,
    private val operacionDao: OperacionPendienteDao,
    private val resolver: ContentResolver,
    private val programarSincronizacion: () -> Unit
) {
    suspend fun buscarDuplicado(nombre: String, excluirId: Long = 0): Producto? =
        withContext(Dispatchers.IO) { buscarDuplicadoEnCurso(nombre, excluirId) }

    /** Sin cambiar de dispatcher: Room rechaza otro contexto dentro de withTransaction. */
    private suspend fun buscarCodigoEnCurso(codigo: String?, excluirId: Long): Producto? {
        val normalizado = codigo?.trim().orEmpty()
        if (normalizado.isEmpty()) return null
        return productoDao.obtenerPorCodigoBarras(normalizado, excluirId)?.toModel()
    }

    private suspend fun buscarDuplicadoEnCurso(nombre: String, excluirId: Long): Producto? {
        val normalizado = nombre.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
        return productoDao.obtenerTodos().firstOrNull {
            it.id != excluirId &&
                it.remoteDeletedAt == null &&
                it.nombre.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) == normalizado
        }?.toModel()
    }

    suspend fun obtenerTodos(): List<Producto> = withContext(Dispatchers.IO) {
        productoDao.eliminarSinDatos()
        productoDao.obtenerTodos().filter { it.remoteDeletedAt==null }.map { it.toModel() }
    }

    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        productoDao.eliminarSinDatos()
        productoDao.obtenerActivos().map { it.toModel() }
    }

    suspend fun ultimoErrorProducto(id: Long): String? = withContext(Dispatchers.IO) {
        val crear = operacionDao.obtenerPendienteDe(
            TipoOperacionPendiente.PRODUCTO,
            id,
            TipoOperacionPendiente.CREAR_PRODUCTO
        )
        val actualizar = operacionDao.obtenerPendienteDe(
            TipoOperacionPendiente.PRODUCTO,
            id,
            TipoOperacionPendiente.ACTUALIZAR_PRODUCTO
        )
        crear?.ultimoError ?: actualizar?.ultimoError
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        productoDao.obtenerPorId(id)?.toModel()?.takeIf { it.nombre.isNotBlank() }
    }

    suspend fun descartarDuplicadoLocal(id: Long) = database.withTransaction {
        val p=productoDao.obtenerPorId(id) ?: return@withTransaction
        if(!p.remoteId.isNullOrBlank()) return@withTransaction
        val op=operacionDao.obtenerPendienteDe(TipoOperacionPendiente.PRODUCTO,id,TipoOperacionPendiente.CREAR_PRODUCTO)
        require(op?.estado!=EstadoSincronizacion.ENVIANDO) { "Espera a que termine la sincronización" }
        operacionDao.eliminarActivasDe(TipoOperacionPendiente.PRODUCTO,id,TipoOperacionPendiente.CREAR_PRODUCTO)
        productoDao.actualizarProducto(p.copy(activo=false,remoteDeletedAt=System.currentTimeMillis().toString()))
    }

    suspend fun crearProducto(producto: Producto): Long = withContext(Dispatchers.IO) {
        require(producto.stock >= 0) { "El stock no puede ser negativo" }
        val id = database.withTransaction {
            val ahora = System.currentTimeMillis()
            require(buscarDuplicadoEnCurso(producto.nombre, producto.id) == null) {
                "Ya existe un producto con ese nombre. Abre el producto existente para actualizarlo."
            }
            require(buscarCodigoEnCurso(producto.codigoBarras, producto.id) == null) {
                "Ya existe un producto con ese código de barras"
            }
            val entidad = producto.toEntity()
            val nuevoId = productoDao.insertar(entidad.copy(id = 0))
            val uuidOperacion = UUID.randomUUID().toString()
            operacionDao.insertar(
                OperacionPendiente(
                    uuidOperacion = uuidOperacion,
                    tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                    entidadIdLocal = nuevoId,
                    operacion = TipoOperacionPendiente.CREAR_PRODUCTO,
                    payload = ProductoSyncPayload.referenciaProducto(nuevoId),
                    estado = EstadoSincronizacion.PENDIENTE,
                    fechaCreacion = ahora,
                    fechaActualizacion = ahora
                )
            )
            nuevoId
        }
        notificarCambio(id)
        programarSincronizacion()
        id
    }

    suspend fun actualizarProducto(producto: Producto): Boolean = withContext(Dispatchers.IO) {
        require(producto.stock >= 0) { "El stock no puede ser negativo" }
        val anterior = productoDao.obtenerPorId(producto.id) ?: return@withContext false
        val aplicado = database.withTransaction {
            val ahora = System.currentTimeMillis()
            val entidad = producto.toEntity()
            require(buscarDuplicadoEnCurso(producto.nombre, producto.id) == null) {
                "Ya existe un producto con ese nombre"
            }
            require(buscarCodigoEnCurso(producto.codigoBarras, producto.id) == null) {
                "Ya existe un producto con ese código de barras"
            }
            val filas = productoDao.actualizarProducto(entidad)
            if (filas <= 0) return@withTransaction false

            if (producto.remoteId.isNullOrBlank()) {
                val pendienteCrear = operacionDao.obtenerPendienteDe(
                    TipoOperacionPendiente.PRODUCTO,
                    producto.id,
                    TipoOperacionPendiente.CREAR_PRODUCTO
                )
                if (pendienteCrear != null) {
                    operacionDao.actualizarOperacionPendiente(
                        pendienteCrear.copy(
                            payload = ProductoSyncPayload.referenciaProducto(producto.id),
                            estado = EstadoSincronizacion.PENDIENTE,
                            ultimoError = null,
                            fechaActualizacion = ahora
                        )
                    )
                } else {
                    encolarCrearProducto(producto.id, ahora)
                }
            } else {
                encolarActualizarProducto(producto.id, ahora)
                ajustarStock(anterior.stock, producto.stock, producto.id, ahora)
            }
            true
        }
        if (aplicado) {
            notificarCambio(producto.id)
            programarSincronizacion()
        }
        aplicado
    }

    /**
     * Guarda la clave que devolvió POST /api/medios. No encola otra sincronización
     * y no borra la imagen anterior del servidor.
     */
    suspend fun registrarImagenRemota(
        idLocal: Long,
        imagenKey: String,
        version: Long?,
        updatedAt: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val actual = productoDao.obtenerPorId(idLocal) ?: return@withContext false
        if (!ClavesImagen.perteneceAProducto(imagenKey, actual.remoteId)) return@withContext false
        val versionLocal = actual.remoteVersion
        val versionNueva = when {
            version == null -> versionLocal
            versionLocal == null || version > versionLocal -> version
            else -> versionLocal
        }
        val filas = productoDao.actualizarProducto(
            actual.copy(
                imagenKey = imagenKey,
                remoteVersion = versionNueva,
                remoteUpdatedAt = updatedAt ?: actual.remoteUpdatedAt
            )
        )
        if (filas > 0) notificarCambio(idLocal)
        filas > 0
    }

    suspend fun eliminarLogico(id: Long): Boolean = withContext(Dispatchers.IO) {
        val actual = productoDao.obtenerPorId(id) ?: return@withContext false
        val aplicado = database.withTransaction {
            val ahora = System.currentTimeMillis()
            val filas = productoDao.actualizarProducto(actual.copy(activo = false))
            if (filas <= 0) return@withTransaction false

            if (actual.remoteId.isNullOrBlank()) {
                operacionDao.eliminarActivasDe(
                    TipoOperacionPendiente.PRODUCTO,
                    id,
                    TipoOperacionPendiente.CREAR_PRODUCTO
                )
            } else {
                val uuidOperacion = UUID.randomUUID().toString()
                operacionDao.insertar(
                    OperacionPendiente(
                        uuidOperacion = uuidOperacion,
                        tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                        entidadIdLocal = id,
                        operacion = TipoOperacionPendiente.ELIMINAR_PRODUCTO,
                        payload = ProductoSyncPayload.referenciaProducto(id),
                        estado = EstadoSincronizacion.PENDIENTE,
                        fechaCreacion = ahora,
                        fechaActualizacion = ahora
                    )
                )
            }
            true
        }
        if (aplicado) {
            notificarCambio(id)
            programarSincronizacion()
        }
        aplicado
    }

    private suspend fun encolarCrearProducto(productoId: Long, ahora: Long) {
        val uuidOperacion = UUID.randomUUID().toString()
        operacionDao.insertar(
            OperacionPendiente(
                uuidOperacion = uuidOperacion,
                tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                entidadIdLocal = productoId,
                operacion = TipoOperacionPendiente.CREAR_PRODUCTO,
                payload = ProductoSyncPayload.referenciaProducto(productoId),
                estado = EstadoSincronizacion.PENDIENTE,
                fechaCreacion = ahora,
                fechaActualizacion = ahora
            )
        )
    }

    private suspend fun encolarActualizarProducto(productoId: Long, ahora: Long) {
        val existente = operacionDao.obtenerPendienteDe(
            TipoOperacionPendiente.PRODUCTO,
            productoId,
            TipoOperacionPendiente.ACTUALIZAR_PRODUCTO
        )
        if (existente != null) {
            operacionDao.actualizarOperacionPendiente(
                existente.copy(
                    estado = EstadoSincronizacion.PENDIENTE,
                    ultimoError = null,
                    fechaActualizacion = ahora
                )
            )
        } else {
            val uuidOperacion = UUID.randomUUID().toString()
            operacionDao.insertar(
                OperacionPendiente(
                    uuidOperacion = uuidOperacion,
                    tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                    entidadIdLocal = productoId,
                    operacion = TipoOperacionPendiente.ACTUALIZAR_PRODUCTO,
                    payload = ProductoSyncPayload.referenciaProducto(productoId),
                    estado = EstadoSincronizacion.PENDIENTE,
                    fechaCreacion = ahora,
                    fechaActualizacion = ahora
                )
            )
        }
    }

    private suspend fun ajustarStock(
        stockAnterior: Int,
        stockNuevo: Int,
        productoId: Long,
        ahora: Long
    ) {
        val ajuste = ProductoInventarioLocal.calcularAjuste(stockAnterior, stockNuevo) ?: return
        val uuidMovimiento = UUID.randomUUID().toString()
        movimientoDao.insertar(
            MovimientoInventario(
                uuidOperacion = uuidMovimiento,
                productoId = productoId,
                tipo = ajuste.tipo,
                cantidad = ajuste.cantidad,
                fechaCreacion = ahora
            )
        )
        operacionDao.insertar(
            OperacionPendiente(
                uuidOperacion = uuidMovimiento,
                tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                entidadIdLocal = productoId,
                operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
                payload = ProductoSyncPayload.payloadMovimientoInventario(productoId, ajuste.tipo, ajuste.cantidad),
                estado = EstadoSincronizacion.PENDIENTE,
                fechaCreacion = ahora,
                fechaActualizacion = ahora
            )
        )
    }

    private fun notificarCambio(id: Long) {
        resolver.notifyChange(ContratoCatalogo.URI_PRODUCTOS, null)
        resolver.notifyChange(ContratoCatalogo.uriProducto(id), null)
    }

    companion object {
        fun crearProgramador(programar: () -> Unit): () -> Unit = programar
    }
}
