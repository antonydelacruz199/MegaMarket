package com.megamarket.picker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.megamarket.picker.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val modelo: ViewModel = when {
            modelClass.isAssignableFrom(SesionViewModel::class.java) ->
                SesionViewModel(container.sesionRepository)
            modelClass.isAssignableFrom(ListaPickerViewModel::class.java) ->
                ListaPickerViewModel(container.pedidosRepository)
            modelClass.isAssignableFrom(ListaRepartidorViewModel::class.java) ->
                ListaRepartidorViewModel(container.repartoRepository)
            modelClass.isAssignableFrom(DetalleEntregaViewModel::class.java) ->
                DetalleEntregaViewModel(
                    extras.createSavedStateHandle(),
                    container.repartoRepository
                )
            modelClass.isAssignableFrom(DetallePreparacionViewModel::class.java) ->
                DetallePreparacionViewModel(
                    extras.createSavedStateHandle(),
                    container.pedidosRepository
                )
            modelClass.isAssignableFrom(EscanerViewModel::class.java) ->
                EscanerViewModel(
                    extras.createSavedStateHandle(),
                    container.pedidosRepository
                )
            modelClass.isAssignableFrom(SustitucionViewModel::class.java) ->
                SustitucionViewModel(
                    extras.createSavedStateHandle(),
                    container.sustitucionesRepository,
                    container.pedidosRepository
                )
            else -> throw IllegalArgumentException("ViewModel no soportado: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return modelo as T
    }
}
