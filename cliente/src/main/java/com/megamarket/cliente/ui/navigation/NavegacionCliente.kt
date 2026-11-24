package com.megamarket.cliente.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.megamarket.cliente.MegaMarketClienteApplication
import com.megamarket.cliente.ui.screens.carrito.PantallaCarrito
import com.megamarket.cliente.ui.screens.catalogo.PantallaCatalogo
import com.megamarket.cliente.ui.screens.checkout.PantallaCheckout
import com.megamarket.cliente.ui.screens.confirmacion.PantallaConfirmacion
import com.megamarket.cliente.ui.screens.favoritos.PantallaFavoritos
import com.megamarket.cliente.ui.screens.inicio.PantallaInicio
import com.megamarket.cliente.ui.screens.pagos.PantallaBilletera
import com.megamarket.cliente.ui.screens.pagos.PantallaEscanearPago
import com.megamarket.cliente.ui.screens.pagos.PantallaPagoQr
import com.megamarket.cliente.ui.screens.perfil.PantallaPerfil
import com.megamarket.cliente.ui.screens.presentacion.PantallaPresentacion
import com.megamarket.cliente.ui.screens.producto.PantallaDetalleProducto
import com.megamarket.cliente.ui.screens.sesion.PantallaSesion
import com.megamarket.cliente.ui.screens.pedidos.PantallaDetallePedido
import com.megamarket.cliente.ui.screens.pedidos.PantallaMisPedidos
import com.megamarket.cliente.ui.screens.sustituciones.PantallaDetalleSustitucion
import com.megamarket.cliente.ui.screens.sustituciones.PantallaSustituciones
import com.megamarket.cliente.viewmodel.SesionViewModel
import com.megamarket.cliente.viewmodel.BilleteraViewModel
import com.megamarket.cliente.viewmodel.CarritoViewModel
import com.megamarket.cliente.viewmodel.CatalogoViewModel
import com.megamarket.cliente.viewmodel.CheckoutViewModel
import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.cliente.viewmodel.DetallePedidoViewModel
import com.megamarket.cliente.viewmodel.DetalleProductoViewModel
import com.megamarket.cliente.viewmodel.DetalleSustitucionViewModel
import com.megamarket.cliente.viewmodel.EscanearPagoViewModel
import com.megamarket.cliente.viewmodel.FavoritosViewModel
import com.megamarket.cliente.viewmodel.InicioViewModel
import com.megamarket.cliente.viewmodel.MisPedidosViewModel
import com.megamarket.cliente.viewmodel.PagoQrViewModel
import com.megamarket.cliente.viewmodel.PerfilViewModel
import com.megamarket.cliente.viewmodel.SustitucionesViewModel
import com.megamarket.cliente.viewmodel.ViewModelFactory

