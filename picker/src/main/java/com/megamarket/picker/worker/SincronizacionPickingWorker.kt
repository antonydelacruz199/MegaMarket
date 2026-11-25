package com.megamarket.picker.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.megamarket.picker.MegaMarketPickerApplication
import com.megamarket.picker.data.repository.ResultadoSincronizacion
import java.util.concurrent.TimeUnit

class SincronizacionPickingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MegaMarketPickerApplication
            ?: return Result.success()
        return when (app.container.sincronizacionRepository.sincronizarPendientes()) {
            is ResultadoSincronizacion.Exito,
            is ResultadoSincronizacion.SinTrabajo -> Result.success()
            is ResultadoSincronizacion.Reintentar -> Result.retry()
        }
    }

    companion object {
        const val NOMBRE_UNICO = "megamarket_picker_sync"
        const val NOMBRE_PERIODICO = "megamarket_picker_sync_periodico"
        private const val INTERVALO_MINUTOS = 15L

        fun programar(contexto: Context, reemplazar: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val trabajo = OneTimeWorkRequestBuilder<SincronizacionPickingWorker>()
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

        fun programarInmediata(contexto: Context) = programar(contexto, reemplazar = true)

        fun programarPeriodica(contexto: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val trabajo = PeriodicWorkRequestBuilder<SincronizacionPickingWorker>(INTERVALO_MINUTOS, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto.applicationContext)
                .enqueueUniquePeriodicWork(
                    NOMBRE_PERIODICO,
                    ExistingPeriodicWorkPolicy.KEEP,
                    trabajo
                )
        }
    }
}
