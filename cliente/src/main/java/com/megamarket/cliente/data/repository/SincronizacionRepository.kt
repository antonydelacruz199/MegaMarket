package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.SyncMetadata
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Cola local de operaciones. No contacta red: la API REST todavía no existe.
 * Las filas históricas de stock absoluto se marcan como error y no se reenvían.
 */
class SincronizacionRepository(
    private val operacionDao: OperacionPendienteDao,
    private val metadatosSincronizacionDao: SyncMetadataDao,
    private val conectado: Flow<Boolean> = flowOf(false)
) {
    data class ResumenSincronizacion(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?,
        val conectado: Boolean = false,
        val enviando: Boolean = false
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

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        operacionDao.marcarLegacyStockComoError(
            "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario.",
            ahora
        )
        registrarIntentoDeSincronizacion(ahora)
        ResultadoSincronizacion.SinTrabajo
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
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
