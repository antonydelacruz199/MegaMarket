package com.megamarket.cliente.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.megamarket.cliente.ui.screens.perfil.PantallaPerfil
import com.megamarket.cliente.ui.screens.presentacion.PantallaPresentacion
import com.megamarket.cliente.ui.screens.producto.PantallaDetalleProducto
import com.megamarket.cliente.ui.screens.sesion.PantallaSesion
import com.megamarket.cliente.viewmodel.AuthViewModel
import com.megamarket.cliente.viewmodel.CarritoViewModel
import com.megamarket.cliente.viewmodel.CatalogoViewModel
import com.megamarket.cliente.viewmodel.CheckoutViewModel
import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.cliente.viewmodel.DetalleProductoViewModel
import com.megamarket.cliente.viewmodel.FavoritosViewModel
import com.megamarket.cliente.viewmodel.HomeViewModel
import com.megamarket.cliente.viewmodel.PerfilViewModel
import com.megamarket.cliente.viewmodel.ViewModelFactory

@Composable
fun NavegacionCliente(
    controlador: NavHostController = rememberNavController()
) {
    val container = (LocalContext.current.applicationContext as MegaMarketClienteApplication).container
    val fabrica = remember(container) { ViewModelFactory(container) }
    val fabricaOfertas = remember(container) { ViewModelFactory(container, soloOfertas = true) }
    val authViewModel: AuthViewModel = viewModel(factory = fabrica)
    val cerrarSesion = {
        authViewModel.cerrarSesion()
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
            val viewModel: AuthViewModel = viewModel(factory = fabrica)
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
                viewModel = viewModel<HomeViewModel>(factory = fabrica),
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
                alCerrarSesion = cerrarSesion
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
                }
            )
        }

        composable(
            route = Ruta.Confirmacion.ruta,
            arguments = listOf(navArgument(ConfirmacionViewModel.ARG_PEDIDO) { type = NavType.LongType })
        ) {
            PantallaConfirmacion(
                viewModel = viewModel<ConfirmacionViewModel>(factory = fabrica),
                alIrAlInicio = { controlador.irARaiz(Ruta.Inicio.ruta) }
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
