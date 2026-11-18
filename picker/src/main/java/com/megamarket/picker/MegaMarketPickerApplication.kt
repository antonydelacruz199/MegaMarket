package com.megamarket.picker

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.megamarket.picker.data.remote.crearCargadorImagenes
import com.megamarket.picker.di.AppContainer
import com.megamarket.picker.worker.SincronizacionPickingWorker

class MegaMarketPickerApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SincronizacionPickingWorker.programar(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        crearCargadorImagenes(context, container.clienteHttp)
}
