package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioFavoritos
import com.megamarket.cliente.estado.EstadoUiFavoritos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModelFavoritos(
    private val favoritos: RepositorioFavoritos
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiFavoritos())
    val estado: StateFlow<EstadoUiFavoritos> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                favoritos.observar().collect { productos ->
                    _estado.value = EstadoUiFavoritos(cargando = false, productos = productos)
                }
            } catch (error: Exception) {
                _estado.value = EstadoUiFavoritos(
                    cargando = false,
                    error = error.message ?: "No se pudieron leer los favoritos"
                )
            }
        }
    }

    fun alternar(productoId: Long) {
        viewModelScope.launch { favoritos.alternar(productoId) }
    }
}
