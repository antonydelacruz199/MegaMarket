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
import com.megamarket.cliente.data.repository.BilleteraRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.FranjasRepository
import com.megamarket.cliente.data.repository.MediosImagenRepository
import com.megamarket.cliente.data.repository.MisPedidosRepository
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.SincronizacionRepository
import com.megamarket.cliente.data.repository.SustitucionesRepository
import com.megamarket.cliente.worker.SincronizacionWorker
import kotlinx.coroutines.flow.map

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val observadorConexion = ObservadorConexion(appContext)
    val sesionLocal = SesionLocalStore(appContext)

    private val apiClient = ApiClient(
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL,
        tokenProvider = { sesionLocal.accessToken },
        ownerProvider = { sesionLocal.usuarioRemotoId.takeIf { sesionLocal.owner.value > 0 } },
        tokenForOwner = { sesionLocal.tokenGuardado(it) }
    )
    private val apiService = apiClient.crear()
    val clienteHttp = apiClient.http
    val mediosImagenRepository = MediosImagenRepository(BuildConfig.MEGAMARKET_API_BASE_URL)

    val catalogoRemotoRepository = CatalogoRemotoRepository(
        api = apiService,
        categoriaDao = database.categoriaDao(),
        productoDao = database.productoDao(),
        operacionDao = database.operacionPendienteDao(),
        metadatosSincronizacionDao = database.metadatosSincronizacionDao(),
        database = database,
        sesion = sesionLocal,
        bootCount = { sesionLocal.bootCount() }
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
        sesion = sesionLocal,
        conectado = observadorConexion.observarConexion().map { it == EstadoConexion.CONECTADO }
    )

    val sesionRepository = SesionRepository(
        clienteDao = database.clienteDao(),
        sesionLocal = sesionLocal,
        api = apiService,
        catalogoRemoto = catalogoRemotoRepository,
        observadorConexion = observadorConexion,
        programarSincronizacion = { SincronizacionWorker.programarSincronizacionInmediata(appContext) }
    )

    val carritoRepository = CarritoRepository(
        database,
        catalogoRepository,
        sesionLocal,
        { SincronizacionWorker.programar(appContext) }
    )

    val favoritosRepository = FavoritosRepository(
        database.favoritoDao(),
        catalogoRepository,
        sesionLocal
    )

    val pedidoRepository = PedidoRepository(
        database,
        database.pedidoDao(),
        database.carritoDao(),
        database.productoDao(),
        database.movimientoInventarioDao(),
        database.operacionPendienteDao(),
        // REPLACE: el pedido recién creado no debe quedar detrás de un Work KEEP pendiente.
        { SincronizacionWorker.programarSincronizacionInmediata(appContext) },
        sesionLocal
    )

    val franjasRepository = FranjasRepository(
        api = apiService,
        usuarioRemotoId = { sesionLocal.usuarioRemotoId }
    )

    val sustitucionesRepository = SustitucionesRepository(
        api = apiService,
        productoDao = database.productoDao(),
        pedidoDao = database.pedidoDao(),
        medios = mediosImagenRepository,
        usuarioRemotoId = { sesionLocal.usuarioRemotoId },
        ownerLocalId = { sesionLocal.owner.value }
    )

    val misPedidosRepository = MisPedidosRepository(
        api = apiService,
        pedidoDao = database.pedidoDao(),
        sustituciones = sustitucionesRepository,
        franjas = franjasRepository,
        usuarioRemotoId = { sesionLocal.usuarioRemotoId },
        ownerLocalId = { sesionLocal.owner.value }
    )

    val pagosRepository = PagosRepository(
        api = apiService,
        usuarioRemotoId = { sesionLocal.usuarioRemotoId }
    )

    val billeteraRepository = BilleteraRepository(
        api = apiService,
        usuarioRemotoId = { sesionLocal.usuarioRemotoId }
    )
}
