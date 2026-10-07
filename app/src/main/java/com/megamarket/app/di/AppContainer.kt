package com.megamarket.app.di

import android.content.Context
import com.megamarket.app.BuildConfig
import com.megamarket.app.data.connectivity.ConnectivityObserver
import com.megamarket.app.data.local.AppDatabase
import com.megamarket.app.data.remote.RetrofitProvider
import com.megamarket.app.data.repository.AuthRepository
import com.megamarket.app.data.repository.CategoriaRepository
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository
import com.megamarket.app.data.repository.SyncRepository
import com.megamarket.app.data.session.SessionStore
import com.megamarket.app.worker.SyncWorker

class AppContainer(context: Context) {

    val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val sessionStore = SessionStore(appContext)
    val connectivityObserver = ConnectivityObserver(appContext)
    val remote = RetrofitProvider.crear(BuildConfig.API_BASE_URL)

    val authRepository = AuthRepository(database.administradorDao())

    val categoriaRepository = CategoriaRepository(database.categoriaDao())

    val syncRepository = SyncRepository(
        operacionDao = database.operacionPendienteDao(),
        productoDao = database.productoDao(),
        categoriaDao = database.categoriaDao(),
        movimientoDao = database.movimientoInventarioDao(),
        syncMetadataDao = database.syncMetadataDao(),
        baseUrlApi = BuildConfig.API_BASE_URL,
        remote = remote
    )

    val productoRepository = ProductoRepository(
        database = database,
        productoDao = database.productoDao(),
        movimientoDao = database.movimientoInventarioDao(),
        operacionDao = database.operacionPendienteDao(),
        resolver = appContext.contentResolver,
        programarSync = { SyncWorker.programar(appContext) }
    )

    val imagenRepository = ImagenRepository(appContext)
}
