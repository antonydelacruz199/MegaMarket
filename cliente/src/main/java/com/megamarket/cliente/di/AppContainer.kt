package com.megamarket.cliente.di

import android.content.Context
import com.megamarket.cliente.BuildConfig
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.repository.AuthRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.SyncRepository

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val authRepository = AuthRepository(database.clienteDao())

    val catalogoRepository = CatalogoRepository(appContext.contentResolver)

    val carritoRepository = CarritoRepository(
        database.carritoDao(),
        catalogoRepository
    )

    val favoritosRepository = FavoritosRepository(
        database.favoritoDao(),
        catalogoRepository
    )

    val syncRepository = SyncRepository(
        dao = database.operacionPendienteDao(),
        baseUrlApi = BuildConfig.API_BASE_URL
        // resolverUuidRemoto queda en null hasta existir mapeo Long → UUID
    )

    val pedidoRepository = PedidoRepository(
        database,
        database.pedidoDao(),
        database.carritoDao(),
        catalogoRepository,
        syncRepository,
        PedidoRepository.crearProgramador(appContext)
    )
}
