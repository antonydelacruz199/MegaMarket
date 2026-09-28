package com.megamarket.app.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.megamarket.app.data.local.BaseDatosMegaMarket
import com.megamarket.app.data.repositorio.RepositorioProducto
import com.megamarket.app.data.repositorio.RepositorioSesion
import com.megamarket.app.ui.pantallas.admin.formulario.PantallaFormularioProducto
import com.megamarket.app.ui.pantallas.admin.panel.PantallaPanelAdmin
import com.megamarket.app.ui.pantallas.admin.productos.PantallaProductosAdmin
import com.megamarket.app.ui.pantallas.presentacion.PantallaPresentacion
import com.megamarket.app.ui.pantallas.sesion.PantallaSesion
import com.megamarket.app.viewmodel.ViewModelCatalogo
import com.megamarket.app.viewmodel.ViewModelSesion

@Composable
fun NavegacionMegaMarket(
    controlador: NavHostController = rememberNavController()
) {
    val contexto = LocalContext.current.applicationContext
    val fabricaCatalogo = remember(contexto) {
        FabricaCatalogo(
            RepositorioProducto(BaseDatosMegaMarket.obtener(contexto).productoDao())
        )
    }
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
            val contexto = LocalContext.current.applicationContext
            val fabrica = remember(contexto) {
                FabricaSesion(
                    RepositorioSesion(BaseDatosMegaMarket.obtener(contexto).administradorDao())
                )
            }
            val viewModel: ViewModelSesion = viewModel(factory = fabrica)
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
            val viewModel: ViewModelCatalogo = viewModel(factory = fabricaCatalogo)
            PantallaProductosAdmin(
                viewModel = viewModel,
                alNavegar = { ruta -> controlador.navegarAdmin(ruta) },
                alCerrarSesion = { controlador.cerrarSesion() }
            )
        }

        composable(Ruta.CrearProductoAdmin.ruta) {
            val viewModel: ViewModelCatalogo = viewModel(factory = fabricaCatalogo)
            PantallaFormularioProducto(
                esEdicion = false,
                alGuardar = { producto ->
                    viewModel.guardar(producto) { guardado ->
                        if (guardado) controlador.popBackStack()
                    }
                },
                alVolver = { controlador.popBackStack() }
            )
        }

        composable(Ruta.EditarProductoAdmin.ruta) {
            val viewModel: ViewModelCatalogo = viewModel(factory = fabricaCatalogo)
            PantallaFormularioProducto(
                esEdicion = true,
                alGuardar = { producto ->
                    viewModel.guardar(producto) { guardado ->
                        if (guardado) controlador.popBackStack()
                    }
                },
                alVolver = { controlador.popBackStack() }
            )
        }
    }
}

private class FabricaCatalogo(
    private val repositorio: RepositorioProducto
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(ViewModelCatalogo::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ViewModelCatalogo(repositorio) as T
        }
        throw IllegalArgumentException("ViewModel no soportado: ${modelClass.name}")
    }
}

private class FabricaSesion(
    private val repositorio: RepositorioSesion
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(ViewModelSesion::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ViewModelSesion(repositorio) as T
        }
        throw IllegalArgumentException("ViewModel no soportado: ${modelClass.name}")
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
