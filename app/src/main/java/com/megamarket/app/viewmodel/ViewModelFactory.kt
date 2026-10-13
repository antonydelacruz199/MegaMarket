package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.megamarket.app.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val modelo: ViewModel = when {
            modelClass.isAssignableFrom(SesionViewModel::class.java) ->
                SesionViewModel(container.sesionRepository)
            modelClass.isAssignableFrom(ProductoViewModel::class.java) ->
                ProductoViewModel(container.productoRepository, container.imagenRepository)
            modelClass.isAssignableFrom(FormularioProductoViewModel::class.java) ->
                FormularioProductoViewModel(
                    extras.createSavedStateHandle(),
                    container.productoRepository,
                    container.categoriaRepository,
                    container.imagenRepository
                )
            modelClass.isAssignableFrom(SincronizacionViewModel::class.java) ->
                SincronizacionViewModel(container.sincronizacionRepository)
            else -> throw IllegalArgumentException("ViewModel no soportado: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return modelo as T
    }
}
