package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioCatalogo
import com.megamarket.cliente.estado.EstadoUiCatalogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewModelCatalogo(
    private val catalogo: RepositorioCatalogo,
    soloOfertas: Boolean
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiCatalogo(soloOfertas = soloOfertas))
    val estado: StateFlow<EstadoUiCatalogo> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            val habiaProductos = _estado.value.productos.isNotEmpty()
            if (!habiaProductos) {
                _estado.update { it.copy(cargando = true, error = null) }
            }
            _estado.update {
                try {
                    it.copy(cargando = false, productos = catalogo.obtenerActivos(), error = null)
                } catch (error: Exception) {
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
}
