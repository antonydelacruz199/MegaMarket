package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioCarrito
import com.megamarket.cliente.data.repositorio.RepositorioCatalogo
import com.megamarket.cliente.data.repositorio.RepositorioFavoritos
import com.megamarket.cliente.estado.EstadoUiDetalle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ViewModelDetalle(
    savedStateHandle: SavedStateHandle,
    private val catalogo: RepositorioCatalogo,
    private val carrito: RepositorioCarrito,
    private val favoritos: RepositorioFavoritos
) : ViewModel() {

    private val productoId: Long = savedStateHandle.get<Long>(ARG_PRODUCTO)
        ?: savedStateHandle.get<String>(ARG_PRODUCTO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(EstadoUiDetalle())
    val estado: StateFlow<EstadoUiDetalle> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val producto = try {
                catalogo.obtenerPorId(productoId)
            } catch (error: Exception) {
                _estado.value = EstadoUiDetalle(
                    cargando = false,
                    error = error.message ?: "No se pudo abrir el producto"
                )
                return@launch
            }
            if (producto == null || !producto.activo) {
                _estado.value = EstadoUiDetalle(
                    cargando = false,
                    error = "Este producto no está disponible"
                )
                return@launch
            }
            combine(
                favoritos.observarEsFavorito(productoId),
                carrito.observar()
            ) { esFavorito, lineas ->
                val cantidad = lineas.firstOrNull { it.producto.id == productoId }?.cantidad ?: 0
                EstadoUiDetalle(
                    cargando = false,
                    producto = producto,
                    esFavorito = esFavorito,
                    cantidadEnCarrito = cantidad
                )
            }.collect { _estado.value = it }
        }
    }

    fun agregar() {
        viewModelScope.launch { carrito.agregar(productoId) }
    }

    fun cambiarCantidad(cantidad: Int) {
        viewModelScope.launch { carrito.cambiarCantidad(productoId, cantidad) }
    }

    fun alternarFavorito() {
        viewModelScope.launch { favoritos.alternar(productoId) }
    }

    companion object {
        const val ARG_PRODUCTO = "productoId"
    }
}
