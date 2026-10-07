package com.megamarket.app

import android.app.Application
import com.megamarket.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MegaMarketApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        alcance.launch {
            container.categoriaRepository.asegurarBootstrap()
        }
    }
}
