package com.megamarket.app

import android.app.Application
import com.megamarket.app.di.AppContainer

class MegaMarketApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
