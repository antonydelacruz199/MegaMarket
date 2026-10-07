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

class AppContainer(context: Context) {

    val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val productoDao = database.productoDao()
    val categoriaDao = database.categoriaDao()
    val sessionStore = SessionStore(appContext)
    val connectivityObserver = ConnectivityObserver(appContext)

    val remote = RetrofitProvider.crear(BuildConfig.API_BASE_URL)

    val authRepository = AuthRepository(database.clienteDao(), sessionStore)

    val catalogoRepository = CatalogoRepository(
        resolver = appContext.contentResolver,
        productoDao = productoDao,
        categoriaDao = categoriaDao,
        movimientoDao = database.movimientoInventarioDao()
    ).also { it.iniciar() }

    val carritoRepository = CarritoRepository(
        database.carritoDao(),
        catalogoRepository
    )

    val favoritosRepository = FavoritosRepository(
        database.favoritoDao(),
        catalogoRepository
    )

    val syncRepository = SyncRepository(
        operacionDao = database.operacionPendienteDao(),
        productoDao = productoDao,
        pedidoDao = database.pedidoDao(),
        movimientoDao = database.movimientoInventarioDao(),
        syncMetadataDao = database.syncMetadataDao(),
        baseUrlApi = BuildConfig.API_BASE_URL,
        remote = remote
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
