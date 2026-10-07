package com.megamarket.cliente.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.megamarket.cliente.MegaMarketClienteApplication
import com.megamarket.cliente.data.repository.ResultadoSincronizacion
import java.util.concurrent.TimeUnit

/**
 * Sincroniza cola de operaciones cuando hay red.
 * Backoff exponencial; no finge éxito sin API.
 */
class SyncWorker(
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
        const val NOMBRE_UNICO = "megamarket_cliente_sync"

        fun programar(contexto: Context, reemplazar: Boolean = false) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val trabajo = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto.applicationContext)
                .enqueueUniqueWork(
                    NOMBRE_UNICO,
                    if (reemplazar) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    trabajo
                )
        }

        fun sincronizarAhora(contexto: Context) = programar(contexto, reemplazar = true)
    }
}

/** Alias de compatibilidad. */
@Deprecated("Usar SyncWorker", ReplaceWith("SyncWorker"))
typealias StockSyncWorker = SyncWorker
