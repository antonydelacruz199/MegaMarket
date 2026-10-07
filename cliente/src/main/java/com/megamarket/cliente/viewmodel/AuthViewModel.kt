package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.AuthRepository
import com.megamarket.cliente.model.estado.AuthUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repositorio: AuthRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(AuthUiState(usuario = repositorio.usuario.value))
    val estado: StateFlow<AuthUiState> = _estado.asStateFlow()

    fun ingresar(nombreUsuario: String, clave: String) {
        if (nombreUsuario.isBlank() || clave.isBlank()) {
            _estado.update { it.copy(error = "Completa usuario y contraseña", usuario = null) }
            return
        }
        if (_estado.value.cargando) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val usuario = try {
                repositorio.autenticar(nombreUsuario, clave)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.update { it.copy(cargando = false, error = "No se pudo iniciar sesión") }
                return@launch
            }
            _estado.update {
                if (usuario == null) {
                    it.copy(cargando = false, error = "Credenciales incorrectas", usuario = null)
                } else {
                    it.copy(cargando = false, error = null, usuario = usuario)
                }
            }
        }
    }

    fun cerrarSesion() {
        repositorio.cerrar()
        _estado.value = AuthUiState()
    }
}
