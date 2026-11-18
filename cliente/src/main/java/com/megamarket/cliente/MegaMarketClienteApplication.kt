package com.megamarket.cliente

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.megamarket.cliente.data.remote.crearCargadorImagenes
import com.megamarket.cliente.di.AppContainer
import com.megamarket.cliente.worker.SincronizacionWorker

class MegaMarketClienteApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SincronizacionWorker.programar(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        crearCargadorImagenes(context, container.clienteHttp)
}
