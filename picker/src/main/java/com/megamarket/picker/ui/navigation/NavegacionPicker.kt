package com.megamarket.picker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

        composable(Ruta.PickerDisponibles.ruta) {
            PantallaListaPicker(
                rutaActual = Ruta.PickerDisponibles.ruta,
                seccion = SeccionPicker.DISPONIBLES,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
            )
        }
        composable(Ruta.PickerPreparacion.ruta) {
            PantallaListaPicker(
                rutaActual = Ruta.PickerPreparacion.ruta,
                seccion = SeccionPicker.PREPARACION,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
            )
        }
        composable(Ruta.PickerDespacho.ruta) {
            PantallaListaPicker(
                rutaActual = Ruta.PickerDespacho.ruta,
                seccion = SeccionPicker.DESPACHO,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
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
                    controlador.navegarAInicioPicker()
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
            LaunchedEffect(escaner.navegarASustitucion) {
                if (!escaner.navegarASustitucion) return@LaunchedEffect
                escanerViewModel.consumirNavegacionSustitucion()
                controlador.navigate(Ruta.Sustitucion.crear(pedidoEscaneo, detalleEscaneo)) {
                    popUpTo(Ruta.Escaner.crear(pedidoEscaneo, detalleEscaneo)) { inclusive = true }
                }
            }
            LaunchedEffect(escaner.pedidoConfirmado) {
                if (escaner.pedidoConfirmado) {
                    controlador.navegarAInicioPicker()
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
                alConfirmarPedido = escanerViewModel::confirmarPedido
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
                alInformarSinStock = sustitucionViewModel::informarSinStock,
                alResolverSinProducto = sustitucionViewModel::resolverSinProducto
            )
        }

        composable(Ruta.RepartidorEnCamino.ruta) {
            PantallaListaReparto(
                rutaActual = Ruta.RepartidorEnCamino.ruta,
                seccion = SeccionReparto.EN_CAMINO,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
            )
        }
        composable(Ruta.RepartidorListos.ruta) {
            PantallaListaReparto(
                rutaActual = Ruta.RepartidorListos.ruta,
                seccion = SeccionReparto.LISTOS,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
            )
        }
        composable(Ruta.RepartidorEntregados.ruta) {
            PantallaListaReparto(
                rutaActual = Ruta.RepartidorEntregados.ruta,
                seccion = SeccionReparto.ENTREGADOS,
                fabrica = fabrica,
                nombre = estado.usuario?.nombre.orEmpty(),
                controlador = controlador,
                sesionViewModel = sesionViewModel
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
                alConfirmarPago = { entregaViewModel.confirmarPago() }
            )
        }
    }
}

@Composable
private fun PantallaListaPicker(
    rutaActual: String,
    seccion: SeccionPicker,
    fabrica: ViewModelFactory,
    nombre: String,
    controlador: NavHostController,
    sesionViewModel: SesionViewModel
) {
    val actividad = LocalContext.current as androidx.activity.ComponentActivity
    val listaViewModel: ListaPickerViewModel = viewModel(
        viewModelStoreOwner = actividad,
        factory = fabrica
    )
    val lista by listaViewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(seccion) {
        listaViewModel.mostrarSeccion(seccion)
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
        nombre = nombre,
        rutaActual = rutaActual,
        seccion = seccion,
        estado = lista,
        alNavegarSeccion = { destino ->
            controlador.navigate(destino) {
                popUpTo(Ruta.PickerDisponibles.ruta) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        alCargar = {
            if (lista.seccion == seccion) {
                listaViewModel.cargar(reiniciar = true)
            } else {
                listaViewModel.mostrarSeccion(seccion)
            }
        },
        alCargarMas = listaViewModel::cargarMas,
        alTomar = listaViewModel::tomar,
        alMandarDelivery = listaViewModel::mandarADelivery,
        alAbrir = { id -> controlador.navigate(Ruta.Preparacion.crear(id)) },
        alCerrarSesion = {
            sesionViewModel.cerrarSesion()
            controlador.volverASesion()
        }
    )
}

@Composable
private fun PantallaListaReparto(
    rutaActual: String,
    seccion: SeccionReparto,
    fabrica: ViewModelFactory,
    nombre: String,
    controlador: NavHostController,
    sesionViewModel: SesionViewModel
) {
    val actividad = LocalContext.current as androidx.activity.ComponentActivity
    val listaRepartoViewModel: ListaRepartidorViewModel = viewModel(
        viewModelStoreOwner = actividad,
        factory = fabrica
    )
    val listaReparto by listaRepartoViewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(seccion) {
        listaRepartoViewModel.mostrarSeccion(seccion)
    }
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
        nombre = nombre,
        rutaActual = rutaActual,
        seccion = seccion,
        estado = listaReparto,
        alNavegarSeccion = { destino ->
            controlador.navigate(destino) {
                popUpTo(Ruta.RepartidorEnCamino.ruta) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        alCargar = {
            if (listaReparto.seccion == seccion) {
                listaRepartoViewModel.cargar(reiniciar = true)
            } else {
                listaRepartoViewModel.mostrarSeccion(seccion)
            }
        },
        alCargarMas = listaRepartoViewModel::cargarMas,
        alTomar = listaRepartoViewModel::tomar,
        alAbrir = { id -> controlador.navigate(Ruta.Entrega.crear(id)) },
        alCerrarSesion = {
            sesionViewModel.cerrarSesion()
            controlador.volverASesion()
        }
    )
}

private fun NavHostController.volverASesion() {
    navigate(Ruta.Sesion.ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navegarAInicioPicker() {
    navigate(Ruta.PickerDisponibles.ruta) {
        popUpTo(Ruta.PickerDisponibles.ruta) { inclusive = false }
        launchSingleTop = true
    }
}
