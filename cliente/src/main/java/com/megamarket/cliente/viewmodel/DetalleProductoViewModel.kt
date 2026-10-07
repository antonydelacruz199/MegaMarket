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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    val estado: StateFlow<DetalleProductoUiState> = combine(
        catalogo.observarProducto(productoId),
        catalogo.observarCategorias(),
        favoritos.observarEsFavorito(productoId),
        carrito.observar()
    ) { producto, categorias, esFavorito, lineas ->
        if (producto == null || !producto.activo || producto.eliminadoRemotamente) {
            DetalleProductoUiState(
                cargando = false,
                error = "Este producto no está disponible"
            )
        } else {
            val cantidad = lineas.firstOrNull { it.producto.id == productoId }?.cantidad ?: 0
            DetalleProductoUiState(
                cargando = false,
                producto = producto,
                nombreCategoria = categorias.firstOrNull { it.id == producto.categoriaId }?.nombre
                    ?: "Categoría",
                esFavorito = esFavorito,
                cantidadEnCarrito = cantidad
            )
        }
    }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(
                DetalleProductoUiState(
                    cargando = false,
                    error = error.message ?: "No se pudo abrir el producto"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetalleProductoUiState()
        )

    init {
        viewModelScope.launch {
            try {
                catalogo.asegurarCatalogoLocal()
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
            }
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
        } catch (_: Exception) {
            // El Flow de estado refleja el catálogo; errores de carrito se omiten aquí
            // para no pisar el producto mostrado. CarritoRepository es silencioso en stock.
        }
    }

    companion object {
        const val ARG_PRODUCTO = "productoId"
    }
}
