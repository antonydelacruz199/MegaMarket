package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.local.OperacionPendiente
import com.megamarket.cliente.data.local.SyncMetadata
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SincronizacionRepositoryTest {

    @Test
    fun sinApi_conservaPendienteYNoMarcaExito() = runBlocking {
        val operaciones = DaoOperacionesPendientesFalso(
            OperacionPendiente(
                id = 1,
                uuidOperacion = "uuid-mov-1",
                tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                entidadIdLocal = 15,
                operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
                payload = """{"productoIdLocal":15,"tipo":"SALIDA_VENTA","cantidad":3}""",
                fechaCreacion = 1L
            )
        )
        val sincronizacion = SincronizacionRepository(
            operacionDao = operaciones,
            metadatosSincronizacionDao = DaoMetadatosSincronizacionFalso()
        )
        val resultado = sincronizacion.sincronizarPendientes()
        assertTrue(resultado is ResultadoSincronizacion.SinTrabajo)
        assertEquals(EstadoSincronizacion.PENDIENTE, operaciones.porId(1)?.estado)
    }

    @Test
    fun legacyActualizarStock_quedaError() = runBlocking {
        val operaciones = DaoOperacionesPendientesFalso(
            OperacionPendiente(
                id = 2,
                uuidOperacion = "legacy-1",
                tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                entidadIdLocal = 1,
                operacion = TipoOperacionPendiente.ACTUALIZAR_STOCK,
                payload = """{"stock":7}""",
                fechaCreacion = 1L
            )
        )
        val sincronizacion = SincronizacionRepository(
            operacionDao = operaciones,
            metadatosSincronizacionDao = DaoMetadatosSincronizacionFalso()
        )
        sincronizacion.sincronizarPendientes()
        assertEquals(EstadoSincronizacion.ERROR, operaciones.porId(2)?.estado)
        assertTrue(operaciones.porId(2)?.ultimoError?.contains("legacy") == true)
    }

    @Test
    fun uuidOperacionSeConservaEnEntidad() {
        val uuid = "mismo-uuid-en-retry"
        val operacionPendiente = OperacionPendiente(
            uuidOperacion = uuid,
            tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
            entidadIdLocal = 1,
            operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
            payload = "{}",
            fechaCreacion = 1L
        )
        assertEquals(uuid, operacionPendiente.uuidOperacion)
        assertEquals(EstadoSincronizacion.PENDIENTE, operacionPendiente.estado)
    }

    private class DaoMetadatosSincronizacionFalso : SyncMetadataDao {
        private var meta: SyncMetadata? = null
        override fun observarMetadatos() = flowOf(meta)
        override suspend fun obtener() = meta
        override suspend fun guardar(entidad: SyncMetadata) {
            meta = entidad
        }
    }

    private class DaoOperacionesPendientesFalso(inicial: OperacionPendiente) : OperacionPendienteDao {
        private val mapa = mutableMapOf(inicial.id to inicial)
        fun porId(id: Long) = mapa[id]

        override suspend fun insertar(entidad: OperacionPendiente): Long {
            mapa[entidad.id] = entidad
            return entidad.id
        }

        override suspend fun actualizarOperacionPendiente(entidad: OperacionPendiente): Int {
            mapa[entidad.id] = entidad
            return 1
        }

        override suspend fun obtenerOperacionesPorSincronizar() =
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

        override suspend fun marcarOperacionEnviando(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(estado = EstadoSincronizacion.ENVIANDO)
            return 1
        }

        override suspend fun marcarOperacionSincronizada(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(
                estado = EstadoSincronizacion.SINCRONIZADO,
                sincronizadoEn = ahora
            )
            return 1
        }

        override suspend fun marcarOperacionError(id: Long, error: String, ahora: Long): Int {
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
