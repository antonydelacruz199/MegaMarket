package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.model.estado.HomeUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val catalogo: CatalogoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(HomeUiState())
    val estado: StateFlow<HomeUiState> = _estado.asStateFlow()

    init {
        cargar()
        viewModelScope.launch {
            catalogo.observarCambiosCatalogo().collect { cargar() }
        }
    }

    fun cargar() {
        viewModelScope.launch {
            val actual = _estado.value
            if (!actual.hayProductos) {
                _estado.value = actual.copy(cargando = true, error = null)
            }
            _estado.value = try {
                val productos = catalogo.obtenerActivos()
                HomeUiState(
                    cargando = false,
                    ofertas = productos.filter { it.ofertaValida }.take(8),
                    hayProductos = productos.isNotEmpty()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actual.copy(
                    cargando = false,
                    error = if (actual.hayProductos) null else error.message ?: "No se pudo cargar el inicio"
                )
            }
        }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)
}
