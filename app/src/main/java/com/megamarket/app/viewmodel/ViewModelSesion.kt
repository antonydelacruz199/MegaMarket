package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repositorio.RepositorioSesion
import com.megamarket.app.estado.EstadoUiSesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewModelSesion(
    private val repositorio: RepositorioSesion = RepositorioSesion()
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiSesion())
    val estado: StateFlow<EstadoUiSesion> = _estado.asStateFlow()

    fun ingresar(correo: String, clave: String) {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val usuario = repositorio.autenticar(correo, clave)
            _estado.update {
                if (usuario == null) {
                    it.copy(
                        cargando = false,
                        error = "Credenciales incorrectas",
                        usuario = null
                    )
                } else {
                    it.copy(cargando = false, error = null, usuario = usuario)
                }
            }
        }
    }

    fun cerrarSesion() {
        _estado.value = EstadoUiSesion()
    }
}
