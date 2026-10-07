package com.megamarket.cliente

import android.app.Application
import com.megamarket.cliente.di.AppContainer
import com.megamarket.cliente.worker.SyncWorker

class MegaMarketClienteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.programar(this)
    }
}
