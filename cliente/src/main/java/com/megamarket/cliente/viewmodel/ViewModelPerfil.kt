package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioSesion
import com.megamarket.cliente.estado.EstadoUiPerfil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModelPerfil(
    repositorio: RepositorioSesion
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiPerfil())
    val estado: StateFlow<EstadoUiPerfil> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.usuario.collect { usuario ->
                _estado.value = EstadoUiPerfil(
                    nombre = usuario?.nombre.orEmpty(),
                    correo = usuario?.correo.orEmpty(),
                    rol = "Cliente"
                )
            }
        }
    }
}
