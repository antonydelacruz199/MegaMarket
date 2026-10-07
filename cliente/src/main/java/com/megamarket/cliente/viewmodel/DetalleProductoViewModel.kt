package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.model.estado.DetalleProductoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DetalleProductoViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalogo: CatalogoRepository,
    private val carrito: CarritoRepository,
    private val favoritos: FavoritosRepository
) : ViewModel() {

    private val productoId: Long = savedStateHandle.get<Long>(ARG_PRODUCTO)
        ?: savedStateHandle.get<String>(ARG_PRODUCTO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(DetalleProductoUiState())
    val estado: StateFlow<DetalleProductoUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val producto = try {
                catalogo.obtenerPorId(productoId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.value = DetalleProductoUiState(
                    cargando = false,
                    error = error.message ?: "No se pudo abrir el producto"
                )
                return@launch
            }
            if (producto == null || !producto.activo) {
                _estado.value = DetalleProductoUiState(
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
                DetalleProductoUiState(
                    cargando = false,
                    producto = producto,
                    esFavorito = esFavorito,
                    cantidadEnCarrito = cantidad
                )
            }
                .catch { error ->
                    _estado.value = DetalleProductoUiState(
                        cargando = false,
                        error = error.message ?: "No se pudo abrir el producto"
                    )
                }
                .collect { _estado.value = it }
        }
    }

    fun agregar() {
        viewModelScope.launch { ejecutar { carrito.agregar(productoId) } }
    }

    fun cambiarCantidad(cantidad: Int) {
        viewModelScope.launch { ejecutar { carrito.cambiarCantidad(productoId, cantidad) } }
    }

    fun alternarFavorito() {
        viewModelScope.launch { ejecutar { favoritos.alternar(productoId) } }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)

    private suspend fun ejecutar(accion: suspend () -> Unit) {
        try {
            accion()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _estado.value = _estado.value.copy(error = error.message ?: "No se pudo actualizar el producto")
        }
    }

    companion object {
        const val ARG_PRODUCTO = "productoId"
    }
}
