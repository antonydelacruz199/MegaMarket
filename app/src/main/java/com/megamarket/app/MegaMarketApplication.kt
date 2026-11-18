package com.megamarket.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.megamarket.app.data.remote.crearCargadorImagenes
import com.megamarket.app.di.AppContainer
import com.megamarket.app.worker.SincronizacionWorker

class MegaMarketApplication : Application(), SingletonImageLoader.Factory {

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
