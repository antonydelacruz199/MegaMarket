package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.MovimientoInventarioEntity
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.local.entities.SyncMetadataEntity
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryTest {

    @Test
    fun sinApi_reintentaYNoMarcaExito() = runBlocking {
        val ops = FakeOperacionDao(
            OperacionPendienteEntity(
                id = 1,
                uuidOperacion = "uuid-mov-1",
                tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                entidadIdLocal = 15,
                operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
                payload = """{"productoIdLocal":15,"tipo":"SALIDA_VENTA","cantidad":3}""",
                fechaCreacion = 1L
            )
        )
        val sync = SyncRepository(
            operacionDao = ops,
            productoDao = FakeProductoDao(
                ProductoEntity(
                    id = 15,
                    providerId = 7,
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
            ),
            pedidoDao = FakePedidoDao(),
            movimientoDao = FakeMovimientoDao(),
            syncMetadataDao = FakeSyncMetaDao(),
            baseUrlApi = ""
        )
        val r = sync.sincronizarPendientes()
        assertTrue(r is ResultadoSincronizacion.Reintentar)
        assertEquals(EstadoSincronizacion.PENDIENTE, ops.porId(1)?.estado)
    }

    @Test
    fun legacyActualizarStock_quedaError() = runBlocking {
        val ops = FakeOperacionDao(
            OperacionPendienteEntity(
                id = 2,
                uuidOperacion = "legacy-1",
                tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                entidadIdLocal = 1,
                operacion = TipoOperacionPendiente.ACTUALIZAR_STOCK,
                payload = """{"stock":7}""",
                fechaCreacion = 1L
            )
        )
        val sync = SyncRepository(
            operacionDao = ops,
            productoDao = FakeProductoDao(null),
            pedidoDao = FakePedidoDao(),
            movimientoDao = FakeMovimientoDao(),
            syncMetadataDao = FakeSyncMetaDao(),
            baseUrlApi = "https://api.test/"
        )
        sync.sincronizarPendientes()
        assertEquals(EstadoSincronizacion.ERROR, ops.porId(2)?.estado)
        assertTrue(ops.porId(2)?.ultimoError?.contains("legacy") == true)
    }

    @Test
    fun uuidOperacionSeConservaEnEntidad() {
        val uuid = "mismo-uuid-en-retry"
        val op = OperacionPendienteEntity(
            uuidOperacion = uuid,
            tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
            entidadIdLocal = 1,
            operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
            payload = "{}",
            fechaCreacion = 1L
        )
        assertEquals(uuid, op.uuidOperacion)
        assertEquals(EstadoSincronizacion.PENDIENTE, op.estado)
    }

    private class FakeProductoDao(private var producto: ProductoEntity?) : ProductoDao {
        override fun observarActivos() = flowOf(listOfNotNull(producto))
        override suspend fun obtenerActivos() = listOfNotNull(producto)
        override suspend fun contar() = if (producto == null) 0 else 1
        override suspend fun obtenerPorId(id: Long) = producto?.takeIf { it.id == id }
        override fun observarPorId(id: Long) = flowOf(producto?.takeIf { it.id == id })
        override suspend fun obtenerPorProviderId(providerId: Long) =
            producto?.takeIf { it.providerId == providerId }
        override suspend fun obtenerPorRemoteId(remoteId: String) =
            producto?.takeIf { it.remoteId == remoteId }
        override suspend fun insertar(entidad: ProductoEntity) = 1L
        override suspend fun actualizar(entidad: ProductoEntity) = 1
        override suspend fun descontarStockLocal(productoId: Long, cantidad: Int) = 1
    }

    private class FakePedidoDao : PedidoDao {
        override suspend fun insertarPedido(pedido: PedidoEntity) = 1L
        override suspend fun insertarDetalles(detalles: List<PedidoDetalleEntity>) = Unit
        override suspend fun insertarDireccion(direccion: DireccionEntity) = 1L
        override suspend fun obtenerPedidoPorId(pedidoId: Long): PedidoEntity? = null
        override suspend fun obtenerDetallesPedido(pedidoId: Long) = emptyList<PedidoDetalleEntity>()
        override suspend fun obtenerDireccionPedido(pedidoId: Long): DireccionEntity? = null
    }

    private class FakeMovimientoDao : MovimientoInventarioDao {
        override suspend fun insertar(entidad: MovimientoInventarioEntity) = 1L
        override suspend fun obtenerPorUuid(uuid: String) = null
        override suspend fun contarPendientesDeProducto(productoId: Long) = 0
    }

    private class FakeSyncMetaDao : SyncMetadataDao {
        private var meta: SyncMetadataEntity? = null
        override fun observar() = flowOf(meta)
        override suspend fun obtener() = meta
        override suspend fun guardar(entidad: SyncMetadataEntity) {
            meta = entidad
        }
    }

    private class FakeOperacionDao(inicial: OperacionPendienteEntity) : OperacionPendienteDao {
        private val mapa = mutableMapOf(inicial.id to inicial)
        fun porId(id: Long) = mapa[id]

        override suspend fun insertar(entidad: OperacionPendienteEntity): Long {
            mapa[entidad.id] = entidad
            return entidad.id
        }

        override suspend fun actualizar(entidad: OperacionPendienteEntity): Int {
            mapa[entidad.id] = entidad
            return 1
        }

        override suspend fun obtenerParaEnviar() =
            mapa.values.filter {
                it.estado == EstadoSincronizacion.PENDIENTE || it.estado == EstadoSincronizacion.ERROR
            }.toList()

        override fun observarPendientesActivos() = flowOf(mapa.size)
        override fun observarErrores() = flowOf(0)
        override suspend fun contarPendientesActivos() = mapa.size
        override suspend fun contarErrores() = 0
        override suspend fun contarActivasDeEntidad(tipoEntidad: String, entidadIdLocal: Long) = 0
        override suspend fun obtenerPendienteDe(
            tipoEntidad: String,
            entidadIdLocal: Long,
            operacion: String
        ) = null

        override suspend fun recuperarEnviandoAtascadas(ahora: Long): Int = 0

        override suspend fun marcarEnviando(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(estado = EstadoSincronizacion.ENVIANDO)
            return 1
        }

        override suspend fun marcarSincronizado(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(
                estado = EstadoSincronizacion.SINCRONIZADO,
                sincronizadoEn = ahora
            )
            return 1
        }

        override suspend fun marcarError(id: Long, error: String, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(
                estado = EstadoSincronizacion.ERROR,
                ultimoError = error
            )
            return 1
        }

        override suspend fun marcarLegacyStockComoError(error: String, ahora: Long): Int {
            var n = 0
            mapa.replaceAll { _, v ->
                if (v.operacion == TipoOperacionPendiente.ACTUALIZAR_STOCK) {
                    n++
                    v.copy(estado = EstadoSincronizacion.ERROR, ultimoError = error)
                } else v
            }
            return n
        }
    }
}
