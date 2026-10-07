package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.megamarket.cliente.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer,
    private val soloOfertas: Boolean = false
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val modelo: ViewModel = when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(container.authRepository)
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(container.catalogoRepository)
            modelClass.isAssignableFrom(CatalogoViewModel::class.java) ->
                CatalogoViewModel(container.catalogoRepository, soloOfertas)
            modelClass.isAssignableFrom(DetalleProductoViewModel::class.java) ->
                DetalleProductoViewModel(
                    extras.createSavedStateHandle(),
                    container.catalogoRepository,
                    container.carritoRepository,
                    container.favoritosRepository
                )
            modelClass.isAssignableFrom(FavoritosViewModel::class.java) ->
                FavoritosViewModel(container.favoritosRepository, container.catalogoRepository)
            modelClass.isAssignableFrom(CarritoViewModel::class.java) ->
                CarritoViewModel(container.carritoRepository, container.catalogoRepository)
            modelClass.isAssignableFrom(CheckoutViewModel::class.java) ->
                CheckoutViewModel(container.authRepository, container.pedidoRepository)
            modelClass.isAssignableFrom(ConfirmacionViewModel::class.java) ->
                ConfirmacionViewModel(extras.createSavedStateHandle(), container.pedidoRepository)
            modelClass.isAssignableFrom(PerfilViewModel::class.java) ->
                PerfilViewModel(container.authRepository)
            modelClass.isAssignableFrom(SyncViewModel::class.java) ->
                SyncViewModel(
                    container.appContext,
                    container.syncRepository,
                    container.connectivityObserver
                )
            else -> throw IllegalArgumentException("ViewModel no registrado: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return modelo as T
    }
}
