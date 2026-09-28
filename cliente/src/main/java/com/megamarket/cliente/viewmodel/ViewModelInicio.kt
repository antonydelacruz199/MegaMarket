package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioCatalogo
import com.megamarket.cliente.estado.EstadoUiInicio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModelInicio(
    private val catalogo: RepositorioCatalogo
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiInicio())
    val estado: StateFlow<EstadoUiInicio> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = EstadoUiInicio(cargando = true)
            _estado.value = try {
                val productos = catalogo.obtenerActivos()
                EstadoUiInicio(
                    cargando = false,
                    ofertas = productos.filter { it.ofertaValida }.take(8),
                    hayProductos = productos.isNotEmpty()
                )
            } catch (error: Exception) {
                EstadoUiInicio(
                    cargando = false,
                    error = error.message ?: "No se pudo cargar el inicio"
                )
            }
        }
    }
}
