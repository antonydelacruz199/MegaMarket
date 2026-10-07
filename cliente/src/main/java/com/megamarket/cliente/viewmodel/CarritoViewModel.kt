package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.model.estado.CarritoUiState
import com.megamarket.cliente.model.totalCentimos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CarritoViewModel(
    private val carrito: CarritoRepository,
    private val catalogo: CatalogoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(CarritoUiState())
    val estado: StateFlow<CarritoUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            carrito.observar()
                .catch { error ->
                    _estado.value = CarritoUiState(
                        cargando = false,
                        error = error.message ?: "No se pudo leer el carrito"
                    )
                }
                .collect { lineas ->
                    val subtotal = lineas.totalCentimos()
                    _estado.value = CarritoUiState(
                        cargando = false,
                        lineas = lineas,
                        subtotalCentimos = subtotal,
                        totalCentimos = subtotal
                    )
                }
        }
    }

    fun aumentar(productoId: Long, cantidadActual: Int) {
        cambiar { carrito.cambiarCantidad(productoId, cantidadActual + 1) }
    }

    fun disminuir(productoId: Long, cantidadActual: Int) {
        if (cantidadActual <= 1) return
        cambiar { carrito.cambiarCantidad(productoId, cantidadActual - 1) }
    }

    fun eliminar(productoId: Long) {
        cambiar { carrito.eliminar(productoId) }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)

    private fun cambiar(accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.value = _estado.value.copy(error = error.message ?: "No se pudo actualizar el carrito")
            }
        }
    }
}
