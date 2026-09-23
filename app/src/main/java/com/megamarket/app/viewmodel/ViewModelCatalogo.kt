package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repositorio.RepositorioProducto
import com.megamarket.app.estado.EstadoUiCatalogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewModelCatalogo(
    private val repositorio: RepositorioProducto = RepositorioProducto()
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiCatalogo())
    val estado: StateFlow<EstadoUiCatalogo> = _estado.asStateFlow()

    init {
        cargarProductos()
    }

    fun actualizarConsulta(consulta: String) {
        _estado.update { it.copy(consulta = consulta) }
    }

    fun cargarProductos() {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val productos = repositorio.obtenerActivos()
            _estado.update { it.copy(cargando = false, productos = productos) }
        }
    }
}
