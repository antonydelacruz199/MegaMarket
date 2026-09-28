package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.megamarket.cliente.ContenedorCliente

class FabricaCliente(
    private val contenedor: ContenedorCliente,
    private val soloOfertas: Boolean = false
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val estadoGuardado: SavedStateHandle = extras.createSavedStateHandle()
        val modelo: ViewModel = when {
            modelClass.isAssignableFrom(ViewModelSesion::class.java) ->
                ViewModelSesion(contenedor.repositorioSesion)
            modelClass.isAssignableFrom(ViewModelInicio::class.java) ->
                ViewModelInicio(contenedor.repositorioCatalogo)
            modelClass.isAssignableFrom(ViewModelCatalogo::class.java) ->
                ViewModelCatalogo(contenedor.repositorioCatalogo, soloOfertas)
            modelClass.isAssignableFrom(ViewModelDetalle::class.java) ->
                ViewModelDetalle(
                    estadoGuardado,
                    contenedor.repositorioCatalogo,
                    contenedor.repositorioCarrito,
                    contenedor.repositorioFavoritos
                )
            modelClass.isAssignableFrom(ViewModelFavoritos::class.java) ->
                ViewModelFavoritos(contenedor.repositorioFavoritos)
            modelClass.isAssignableFrom(ViewModelCarrito::class.java) ->
                ViewModelCarrito(contenedor.repositorioCarrito)
            modelClass.isAssignableFrom(ViewModelCompra::class.java) ->
                ViewModelCompra(contenedor.repositorioCarrito, contenedor.repositorioCompra)
            modelClass.isAssignableFrom(ViewModelConfirmacion::class.java) ->
                ViewModelConfirmacion(contenedor.repositorioCompra)
            modelClass.isAssignableFrom(ViewModelPerfil::class.java) ->
                ViewModelPerfil(contenedor.repositorioSesion)
            else -> throw IllegalArgumentException("ViewModel no registrado")
        }
        @Suppress("UNCHECKED_CAST")
        return modelo as T
    }
}
