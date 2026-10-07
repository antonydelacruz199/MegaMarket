package com.megamarket.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository
import com.megamarket.app.model.FiltroProducto
import com.megamarket.app.model.estado.ProductoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductoViewModel(
    private val productos: ProductoRepository,
    private val imagenes: ImagenRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(ProductoUiState())
    val estado: StateFlow<ProductoUiState> = _estado.asStateFlow()

    init {
        cargarProductos()
    }

    fun cargarProductos() {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            try {
                val lista = productos.obtenerTodos()
                _estado.update { it.copy(cargando = false, productos = lista) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.update { it.copy(cargando = false, error = "No se pudo cargar el catálogo") }
            }
        }
    }

    fun actualizarConsulta(consulta: String) {
        _estado.update { it.copy(consulta = consulta) }
    }

    fun seleccionarFiltro(filtro: FiltroProducto) {
        _estado.update { it.copy(filtro = filtro) }
    }

    suspend fun cargarImagen(clave: String, lado: Int): Bitmap? = imagenes.cargar(clave, lado)
}
