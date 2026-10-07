package com.megamarket.cliente.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.megamarket.cliente.MegaMarketClienteApplication
import com.megamarket.cliente.data.repository.ResultadoSincronizacion

/**
 * Sincroniza operaciones_pendientes cuando hay red.
 * No contiene UI. No marca éxito si la API/UUID no están listos.
 */
class StockSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MegaMarketClienteApplication
            ?: return Result.retry()
        return when (val resultado = app.container.syncRepository.sincronizarPendientes()) {
            is ResultadoSincronizacion.Exito,
            is ResultadoSincronizacion.SinTrabajo -> Result.success()
            is ResultadoSincronizacion.Reintentar -> Result.retry()
        }
    }

    companion object {
        const val NOMBRE_UNICO = "megamarket_stock_sync"

        fun programar(contexto: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val trabajo = OneTimeWorkRequestBuilder<StockSyncWorker>()
                .setConstraints(restricciones)
                .build()
            WorkManager.getInstance(contexto.applicationContext)
                .enqueueUniqueWork(NOMBRE_UNICO, ExistingWorkPolicy.KEEP, trabajo)
        }
    }
}
