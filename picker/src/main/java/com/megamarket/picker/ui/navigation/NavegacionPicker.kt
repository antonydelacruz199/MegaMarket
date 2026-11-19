package com.megamarket.picker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.megamarket.picker.MegaMarketPickerApplication
import com.megamarket.picker.ui.screens.picker.PantallaDetallePreparacion
import com.megamarket.picker.ui.screens.picker.PantallaEscaner
import com.megamarket.picker.ui.screens.picker.PantallaInicioPicker
import com.megamarket.picker.ui.screens.picker.PantallaSustitucion
import com.megamarket.picker.ui.screens.repartidor.PantallaDetalleEntrega
import com.megamarket.picker.ui.screens.repartidor.PantallaInicioRepartidor
import com.megamarket.picker.ui.screens.sesion.PantallaSesion
import com.megamarket.picker.viewmodel.DetalleEntregaViewModel
import com.megamarket.picker.viewmodel.DetallePreparacionViewModel
import com.megamarket.picker.viewmodel.EscanerViewModel
import com.megamarket.picker.viewmodel.ListaPickerViewModel
import com.megamarket.picker.viewmodel.ListaRepartidorViewModel
import com.megamarket.picker.viewmodel.SesionViewModel
import com.megamarket.picker.viewmodel.SustitucionViewModel
import com.megamarket.picker.viewmodel.ViewModelFactory

