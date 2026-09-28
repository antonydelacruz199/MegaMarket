package com.megamarket.cliente.ui.navegacion

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
import com.megamarket.cliente.AplicacionCliente
import com.megamarket.cliente.ui.pantallas.carrito.PantallaCarrito
import com.megamarket.cliente.ui.pantallas.catalogo.PantallaCatalogo
import com.megamarket.cliente.ui.pantallas.compra.PantallaCompra
import com.megamarket.cliente.ui.pantallas.confirmacion.PantallaConfirmacion
import com.megamarket.cliente.ui.pantallas.favoritos.PantallaFavoritos
import com.megamarket.cliente.ui.pantallas.inicio.PantallaInicio
import com.megamarket.cliente.ui.pantallas.perfil.PantallaPerfil
import com.megamarket.cliente.ui.pantallas.presentacion.PantallaPresentacion
import com.megamarket.cliente.ui.pantallas.producto.PantallaDetalleProducto
import com.megamarket.cliente.ui.pantallas.sesion.PantallaSesion
import com.megamarket.cliente.viewmodel.FabricaCliente
import com.megamarket.cliente.viewmodel.ViewModelCarrito
import com.megamarket.cliente.viewmodel.ViewModelCatalogo
import com.megamarket.cliente.viewmodel.ViewModelCompra
import com.megamarket.cliente.viewmodel.ViewModelConfirmacion
import com.megamarket.cliente.viewmodel.ViewModelDetalle
import com.megamarket.cliente.viewmodel.ViewModelFavoritos
import com.megamarket.cliente.viewmodel.ViewModelInicio
import com.megamarket.cliente.viewmodel.ViewModelPerfil
import com.megamarket.cliente.viewmodel.ViewModelSesion

@Composable
fun NavegacionCliente(
    controlador: NavHostController = rememberNavController()
) {
    val contexto = LocalContext.current
    val contenedor = (contexto.applicationContext as AplicacionCliente).contenedor
    val fabrica = remember(contenedor) { FabricaCliente(contenedor) }
    val fabricaOfertas = remember(contenedor) { FabricaCliente(contenedor, soloOfertas = true) }

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
            val viewModel: ViewModelSesion = viewModel(factory = fabrica)
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
                viewModel = viewModel<ViewModelInicio>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) },
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Catalogo.ruta) {
            PantallaCatalogo(
                viewModel = viewModel<ViewModelCatalogo>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) },
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Ofertas.ruta) {
            PantallaCatalogo(
                viewModel = viewModel<ViewModelCatalogo>(factory = fabricaOfertas),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) },
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Favoritos.ruta) {
            PantallaFavoritos(
                viewModel = viewModel<ViewModelFavoritos>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) },
                alAbrirProducto = { id -> controlador.navigate(Ruta.Detalle.crear(id)) }
            )
        }

        composable(Ruta.Carrito.ruta) {
            PantallaCarrito(
                viewModel = viewModel<ViewModelCarrito>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) },
                alComprar = { controlador.navigate(Ruta.Compra.ruta) }
            )
        }

        composable(Ruta.Perfil.ruta) {
            PantallaPerfil(
                viewModel = viewModel<ViewModelPerfil>(factory = fabrica),
                alNavegar = controlador::irARaiz,
                alCerrarSesion = { controlador.cerrarSesion(contenedor) }
            )
        }

        composable(
            route = Ruta.Detalle.ruta,
            arguments = listOf(navArgument(ViewModelDetalle.ARG_PRODUCTO) { type = NavType.LongType })
        ) {
            PantallaDetalleProducto(
                viewModel = viewModel<ViewModelDetalle>(factory = fabrica),
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(Ruta.Compra.ruta) {
            PantallaCompra(
                viewModel = viewModel<ViewModelCompra>(factory = fabrica),
                alVolver = { controlador.popBackStack() },
                alConfirmar = {
                    controlador.navigate(Ruta.Confirmacion.ruta) {
                        popUpTo(Ruta.Inicio.ruta)
                    }
                }
            )
        }

        composable(Ruta.Confirmacion.ruta) {
            PantallaConfirmacion(
                viewModel = viewModel<ViewModelConfirmacion>(factory = fabrica),
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

private fun NavHostController.cerrarSesion(contenedor: com.megamarket.cliente.ContenedorCliente) {
    contenedor.repositorioSesion.cerrar()
    navigate(Ruta.Sesion.ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
