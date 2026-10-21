package com.megamarket.app.di

import android.content.Context
import com.megamarket.app.BuildConfig
import com.megamarket.app.data.connectivity.ObservadorConexion
import com.megamarket.app.data.connectivity.EstadoConexion
import com.megamarket.app.data.local.AppDatabase
import com.megamarket.app.data.local.SesionLocalStore
import com.megamarket.app.data.remote.ApiClient
import com.megamarket.app.data.repository.CatalogoRemotoRepository
import com.megamarket.app.data.repository.SesionRepository
import com.megamarket.app.data.repository.CategoriaRepository
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository
import com.megamarket.app.data.repository.SincronizacionRepository
import com.megamarket.app.worker.SincronizacionWorker
import kotlinx.coroutines.flow.map

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val observadorConexion = ObservadorConexion(appContext)
    val sesionLocal = SesionLocalStore(appContext)

    private val apiService = ApiClient(
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL,
        tokenProvider = { sesionLocal.accessToken }
    ).crear()

    val catalogoRemotoRepository = CatalogoRemotoRepository(
        api = apiService,
        categoriaDao = database.categoriaDao(),
        productoDao = database.productoDao(),
        operacionDao = database.operacionPendienteDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao()
    )

    val sincronizacionRepository = SincronizacionRepository(
        api = apiService,
        operacionDao = database.operacionPendienteDao(),
        productoDao = database.productoDao(),
        categoriaDao = database.categoriaDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao(),
        catalogoRemoto = catalogoRemotoRepository,
        conectado = observadorConexion.observarConexion().map { it == EstadoConexion.CONECTADO }
    )

    val sesionRepository = SesionRepository(
        administradorDao = database.administradorDao(),
        sesionLocal = sesionLocal,
        api = apiService,
        catalogoRemoto = catalogoRemotoRepository,
        observadorConexion = observadorConexion
    )

    val categoriaRepository = CategoriaRepository(database.categoriaDao())

    val productoRepository = ProductoRepository(
        database = database,
        productoDao = database.productoDao(),
        movimientoDao = database.movimientoInventarioDao(),
        operacionDao = database.operacionPendienteDao(),
        resolver = appContext.contentResolver,
        programarSincronizacion = { SincronizacionWorker.programar(appContext) }
    )

    val imagenRepository = ImagenRepository(appContext)
}
