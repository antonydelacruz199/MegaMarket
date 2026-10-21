package com.megamarket.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.megamarket.app.MegaMarketApplication
import com.megamarket.app.data.repository.ResultadoSincronizacion
import java.util.concurrent.TimeUnit

class SincronizacionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MegaMarketApplication
            ?: return Result.success()
        return when (app.container.sincronizacionRepository.sincronizarPendientes()) {
            is ResultadoSincronizacion.Exito,
            is ResultadoSincronizacion.SinTrabajo -> Result.success()
            is ResultadoSincronizacion.Reintentar -> Result.retry()
        }
    }

    companion object {
        const val NOMBRE_UNICO = "megamarket_admin_sync"

        fun programar(contexto: Context, reemplazar: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val trabajo = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setConstraints(constraints)
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
