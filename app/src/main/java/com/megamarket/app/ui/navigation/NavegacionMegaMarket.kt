package com.megamarket.app.ui.navigation

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
import com.megamarket.app.MegaMarketApplication
import com.megamarket.app.ui.screens.admin.formulario.PantallaFormularioProducto
import com.megamarket.app.ui.screens.admin.panel.PantallaPanelAdmin
import com.megamarket.app.ui.screens.admin.productos.PantallaProductosAdmin
import com.megamarket.app.ui.screens.presentacion.PantallaPresentacion
import com.megamarket.app.ui.screens.sesion.PantallaSesion
import com.megamarket.app.viewmodel.AuthViewModel
import com.megamarket.app.viewmodel.ProductoFormViewModel
import com.megamarket.app.viewmodel.ProductoViewModel
import com.megamarket.app.viewmodel.SyncViewModel
import com.megamarket.app.viewmodel.ViewModelFactory

@Composable
fun NavegacionMegaMarket(
    controlador: NavHostController = rememberNavController()
) {
    val aplicacion = LocalContext.current.applicationContext as MegaMarketApplication
    val fabrica = remember(aplicacion) { ViewModelFactory(aplicacion.container) }

    NavHost(
        navController = controlador,
        startDestination = Ruta.Presentacion.ruta
    ) {
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
            val estado by viewModel.estado.collectAsStateWithLifecycle()

            LaunchedEffect(estado.usuario) {
                if (estado.usuario != null) {
                    controlador.navigate(Ruta.PanelAdmin.ruta) {
                        popUpTo(Ruta.Sesion.ruta) { inclusive = true }
                    }
                }
            }

            PantallaSesion(
                cargando = estado.cargando,
                error = estado.error,
                alIngresar = viewModel::ingresar
            )
        }

        composable(Ruta.PanelAdmin.ruta) {
            PantallaPanelAdmin(
                viewModel = viewModel<ProductoViewModel>(factory = fabrica),
                syncViewModel = viewModel<SyncViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.ProductosAdmin.ruta) {
            PantallaProductosAdmin(
                viewModel = viewModel<ProductoViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.CrearProductoAdmin.ruta) {
            PantallaFormularioProducto(
                viewModel = viewModel<ProductoFormViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(
            route = Ruta.EditarProductoAdmin.ruta,
            arguments = listOf(
                navArgument(ProductoFormViewModel.ARG_PRODUCTO_ID) { type = NavType.LongType }
            )
        ) {
            PantallaFormularioProducto(
                viewModel = viewModel<ProductoFormViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() }
            )
        }
    }
}

private fun NavHostController.cerrarSesion() {
    navigate(Ruta.Sesion.ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navegarAdmin(ruta: String) {
    val raicesAdmin = setOf(Ruta.PanelAdmin.ruta, Ruta.ProductosAdmin.ruta)
    if (ruta in raicesAdmin) {
        navigate(ruta) {
            popUpTo(Ruta.PanelAdmin.ruta) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(ruta)
    }
}
