package com.megamarket.cliente

import android.app.Application
import com.megamarket.cliente.di.AppContainer
import com.megamarket.cliente.worker.StockSyncWorker

class MegaMarketClienteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Reintenta pendientes al abrir la app (solo corre con red; no finge éxito).
        StockSyncWorker.programar(this)
    }
}
