package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.remote.api.StockApi
import com.megamarket.cliente.data.remote.dto.ActualizarStockRequest
import com.megamarket.cliente.data.remote.dto.ActualizarStockResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncRepositoryTest {

    @Test
    fun resuelveUuidDesdeRoom() = runBlocking {
        val productoDao = FakeProductoDao(
            ProductoEntity(
                id = 15,
                remoteId = "550e8400-e29b-41d4-a716-446655440000",
                nombre = "Arroz",
                marca = "X",
                descripcion = "",
                categoriaId = 1,
                precioCentimos = 100,
                precioOfertaCentimos = null,
                stock = 7,
                imagenKey = "",
                esOferta = false,
                activo = true
            )
        )
        val ops = FakeOperacionDao(
            OperacionPendienteEntity(
                id = 1,
                tipoEntidad = OperacionPendienteEntity.TIPO_PRODUCTO,
                entidadIdLocal = 15,
                operacion = OperacionPendienteEntity.OPERACION_ACTUALIZAR_STOCK,
                payload = """{"productoIdLocal":15,"stock":7}""",
                fechaCreacion = 1L
            )
        )
        var uuidUsado: String? = null
        val api = object : StockApi {
            override suspend fun actualizarStock(
                uuidProducto: String,
                body: ActualizarStockRequest
            ): Response<ActualizarStockResponse> {
                uuidUsado = uuidProducto
                return Response.success(
                    ActualizarStockResponse(id = uuidProducto, stock = body.stock)
                )
            }
        }
        val sync = SyncRepository(ops, productoDao, baseUrlApi = "https://api.test/", stockApiOverride = api)
        val resultado = sync.sincronizarPendientes()
        assertEquals(ResultadoSincronizacion.Exito, resultado)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuidUsado)
        assertTrue(ops.eliminadas.contains(1L))
    }

    @Test
    fun remoteIdNull_mantieneOperacionPendiente() = runBlocking {
        val productoDao = FakeProductoDao(
            ProductoEntity(
                id = 15,
                remoteId = null,
                nombre = "Arroz",
                marca = "X",
                descripcion = "",
                categoriaId = 1,
                precioCentimos = 100,
                precioOfertaCentimos = null,
                stock = 7,
                imagenKey = "",
                esOferta = false,
                activo = true
            )
        )
        val ops = FakeOperacionDao(
            OperacionPendienteEntity(
                id = 2,
                tipoEntidad = OperacionPendienteEntity.TIPO_PRODUCTO,
                entidadIdLocal = 15,
                operacion = OperacionPendienteEntity.OPERACION_ACTUALIZAR_STOCK,
                payload = """{"productoIdLocal":15,"stock":7}""",
                fechaCreacion = 1L
            )
        )
        val api = object : StockApi {
            override suspend fun actualizarStock(
                uuidProducto: String,
                body: ActualizarStockRequest
            ): Response<ActualizarStockResponse> {
                error("no debe llamarse")
            }
        }
        val sync = SyncRepository(ops, productoDao, baseUrlApi = "https://api.test/", stockApiOverride = api)
        val resultado = sync.sincronizarPendientes()
        assertTrue(resultado is ResultadoSincronizacion.Reintentar)
        assertTrue(ops.eliminadas.isEmpty())
        assertEquals(OperacionPendienteEntity.ESTADO_ERROR, ops.porId(2)?.estado)
    }

    private class FakeProductoDao(private var producto: ProductoEntity?) : ProductoDao {
        override fun observarActivos() = flowOf(listOfNotNull(producto))
        override suspend fun obtenerActivos() = listOfNotNull(producto)
        override suspend fun contar() = if (producto == null) 0 else 1
        override suspend fun maxId() = producto?.id ?: 0
        override suspend fun obtenerPorId(id: Long) = producto?.takeIf { it.id == id }
        override fun observarPorId(id: Long): Flow<ProductoEntity?> =
            flowOf(producto?.takeIf { it.id == id })
        override suspend fun obtenerPorRemoteId(remoteId: String) =
            producto?.takeIf { it.remoteId == remoteId }
        override suspend fun upsert(entidad: ProductoEntity) {
            producto = entidad
        }
        override suspend fun upsertVarios(entidades: List<ProductoEntity>) {
            producto = entidades.firstOrNull()
        }
        override suspend fun actualizar(entidad: ProductoEntity) = 1
        override suspend fun actualizarDesdeCatalogo(
            id: Long,
            stock: Int,
            remoteId: String?,
            remoteVersion: Long?,
            remoteUpdatedAt: String?,
            remoteDeletedAt: String?
        ) = 1
    }

    private class FakeOperacionDao(
        inicial: OperacionPendienteEntity
    ) : OperacionPendienteDao {
        private val mapa = mutableMapOf(inicial.id to inicial)
        val eliminadas = mutableListOf<Long>()

        fun porId(id: Long) = mapa[id]

        override suspend fun insertar(entidad: OperacionPendienteEntity): Long {
            mapa[entidad.id] = entidad
            return entidad.id
        }

        override suspend fun actualizar(entidad: OperacionPendienteEntity): Int {
            mapa[entidad.id] = entidad
            return 1
        }

        override suspend fun obtenerPendientes() =
            mapa.values.filter {
                it.estado == OperacionPendienteEntity.ESTADO_PENDIENTE ||
                    it.estado == OperacionPendienteEntity.ESTADO_ERROR
            }

        override suspend fun obtenerPendienteDe(
            tipoEntidad: String,
            entidadIdLocal: Long,
            operacion: String
        ) = mapa.values.firstOrNull {
            it.tipoEntidad == tipoEntidad &&
                it.entidadIdLocal == entidadIdLocal &&
                it.operacion == operacion
        }

        override suspend fun recuperarSincronizandoAtascadas(): Int {
            var n = 0
            mapa.replaceAll { _, v ->
                if (v.estado == OperacionPendienteEntity.ESTADO_SINCRONIZANDO) {
                    n++
                    v.copy(estado = OperacionPendienteEntity.ESTADO_PENDIENTE)
                } else v
            }
            return n
        }

        override suspend fun marcarSincronizando(id: Long): Int {
            mapa[id] = mapa.getValue(id).copy(estado = OperacionPendienteEntity.ESTADO_SINCRONIZANDO)
            return 1
        }

        override suspend fun marcarError(id: Long, error: String): Int {
            val actual = mapa.getValue(id)
            mapa[id] = actual.copy(
                estado = OperacionPendienteEntity.ESTADO_ERROR,
                ultimoError = error,
                intentos = actual.intentos + 1
            )
            return 1
        }

        override suspend fun eliminar(id: Long): Int {
            mapa.remove(id)
            eliminadas += id
            return 1
        }

        override suspend fun incrementarIntentos(id: Long): Int = 1
    }
}
