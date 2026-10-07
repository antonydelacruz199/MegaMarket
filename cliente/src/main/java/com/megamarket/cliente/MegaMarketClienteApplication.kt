package com.megamarket.cliente

import android.app.Application
import com.megamarket.cliente.di.AppContainer

class MegaMarketClienteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
