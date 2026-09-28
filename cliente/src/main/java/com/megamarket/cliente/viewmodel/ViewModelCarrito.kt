package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioCarrito
import com.megamarket.cliente.estado.EstadoUiCarrito
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModelCarrito(
    private val carrito: RepositorioCarrito
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiCarrito())
    val estado: StateFlow<EstadoUiCarrito> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                carrito.observar().collect { lineas ->
                    val subtotal = lineas.sumOf { it.subtotalCentimos }
                    _estado.value = EstadoUiCarrito(
                        cargando = false,
                        lineas = lineas,
                        subtotalCentimos = subtotal,
                        totalCentimos = subtotal
                    )
                }
            } catch (error: Exception) {
                _estado.value = EstadoUiCarrito(
                    cargando = false,
                    error = error.message ?: "No se pudo leer el carrito"
                )
            }
        }
    }

    fun aumentar(productoId: Long, cantidadActual: Int) {
        viewModelScope.launch { carrito.cambiarCantidad(productoId, cantidadActual + 1) }
    }

    fun disminuir(productoId: Long, cantidadActual: Int) {
        if (cantidadActual <= 1) return
        viewModelScope.launch { carrito.cambiarCantidad(productoId, cantidadActual - 1) }
    }

    fun eliminar(productoId: Long) {
        viewModelScope.launch { carrito.eliminar(productoId) }
    }
}
