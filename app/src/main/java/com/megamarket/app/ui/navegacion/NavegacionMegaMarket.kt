package com.megamarket.app.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.megamarket.app.ui.pantallas.admin.formulario.PantallaFormularioProducto
import com.megamarket.app.ui.pantallas.admin.panel.PantallaPanelAdmin
import com.megamarket.app.ui.pantallas.admin.productos.PantallaProductosAdmin
import com.megamarket.app.ui.pantallas.carrito.PantallaCarrito
import com.megamarket.app.ui.pantallas.catalogo.PantallaCatalogo
import com.megamarket.app.ui.pantallas.compra.PantallaCompra
import com.megamarket.app.ui.pantallas.confirmacion.PantallaConfirmacion
import com.megamarket.app.ui.pantallas.favoritos.PantallaFavoritos
import com.megamarket.app.ui.pantallas.inicio.PantallaInicio
import com.megamarket.app.ui.pantallas.perfil.PantallaPerfil
import com.megamarket.app.ui.pantallas.presentacion.PantallaPresentacion
import com.megamarket.app.ui.pantallas.producto.PantallaDetalleProducto
import com.megamarket.app.ui.pantallas.sesion.PantallaSesion

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
            PantallaSesion(
                alIngresar = {
                    // Temporal: solo navegación de UI. Sin validación ni base de datos.
                    // TODO: ViewModel de sesión decidirá Inicio o PanelAdmin según el rol.
                    controlador.navigate(Ruta.Inicio.ruta) {
                        popUpTo(Ruta.Sesion.ruta) { inclusive = true }
                    }
                }
            )
        }

        composable(Ruta.Inicio.ruta) {
            PantallaInicio(
                alNavegar = { ruta -> controlador.navegarCliente(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.Catalogo.ruta) {
            PantallaCatalogo(
                alNavegar = { ruta -> controlador.navegarCliente(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.DetalleProducto.ruta) {
            PantallaDetalleProducto(alVolver = { controlador.popBackStack() })
        }

        composable(Ruta.Favoritos.ruta) {
            PantallaFavoritos(
                alNavegar = { ruta -> controlador.navegarCliente(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.Carrito.ruta) {
            PantallaCarrito(
                alNavegar = { ruta ->
                    when (ruta) {
                        Ruta.Compra.ruta -> controlador.navigate(Ruta.Compra.ruta)
                        else -> controlador.navegarCliente(ruta)
                    }
                },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.Compra.ruta) {
            PantallaCompra(
                alVolver = { controlador.popBackStack() },
                alConfirmarCompra = {
                    controlador.navigate(Ruta.Confirmacion.ruta)
                }
            )
        }

        composable(Ruta.Confirmacion.ruta) {
            PantallaConfirmacion(
                alVolverAlInicio = {
                    controlador.navigate(Ruta.Inicio.ruta) {
                        popUpTo(Ruta.Inicio.ruta) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Ruta.Perfil.ruta) {
            PantallaPerfil(
                alNavegar = { ruta -> controlador.navegarCliente(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
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

private fun NavHostController.navegarCliente(ruta: String) {
    if (ruta in DestinosInferioresCliente.rutas) {
        navigate(ruta) {
            popUpTo(Ruta.Inicio.ruta) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(ruta)
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
