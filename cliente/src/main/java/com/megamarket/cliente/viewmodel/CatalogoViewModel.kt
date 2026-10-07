package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.model.estado.CatalogoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogoViewModel(
    private val catalogo: CatalogoRepository,
    soloOfertas: Boolean
) : ViewModel() {

    private val _estado = MutableStateFlow(CatalogoUiState(soloOfertas = soloOfertas))
    val estado: StateFlow<CatalogoUiState> = _estado.asStateFlow()

    init {
        cargar()
        viewModelScope.launch {
            catalogo.observarCambiosCatalogo().collect { cargar() }
        }
    }

    fun cargar() {
        viewModelScope.launch {
            val habiaProductos = _estado.value.productos.isNotEmpty()
            if (!habiaProductos) {
                _estado.update { it.copy(cargando = true, error = null) }
            }
            try {
                val productos = catalogo.obtenerActivos()
                _estado.update { it.copy(cargando = false, productos = productos, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.update {
                    it.copy(
                        cargando = false,
                        productos = if (habiaProductos) it.productos else emptyList(),
                        error = if (habiaProductos) null else error.message ?: "No se pudo cargar el catálogo"
                    )
                }
            }
        }
    }

    fun actualizarConsulta(consulta: String) {
        _estado.update { it.copy(consulta = consulta) }
    }

    fun seleccionarCategoria(categoriaId: Long?) {
        _estado.update { it.copy(categoriaId = categoriaId) }
    }

    fun limpiarFiltros() {
        _estado.update { it.copy(consulta = "", categoriaId = null) }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)
}
