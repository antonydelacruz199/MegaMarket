package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.AuthRepository
import com.megamarket.app.model.estado.AuthUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repositorio: AuthRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(AuthUiState())
    val estado: StateFlow<AuthUiState> = _estado.asStateFlow()

    fun ingresar(nombreUsuario: String, clave: String) {
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
}
