package com.megamarket.cliente.di

import android.content.Context
import com.megamarket.cliente.data.connectivity.ObservadorConexion
import com.megamarket.cliente.data.connectivity.EstadoConexion
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.repository.SesionRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.SincronizacionRepository
import kotlinx.coroutines.flow.map

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val observadorConexion = ObservadorConexion(appContext)

    val catalogoRepository = CatalogoRepository(
        resolver = appContext.contentResolver,
        productoDao = database.productoDao(),
        categoriaDao = database.categoriaDao()
    ).also { it.iniciarObservacionDelCatalogo() }

    val sincronizacionRepository = SincronizacionRepository(
        operacionDao = database.operacionPendienteDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao(),
        conectado = observadorConexion.observarConexion().map { it == EstadoConexion.CONECTADO }
    )

    val sesionRepository = SesionRepository(
        clienteDao = database.clienteDao()
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
        PedidoRepository.crearProgramador(appContext)
    )
}
