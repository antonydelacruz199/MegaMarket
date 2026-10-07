package com.megamarket.app.data.repository

import android.content.ContentResolver
import androidx.room.withTransaction
import com.megamarket.app.data.local.AppDatabase
import com.megamarket.app.data.local.dao.MovimientoInventarioDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.entities.MovimientoInventarioEntity
import com.megamarket.app.data.local.entities.OperacionPendienteEntity
import com.megamarket.app.data.mapper.toEntity
import com.megamarket.app.data.mapper.toModel
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
    private val programarSync: () -> Unit
) {
    suspend fun obtenerTodos(): List<Producto> = withContext(Dispatchers.IO) {
        productoDao.eliminarSinDatos()
        productoDao.obtenerTodos().map { it.toModel() }
    }

    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        productoDao.eliminarSinDatos()
        productoDao.obtenerActivos().map { it.toModel() }
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        productoDao.obtenerPorId(id)?.toModel()?.takeIf { it.nombre.isNotBlank() }
    }

    suspend fun insertar(producto: Producto): Long = withContext(Dispatchers.IO) {
        require(producto.stock >= 0) { "El stock no puede ser negativo" }
        val id = database.withTransaction {
            val ahora = System.currentTimeMillis()
            val entidad = producto.toEntity()
            val nuevoId = productoDao.insertar(entidad.copy(id = 0))
            val uuidOperacion = UUID.randomUUID().toString()
            operacionDao.insertar(
                OperacionPendienteEntity(
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
        programarSync()
        id
    }

    suspend fun actualizar(producto: Producto): Boolean = withContext(Dispatchers.IO) {
        require(producto.stock >= 0) { "El stock no puede ser negativo" }
        val anterior = productoDao.obtenerPorId(producto.id) ?: return@withContext false
        val aplicado = database.withTransaction {
            val ahora = System.currentTimeMillis()
            val entidad = producto.toEntity()
            val filas = productoDao.actualizar(entidad)
            if (filas <= 0) return@withTransaction false

            if (producto.remoteId.isNullOrBlank()) {
                val pendienteCrear = operacionDao.obtenerPendienteDe(
                    TipoOperacionPendiente.PRODUCTO,
                    producto.id,
                    TipoOperacionPendiente.CREAR_PRODUCTO
                )
                if (pendienteCrear != null) {
                    operacionDao.actualizar(
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
                registrarAjusteStock(anterior.stock, producto.stock, producto.id, ahora)
            }
            true
        }
        if (aplicado) {
            notificarCambio(producto.id)
            programarSync()
        }
        aplicado
    }

    suspend fun eliminarLogico(id: Long): Boolean = withContext(Dispatchers.IO) {
        val actual = productoDao.obtenerPorId(id) ?: return@withContext false
        val aplicado = database.withTransaction {
            val ahora = System.currentTimeMillis()
            val filas = productoDao.actualizar(actual.copy(activo = false))
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
                    OperacionPendienteEntity(
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
            programarSync()
        }
        aplicado
    }

    private suspend fun encolarCrearProducto(productoId: Long, ahora: Long) {
        val uuidOperacion = UUID.randomUUID().toString()
        operacionDao.insertar(
            OperacionPendienteEntity(
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
            operacionDao.actualizar(
                existente.copy(
                    estado = EstadoSincronizacion.PENDIENTE,
                    ultimoError = null,
                    fechaActualizacion = ahora
                )
            )
        } else {
            val uuidOperacion = UUID.randomUUID().toString()
            operacionDao.insertar(
                OperacionPendienteEntity(
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

    private suspend fun registrarAjusteStock(
        stockAnterior: Int,
        stockNuevo: Int,
        productoId: Long,
        ahora: Long
    ) {
        val ajuste = ProductoInventarioLocal.calcularAjuste(stockAnterior, stockNuevo) ?: return
        val uuidMov = UUID.randomUUID().toString()
        movimientoDao.insertar(
            MovimientoInventarioEntity(
                uuidOperacion = uuidMov,
                productoId = productoId,
                tipo = ajuste.tipo,
                cantidad = ajuste.cantidad,
                fechaCreacion = ahora
            )
        )
        operacionDao.insertar(
            OperacionPendienteEntity(
                uuidOperacion = uuidMov,
                tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                entidadIdLocal = productoId,
                operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
                payload = ProductoSyncPayload.movimiento(productoId, ajuste.tipo, ajuste.cantidad),
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
