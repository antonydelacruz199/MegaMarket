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
import com.megamarket.app.ui.screens.admin.billetera.PantallaBilleteraComercio
import com.megamarket.app.ui.screens.admin.compras.PantallaDetalleOrden
import com.megamarket.app.ui.screens.admin.compras.PantallaNuevaOrden
import com.megamarket.app.ui.screens.admin.compras.PantallaOrdenesCompra
import com.megamarket.app.ui.screens.admin.formulario.PantallaFormularioProducto
import com.megamarket.app.ui.screens.admin.panel.PantallaPanelAdmin
import com.megamarket.app.ui.screens.admin.productos.PantallaProductosAdmin
import com.megamarket.app.ui.screens.admin.proveedores.PantallaDetalleProveedor
import com.megamarket.app.ui.screens.admin.proveedores.PantallaFormularioProveedor
import com.megamarket.app.ui.screens.admin.proveedores.PantallaProveedores
import com.megamarket.app.ui.screens.presentacion.PantallaPresentacion
import com.megamarket.app.ui.screens.sesion.PantallaSesion
import com.megamarket.app.viewmodel.BilleteraComercioViewModel
import com.megamarket.app.viewmodel.FormularioProductoViewModel
import com.megamarket.app.viewmodel.OrdenesCompraViewModel
import com.megamarket.app.viewmodel.ProductoViewModel
import com.megamarket.app.viewmodel.ProveedoresViewModel
import com.megamarket.app.viewmodel.SesionViewModel
import com.megamarket.app.viewmodel.ViewModelFactory

@Composable
fun NavegacionMegaMarket(
    controlador: NavHostController = rememberNavController()
) {
    val aplicacion = LocalContext.current.applicationContext as MegaMarketApplication
    val fabrica = remember(aplicacion) { ViewModelFactory(aplicacion.container) }
    val sesionViewModel: SesionViewModel = viewModel(factory = fabrica)

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
            val estado by sesionViewModel.estado.collectAsStateWithLifecycle()

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
                alIngresar = sesionViewModel::iniciarSesion
            )
        }

        composable(Ruta.PanelAdmin.ruta) {
            PantallaPanelAdmin(
                viewModel = viewModel<ProductoViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.cerrarSesion()
                }
            )
        }

        composable(Ruta.ProductosAdmin.ruta) {
            PantallaProductosAdmin(
                viewModel = viewModel<ProductoViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.cerrarSesion()
                }
            )
        }

        composable(Ruta.BilleteraAdmin.ruta) {
            PantallaBilleteraComercio(
                viewModel = viewModel<BilleteraComercioViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.cerrarSesion()
                }
            )
        }

        composable(Ruta.ProveedoresAdmin.ruta) {
            PantallaProveedores(
                viewModel = viewModel<ProveedoresViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.cerrarSesion()
                },
                alDetalle = { id -> controlador.navigate(Ruta.DetalleProveedorAdmin.crear(id)) },
                alNuevo = { controlador.navigate(Ruta.NuevoProveedorAdmin.ruta) }
            )
        }

        composable(Ruta.NuevoProveedorAdmin.ruta) {
            PantallaFormularioProveedor(
                viewModel = viewModel<ProveedoresViewModel>(factory = fabrica),
                proveedorId = null,
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(
            route = Ruta.EditarProveedorAdmin.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PROVEEDOR_ID) { type = NavType.StringType })
        ) { entrada ->
            PantallaFormularioProveedor(
                viewModel = viewModel<ProveedoresViewModel>(factory = fabrica),
                proveedorId = entrada.arguments?.getString(Ruta.ARG_PROVEEDOR_ID).orEmpty(),
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(
            route = Ruta.DetalleProveedorAdmin.ruta,
            arguments = listOf(navArgument(Ruta.ARG_PROVEEDOR_ID) { type = NavType.StringType })
        ) { entrada ->
            PantallaDetalleProveedor(
                viewModel = viewModel<ProveedoresViewModel>(factory = fabrica),
                proveedorId = entrada.arguments?.getString(Ruta.ARG_PROVEEDOR_ID).orEmpty(),
                alVolver = { controlador.popBackStack() },
                alEditar = { id -> controlador.navigate(Ruta.EditarProveedorAdmin.crear(id)) },
                alNuevaOrden = { id -> controlador.navigate(Ruta.NuevaOrdenCompraAdmin.crear(id)) },
                alDetalleOrden = { id -> controlador.navigate(Ruta.DetalleOrdenCompraAdmin.crear(id)) }
            )
        }

        composable(Ruta.OrdenesCompraAdmin.ruta) {
            PantallaOrdenesCompra(
                viewModel = viewModel<OrdenesCompraViewModel>(factory = fabrica),
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                    controlador.cerrarSesion()
                },
                alDetalle = { id -> controlador.navigate(Ruta.DetalleOrdenCompraAdmin.crear(id)) },
                alNueva = { controlador.navigate(Ruta.NuevaOrdenCompraAdmin.crear()) }
            )
        }

        composable(
            route = Ruta.NuevaOrdenCompraAdmin.ruta,
            arguments = listOf(
                navArgument(Ruta.ARG_PROVEEDOR_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entrada ->
            PantallaNuevaOrden(
                viewModel = viewModel<OrdenesCompraViewModel>(factory = fabrica),
                proveedorIdInicial = entrada.arguments?.getString(Ruta.ARG_PROVEEDOR_ID),
                alVolver = { controlador.popBackStack() },
                alCreada = { id ->
                    controlador.navigate(Ruta.DetalleOrdenCompraAdmin.crear(id)) {
                        popUpTo(Ruta.NuevaOrdenCompraAdmin.ruta) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Ruta.DetalleOrdenCompraAdmin.ruta,
            arguments = listOf(navArgument(Ruta.ARG_ORDEN_ID) { type = NavType.StringType })
        ) { entrada ->
            PantallaDetalleOrden(
                viewModel = viewModel<OrdenesCompraViewModel>(factory = fabrica),
                ordenId = entrada.arguments?.getString(Ruta.ARG_ORDEN_ID).orEmpty(),
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(Ruta.CrearProductoAdmin.ruta) {
            PantallaFormularioProducto(
                viewModel = viewModel<FormularioProductoViewModel>(factory = fabrica),
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(
            route = Ruta.EditarProductoAdmin.ruta,
            arguments = listOf(
                navArgument(FormularioProductoViewModel.ARG_PRODUCTO_ID) { type = NavType.LongType }
            )
        ) {
            PantallaFormularioProducto(
                viewModel = viewModel<FormularioProductoViewModel>(factory = fabrica),
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
    val raicesAdmin = setOf(
        Ruta.PanelAdmin.ruta,
        Ruta.ProductosAdmin.ruta,
        Ruta.BilleteraAdmin.ruta,
        Ruta.ProveedoresAdmin.ruta,
        Ruta.OrdenesCompraAdmin.ruta
    )
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
