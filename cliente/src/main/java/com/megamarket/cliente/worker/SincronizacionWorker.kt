package com.megamarket.cliente.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.megamarket.cliente.MegaMarketClienteApplication
import com.megamarket.cliente.data.repository.ResultadoSincronizacion
import java.util.concurrent.TimeUnit

/**
 * Mantiene la cola local. No llama a una API.
 */
class SincronizacionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MegaMarketClienteApplication
            ?: return Result.success()
        return when (app.container.sincronizacionRepository.sincronizarPendientes()) {
            is ResultadoSincronizacion.Exito,
            is ResultadoSincronizacion.SinTrabajo -> Result.success()
            is ResultadoSincronizacion.Reintentar -> Result.success()
        }
    }

    companion object {
        const val NOMBRE_UNICO = "megamarket_cliente_sync"

        fun programar(contexto: Context, reemplazar: Boolean = false) {
            val trabajo = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto.applicationContext)
                .enqueueUniqueWork(
                    NOMBRE_UNICO,
                    if (reemplazar) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    trabajo
                )
        }

        fun programarSincronizacionInmediata(contexto: Context) = programar(contexto, reemplazar = true)
    }
}
