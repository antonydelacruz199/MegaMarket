package com.megamarket.cliente.di

import android.content.Context
import com.megamarket.cliente.BuildConfig
import com.megamarket.cliente.data.connectivity.ConnectivityObserver
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.remote.RetrofitProvider
import com.megamarket.cliente.data.repository.AuthRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.SyncRepository
import com.megamarket.cliente.data.session.SessionStore
import com.megamarket.cliente.worker.SyncWorker

class AppContainer(context: Context) {

    val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val productoDao = database.productoDao()
    val categoriaDao = database.categoriaDao()
    val sessionStore = SessionStore(appContext)
    val connectivityObserver = ConnectivityObserver(appContext)

    val remote = RetrofitProvider.crear(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenProvider = { sessionStore.accessToken }
    )

    val catalogoRepository = CatalogoRepository(
        resolver = appContext.contentResolver,
        productoDao = productoDao,
        categoriaDao = categoriaDao,
        movimientoDao = database.movimientoInventarioDao()
    ).also { it.iniciar() }

    val syncRepository = SyncRepository(
        operacionDao = database.operacionPendienteDao(),
        productoDao = productoDao,
        pedidoDao = database.pedidoDao(),
        movimientoDao = database.movimientoInventarioDao(),
        syncMetadataDao = database.syncMetadataDao(),
        baseUrlApi = BuildConfig.API_BASE_URL,
        catalogoRepository = catalogoRepository,
        sessionStore = sessionStore,
        remote = remote
    )

    val authRepository = AuthRepository(
        dao = database.clienteDao(),
        sessionStore = sessionStore,
        authApi = remote?.authApi,
        connectivity = connectivityObserver,
        trasLoginExitoso = {
            SyncWorker.programar(appContext)
            syncRepository.sincronizarPendientes()
        }
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
        productoDao,
        database.movimientoInventarioDao(),
        database.operacionPendienteDao(),
        PedidoRepository.crearProgramador(appContext)
    )
}
