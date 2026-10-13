package com.megamarket.app.di

import android.content.Context
import com.megamarket.app.data.connectivity.ObservadorConexion
import com.megamarket.app.data.connectivity.EstadoConexion
import com.megamarket.app.data.local.AppDatabase
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

    val sincronizacionRepository = SincronizacionRepository(
        operacionDao = database.operacionPendienteDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao(),
        conectado = observadorConexion.observarConexion().map { it == EstadoConexion.CONECTADO }
    )

    val sesionRepository = SesionRepository(
        administradorDao = database.administradorDao()
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