@Composable
fun NavegacionCliente(
    controlador: NavHostController = rememberNavController()
) {
    val container = (LocalContext.current.applicationContext as MegaMarketClienteApplication).container
    val fabrica = remember(container) { ViewModelFactory(container) }
    val fabricaOfertas = remember(container) { ViewModelFactory(container, soloOfertas = true) }
    val sesionViewModel: SesionViewModel = viewModel(factory = fabrica)
    val cerrarSesion = {
        sesionViewModel.cerrarSesion()
        controlador.irASesion()
    }

    NavHost(navController = controlador, startDestination = Ruta.Presentacion.ruta) {
        composable(Ruta.Presentacion.ruta) {
            PantallaPresentacion(
                alIrASesion = {
                    controlador.navigate(Ruta.Sesion.ruta) {
                        popUpTo(Ruta.Presentacion.ruta) { inclusive = true }
                    }
                }
            )
        }

        composable(Ruta.Sesion.ruta) {
            val viewModel: SesionViewModel = viewModel(factory = fabrica)
            LaunchedEffect(viewModel.estado) {
                viewModel.estado.collect { estado ->
                    if (estado.usuario != null) {
                        controlador.navigate(Ruta.Inicio.ruta) {
                            popUpTo(Ruta.Sesion.ruta) { inclusive = true }
                        }
                    }
                }
            }
            PantallaSesion(viewModel)
        }

        composable(Ruta.Inicio.ruta) {
            PantallaInicio(
                viewModel = viewModel<InicioViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Catalogo.ruta) {
            PantallaCatalogo(
                viewModel = viewModel<CatalogoViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Ofertas.ruta) {
            PantallaCatalogo(
                viewModel = viewModel<CatalogoViewModel>(factory = fabricaOfertas),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Favoritos.ruta) {
            PantallaFavoritos(
                viewModel = viewModel<FavoritosViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Carrito.ruta) {
            PantallaCarrito(
                viewModel = viewModel<CarritoViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alComprar = { controlador.navigate(Ruta.Checkout.ruta) }
            )
        }

        composable(Ruta.Perfil.ruta) {
            PantallaPerfil(
                viewModel = viewModel<PerfilViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirBilletera = { controlador.navigate(Ruta.Billetera.ruta) }
            )
        }

        composable(Ruta.Billetera.ruta) {
            PantallaBilletera(
                viewModel = viewModel<BilleteraViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alCerrarSesion = cerrarSesion
            )
        }

        composable(
            route = Ruta.PagoQr.ruta,
            arguments = listOf(
                navArgument(Ruta.ARG_PEDIDO_REMOTO) { type = NavType.StringType },
                navArgument(Ruta.ARG_ABRIR_QR) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            PantallaPagoQr(
                viewModel = viewModel<PagoQrViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alCerrarSesion = cerrarSesion,
                alPagoConfirmado = {
                    controlador.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(ConfirmacionViewModel.KEY_PAGO_REALIZADO, true)
                    controlador.popBackStack()
                }
            )
        }

        composable(Ruta.MisPedidos.ruta) {
            PantallaMisPedidos(
                viewModel = viewModel<MisPedidosViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirDetalle = { id -> controlador.navigate(Ruta.DetallePedido.crear(id)) }
            )
        }

        composable(
            route = Ruta.DetallePedido.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PEDIDO_REMOTO) { type = NavType.StringType })
        ) {
            PantallaDetallePedido(
                viewModel = viewModel<DetallePedidoViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alCerrarSesion = cerrarSesion,
                alPagarQr = { pedidoId ->
                    controlador.navigate(Ruta.PagoQr.crear(pedidoId, abrirQr = true))
                },
                alEscanearQr = { pedidoId ->
                    controlador.navigate(Ruta.EscanearPago.crear(pedidoId))
                }
            )
        }

        composable(
            route = Ruta.EscanearPago.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PEDIDO_REMOTO) { type = NavType.StringType })
        ) {
            PantallaEscanearPago(
                viewModel = viewModel<EscanearPagoViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alCerrarSesion = cerrarSesion,
                alPagoConfirmado = { controlador.popBackStack() }
            )
        }

        composable(Ruta.Sustituciones.ruta) {
            PantallaSustituciones(
                viewModel = viewModel<SustitucionesViewModel>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = cerrarSesion,
                alAbrirDetalle = { id -> controlador.navigate(Ruta.DetalleSustitucion.crear(id)) }
            )
        }

        composable(
            route = Ruta.DetalleSustitucion.ruta,
            arguments = listOf(navArgument(Ruta.ARG_SUSTITUCION) { type = NavType.StringType })
        ) {
            PantallaDetalleSustitucion(
                viewModel = viewModel<DetalleSustitucionViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alCerrarSesion = cerrarSesion,
                alAbrirPedido = { pedidoId ->
                    controlador.navigate(Ruta.Confirmacion.crear(pedidoId)) {
                        popUpTo(Ruta.Sustituciones.ruta)
                    }
                }
            )
        }

        composable(
            route = Ruta.Detalle.ruta,
            arguments = listOf(navArgument(DetalleProductoViewModel.ARG_PRODUCTO) { type = NavType.LongType })
        ) {
            PantallaDetalleProducto(
                viewModel = viewModel<DetalleProductoViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alIrAlCarrito = { controlador.irARaiz(Ruta.Carrito.ruta) },
                alSeguirComprando = { controlador.irARaiz(Ruta.Catalogo.ruta) }
            )
        }

        composable(Ruta.Checkout.ruta) {
            PantallaCheckout(
                viewModel = viewModel<CheckoutViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alConfirmar = { pedidoId ->
                    controlador.navigate(Ruta.Confirmacion.crear(pedidoId)) {
                        popUpTo(Ruta.Inicio.ruta)
                    }
                },
                alAnadirProductos = {
                    controlador.navigate(Ruta.Catalogo.ruta)
                }
            )
        }

        composable(
            route = Ruta.Confirmacion.ruta,
            arguments = listOf(navArgument(ConfirmacionViewModel.ARG_PEDIDO) { type = NavType.LongType })
        ) { entrada ->
            val pagoRecienConfirmado by entrada.savedStateHandle
                .getStateFlow(ConfirmacionViewModel.KEY_PAGO_REALIZADO, false)
                .collectAsStateWithLifecycle()
            PantallaConfirmacion(
                viewModel = viewModel<ConfirmacionViewModel>(factory = fabrica),
                alIrAlInicio = { controlador.irARaiz(Ruta.Inicio.ruta) },
                alPagarConQr = { pedidoId ->
                    entrada.savedStateHandle[ConfirmacionViewModel.KEY_PAGO_REALIZADO] = false
                    controlador.navigate(Ruta.PagoQr.crear(pedidoId, abrirQr = true))
                },
                alEscanearQr = { pedidoId ->
                    entrada.savedStateHandle[ConfirmacionViewModel.KEY_PAGO_REALIZADO] = false
                    controlador.navigate(Ruta.EscanearPago.crear(pedidoId))
                },
                alSeguirPedido = { pedidoRemotoId ->
                    controlador.navigate(Ruta.DetallePedido.crear(pedidoRemotoId)) {
                        popUpTo(Ruta.Inicio.ruta)
                    }
                },
                pagoRecienConfirmado = pagoRecienConfirmado,
                alConsumirPagoConfirmado = {
                    entrada.savedStateHandle[ConfirmacionViewModel.KEY_PAGO_REALIZADO] = false
                }
            )
        }
    }
}

private fun NavHostController.irARaiz(ruta: String) {
    navigate(ruta) {
        popUpTo(Ruta.Inicio.ruta) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.irASesion() {
    navigate(Ruta.Sesion.ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
