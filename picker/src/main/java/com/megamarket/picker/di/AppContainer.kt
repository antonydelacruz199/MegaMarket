package com.megamarket.picker.di

import android.content.Context
import android.os.Build
import com.megamarket.picker.BuildConfig
import com.megamarket.picker.data.connectivity.EstadoConexion
import com.megamarket.picker.data.connectivity.ObservadorConexion
import com.megamarket.picker.data.local.OperacionEntregaLocal
import com.megamarket.picker.data.local.PickerDatabase
import com.megamarket.picker.data.local.SesionLocalStore
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.repository.CompresorJpegEntrega
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.PedidosRepartoRepository
import com.megamarket.picker.data.repository.SesionRepository
import com.megamarket.picker.data.repository.SincronizacionPickingRepository
import com.megamarket.picker.data.repository.SustitucionesRepository
import com.megamarket.picker.worker.SincronizacionPickingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val observadorConexion = ObservadorConexion(appContext)
    val sesionLocal = SesionLocalStore(appContext)
    private val db = PickerDatabase.obtener(appContext)

    private val apiClient = ApiClient(
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL,
        tokenProvider = { sesionLocal.tokenActual() },
        ownerProvider = { sesionLocal.usuarioActivoId() },
        tokenForOwner = { sesionLocal.tokenDe(it) }
    )
    val clienteHttp = apiClient.http
    private val api = apiClient.crear()

    private val programarSync: () -> Unit = {
        SincronizacionPickingWorker.programarInmediata(appContext)
    }

    val sincronizacionRepository = SincronizacionPickingRepository(
        api = api,
        operacionDao = db.operacionDao(),
        lineaDao = db.lineaDao(),
        almacen = sesionLocal,
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL
    )

    val pedidosRepository = PedidosPickerRepository(
        api = api,
        almacen = sesionLocal,
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL,
        pedidoDao = db.pedidoDao(),
        lineaDao = db.lineaDao(),
        operacionDao = db.operacionDao(),
        sincronizacion = sincronizacionRepository,
        programarSincronizacion = programarSync
    )

    val repartoRepository = PedidosRepartoRepository(
        api = api,
        almacen = sesionLocal,
        operaciones = OperacionEntregaLocal(appContext),
        compresor = CompresorJpegEntrega()
    )

    val sesionRepository = SesionRepository(
        almacen = sesionLocal,
        api = api,
        hayConexion = { observadorConexion.hayInternet() },
        nombreDispositivo = {
            listOfNotNull(Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ")
                .ifBlank { "Android" }
        }
    )

    val sustitucionesRepository = SustitucionesRepository(
        api = api,
        almacen = sesionLocal,
        lineaDao = db.lineaDao(),
        operacionDao = db.operacionDao(),
        pedidosRepository = pedidosRepository,
        baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL
    )

    init {
        alcance.launch {
            observadorConexion.observarConexion().collectLatest { estado ->
                if (estado == EstadoConexion.CONECTADO) {
                    SincronizacionPickingWorker.programar(appContext)
                }
            }
        }
    }
}
