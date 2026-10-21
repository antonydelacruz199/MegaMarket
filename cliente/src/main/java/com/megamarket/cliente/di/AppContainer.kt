package com.megamarket.cliente.di

import android.content.Context
import com.megamarket.cliente.BuildConfig
import com.megamarket.cliente.data.connectivity.ObservadorConexion
import com.megamarket.cliente.data.connectivity.EstadoConexion
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.local.SesionLocalStore
import com.megamarket.cliente.data.remote.ApiClient
import com.megamarket.cliente.data.repository.CatalogoRemotoRepository
import com.megamarket.cliente.data.repository.SesionRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.SincronizacionRepository
import com.megamarket.cliente.worker.SincronizacionWorker
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

    val catalogoRepository = CatalogoRepository(
        resolver = appContext.contentResolver,
        productoDao = database.productoDao(),
        categoriaDao = database.categoriaDao()
    )

    val sincronizacionRepository = SincronizacionRepository(
        api = apiService,
        operacionDao = database.operacionPendienteDao(),
        pedidoDao = database.pedidoDao(),
        productoDao = database.productoDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao(),
        catalogoRemoto = catalogoRemotoRepository,
        conectado = observadorConexion.observarConexion().map { it == EstadoConexion.CONECTADO }
    )

    val sesionRepository = SesionRepository(
        clienteDao = database.clienteDao(),
        sesionLocal = sesionLocal,
        api = apiService,
        catalogoRemoto = catalogoRemotoRepository,
        observadorConexion = observadorConexion
    )

    val carritoRepository = CarritoRepository(
        database.carritoDao(),
        catalogoRepository
    )

    val favoritosRepository = FavoritosRepository(
        database.favoritoDao(),
        catalogoRepository
    )

    val pedidoRepository = PedidoRepository(
        database,
        database.pedidoDao(),
        database.carritoDao(),
        database.productoDao(),
        database.movimientoInventarioDao(),
        database.operacionPendienteDao(),
        { SincronizacionWorker.programar(appContext) }
    )
}
