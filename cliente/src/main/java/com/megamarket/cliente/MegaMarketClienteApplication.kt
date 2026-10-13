package com.megamarket.cliente

import android.app.Application
import com.megamarket.cliente.di.AppContainer
import com.megamarket.cliente.worker.SincronizacionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MegaMarketClienteApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        alcance.launch {
            container.sesionRepository.prepararCuentaLocal()
        }
        SincronizacionWorker.programar(this)
    }
}