@Composable
fun NavegacionPicker(
    controlador: NavHostController = rememberNavController()
) {
    val aplicacion = LocalContext.current.applicationContext as MegaMarketPickerApplication
    val fabrica = remember(aplicacion) { ViewModelFactory(aplicacion.container) }
    val sesionViewModel: SesionViewModel = viewModel(factory = fabrica)
    val estado by sesionViewModel.estado.collectAsStateWithLifecycle()

    NavHost(
        navController = controlador,
        startDestination = Ruta.Sesion.ruta
    ) {
        composable(Ruta.Sesion.ruta) {
            LaunchedEffect(estado.restaurando, estado.usuario?.id, estado.usuario?.rol) {
                val usuario = estado.usuario ?: return@LaunchedEffect
                if (estado.restaurando) return@LaunchedEffect
                controlador.navigate(rutaDeRol(usuario.rol)) {
                    popUpTo(Ruta.Sesion.ruta) { inclusive = true }
                    launchSingleTop = true
                }
            }
            PantallaSesion(
                restaurando = estado.restaurando,
                cargando = estado.cargando,
                error = estado.error,
                alIngresar = sesionViewModel::iniciarSesion
            )
        }

        composable(Ruta.Picker.ruta) {
            val listaViewModel: ListaPickerViewModel = viewModel(factory = fabrica)
            val lista by listaViewModel.estado.collectAsStateWithLifecycle()
            val cicloVida = LocalLifecycleOwner.current
            DisposableEffect(cicloVida, listaViewModel) {
                val observador = LifecycleEventObserver { _, evento ->
                    if (evento == Lifecycle.Event.ON_RESUME) {
                        listaViewModel.cargar()
                    }
                }
                cicloVida.lifecycle.addObserver(observador)
                onDispose { cicloVida.lifecycle.removeObserver(observador) }
            }
            LaunchedEffect(lista.pedidoTomadoId) {
                val id = lista.pedidoTomadoId ?: return@LaunchedEffect
                listaViewModel.consumirPedidoTomado()
                controlador.navigate(Ruta.Preparacion.crear(id))
            }
            LaunchedEffect(lista.sesionVencida) {
                if (lista.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            PantallaInicioPicker(
                nombre = estado.usuario?.nombre.orEmpty(),
                estado = lista,
                alCargar = listaViewModel::cargar,
                alTomar = listaViewModel::tomar,
                alAbrir = { id -> controlador.navigate(Ruta.Preparacion.crear(id)) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            )
        }

        composable(
            route = Ruta.Preparacion.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PEDIDO) { type = NavType.StringType })
        ) { entrada ->
            val detalleViewModel: DetallePreparacionViewModel = viewModel(factory = fabrica)
            val detalle by detalleViewModel.estado.collectAsStateWithLifecycle()
            val refrescar by entrada.savedStateHandle
                .getStateFlow(Ruta.REFRESCAR_PREPARACION, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(refrescar) {
                if (!refrescar) return@LaunchedEffect
                detalleViewModel.cargar()
                entrada.savedStateHandle[Ruta.REFRESCAR_PREPARACION] = false
            }
            LaunchedEffect(detalle.sesionVencida) {
                if (detalle.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            val pedidoId = entrada.arguments?.getString(Ruta.ARG_PEDIDO).orEmpty()
            LaunchedEffect(detalle.listoOk) {
                if (detalle.listoOk) {
                    controlador.popBackStack()
                }
            }
            PantallaDetallePreparacion(
                estado = detalle,
                alVolver = { controlador.popBackStack() },
                alReintentar = detalleViewModel::cargar,
                alEscanear = { detalleId ->
                    controlador.navigate(Ruta.Escaner.crear(pedidoId, detalleId))
                },
                alSustituir = { detalleId ->
                    controlador.navigate(Ruta.Sustitucion.crear(pedidoId, detalleId))
                },
                alResolverSinProducto = detalleViewModel::resolverSinProducto,
                alMarcarListo = detalleViewModel::marcarListo
            )
        }

        composable(
            route = Ruta.Escaner.ruta,
            arguments = listOf(
                navArgument(Ruta.ARG_PEDIDO) { type = NavType.StringType },
                navArgument(Ruta.ARG_DETALLE) { type = NavType.StringType }
            )
        ) { entrada ->
            val escanerViewModel: EscanerViewModel = viewModel(factory = fabrica)
            val escaner by escanerViewModel.estado.collectAsStateWithLifecycle()
            val pedidoEscaneo = entrada.arguments?.getString(Ruta.ARG_PEDIDO).orEmpty()
            val detalleEscaneo = entrada.arguments?.getString(Ruta.ARG_DETALLE).orEmpty()
            DisposableEffect(pedidoEscaneo) {
                onDispose {
                    if (pedidoEscaneo.isBlank()) return@onDispose
                    runCatching {
                        controlador.getBackStackEntry(Ruta.Preparacion.crear(pedidoEscaneo))
                            .savedStateHandle[Ruta.REFRESCAR_PREPARACION] = true
                    }
                }
            }
            LaunchedEffect(escaner.sesionVencida) {
                if (escaner.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            PantallaEscaner(
                estado = escaner,
                alVolver = { controlador.popBackStack() },
                alDetectar = escanerViewModel::codigoDetectado,
                alManual = escanerViewModel::enviarManual,
                alReintentar = escanerViewModel::reintentar,
                alContinuar = escanerViewModel::continuar,
                alMarcarFaltante = escanerViewModel::marcarFaltante,
                alSustituir = {
                    controlador.navigate(Ruta.Sustitucion.crear(pedidoEscaneo, detalleEscaneo))
                }
            )
        }

        composable(
            route = Ruta.Sustitucion.ruta,
            arguments = listOf(
                navArgument(Ruta.ARG_PEDIDO) { type = NavType.StringType },
                navArgument(Ruta.ARG_DETALLE) { type = NavType.StringType }
            )
        ) { entrada ->
            val sustitucionViewModel: SustitucionViewModel = viewModel(factory = fabrica)
            val sustitucion by sustitucionViewModel.estado.collectAsStateWithLifecycle()
            val pedidoSust = entrada.arguments?.getString(Ruta.ARG_PEDIDO).orEmpty()
            DisposableEffect(pedidoSust) {
                onDispose {
                    if (pedidoSust.isBlank()) return@onDispose
                    runCatching {
                        controlador.getBackStackEntry(Ruta.Preparacion.crear(pedidoSust))
                            .savedStateHandle[Ruta.REFRESCAR_PREPARACION] = true
                    }
                }
            }
            LaunchedEffect(sustitucion.sesionVencida) {
                if (sustitucion.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            LaunchedEffect(sustitucion.lineaResuelta) {
                if (sustitucion.lineaResuelta) {
                    controlador.popBackStack()
                }
            }
            LaunchedEffect(sustitucion.detalleActualizado) {
                if (sustitucion.detalleActualizado && pedidoSust.isNotBlank()) {
                    runCatching {
                        controlador.getBackStackEntry(Ruta.Preparacion.crear(pedidoSust))
                            .savedStateHandle[Ruta.REFRESCAR_PREPARACION] = true
                    }
                }
            }
            PantallaSustitucion(
                estado = sustitucion,
                alVolver = { controlador.popBackStack() },
                alReintentar = sustitucionViewModel::cargar,
                alSeleccionar = sustitucionViewModel::seleccionar,
                alProponer = sustitucionViewModel::proponer,
                alIniciarSeguimiento = sustitucionViewModel::iniciarSeguimiento,
                alDetenerSeguimiento = sustitucionViewModel::detenerSeguimiento,
                alConsultarEstado = sustitucionViewModel::consultarEstado,
                alResolverSinProducto = sustitucionViewModel::resolverSinProducto
            )
        }

        composable(Ruta.Repartidor.ruta) {
            val listaRepartoViewModel: ListaRepartidorViewModel = viewModel(factory = fabrica)
            val listaReparto by listaRepartoViewModel.estado.collectAsStateWithLifecycle()
            LaunchedEffect(listaReparto.pedidoTomadoId) {
                val id = listaReparto.pedidoTomadoId ?: return@LaunchedEffect
                listaRepartoViewModel.consumirPedidoTomado()
                controlador.navigate(Ruta.Entrega.crear(id))
            }
            LaunchedEffect(listaReparto.sesionVencida) {
                if (listaReparto.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            PantallaInicioRepartidor(
                nombre = estado.usuario?.nombre.orEmpty(),
                estado = listaReparto,
                alCargar = listaRepartoViewModel::cargar,
                alTomar = listaRepartoViewModel::tomar,
                alAbrir = { id -> controlador.navigate(Ruta.Entrega.crear(id)) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            )
        }

        composable(
            route = Ruta.Entrega.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PEDIDO) { type = NavType.StringType })
        ) {
            val entregaViewModel: DetalleEntregaViewModel = viewModel(factory = fabrica)
            val entrega by entregaViewModel.estado.collectAsStateWithLifecycle()
            LaunchedEffect(entrega.sesionVencida) {
                if (entrega.sesionVencida) {
                    sesionViewModel.cerrarSesion()
                    controlador.volverASesion()
                }
            }
            LaunchedEffect(entrega.conflicto) {
                if (entrega.conflicto) {
                    controlador.popBackStack()
                }
            }
            PantallaDetalleEntrega(
                estado = entrega,
                fotografia = entregaViewModel::fotografia,
                alVolver = { controlador.popBackStack() },
                alReintentar = entregaViewModel::cargar,
                alFoto = entregaViewModel::establecerFoto,
                alFijarUbicacion = entregaViewModel::fijarUbicacion,
                alRegistrar = entregaViewModel::registrar,
                alGenerarQr = entregaViewModel::generarQrPago,
                alOcultarQr = entregaViewModel::ocultarQrPago,
                alConfirmarPago = { entregaViewModel.confirmarPago() },
                alConfirmarPagoBilletera = {
                    entregaViewModel.confirmarPago(
                        com.megamarket.picker.data.repository.FORMA_COBRO_BILLETERA
                    )
                }
            )
        }
    }
}

private fun NavHostController.volverASesion() {
    navigate(Ruta.Sesion.ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
