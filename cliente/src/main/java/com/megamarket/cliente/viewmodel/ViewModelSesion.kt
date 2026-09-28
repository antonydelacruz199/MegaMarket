package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioSesion
import com.megamarket.cliente.estado.EstadoUiSesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewModelSesion(
    private val repositorio: RepositorioSesion
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiSesion(usuario = repositorio.usuario.value))
    val estado: StateFlow<EstadoUiSesion> = _estado.asStateFlow()

    fun ingresar(nombreUsuario: String, clave: String) {
        if (nombreUsuario.isBlank() || clave.isBlank()) {
            _estado.update { it.copy(error = "Completa usuario y contraseña", usuario = null) }
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val usuario = repositorio.autenticar(nombreUsuario, clave)
            _estado.update {
                if (usuario == null) {
                    it.copy(cargando = false, error = "Credenciales incorrectas", usuario = null)
                } else {
                    it.copy(cargando = false, error = null, usuario = usuario)
                }
            }
        }
    }
}
