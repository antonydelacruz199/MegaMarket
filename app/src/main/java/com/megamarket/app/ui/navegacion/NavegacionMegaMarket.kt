package com.megamarket.app.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.megamarket.app.ui.pantallas.admin.formulario.PantallaFormularioProducto
import com.megamarket.app.ui.pantallas.admin.panel.PantallaPanelAdmin
import com.megamarket.app.ui.pantallas.admin.productos.PantallaProductosAdmin
import com.megamarket.app.ui.pantallas.presentacion.PantallaPresentacion
import com.megamarket.app.ui.pantallas.sesion.PantallaSesion
import com.megamarket.app.viewmodel.ViewModelSesion

@Composable
fun NavegacionMegaMarket(
    controlador: NavHostController = rememberNavController()
) {
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
            val viewModel: ViewModelSesion = viewModel()
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
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.ProductosAdmin.ruta) {
            PantallaProductosAdmin(
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.CrearProductoAdmin.ruta) {
            PantallaFormularioProducto(
                esEdicion = false,
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(Ruta.EditarProductoAdmin.ruta) {
            PantallaFormularioProducto(
                esEdicion = true,
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
