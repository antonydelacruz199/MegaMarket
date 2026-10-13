package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.SesionRepository
import com.megamarket.app.state.SesionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SesionViewModel(
    private val sesionRepository: SesionRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(SesionUiState())
    val estado: StateFlow<SesionUiState> = _estado.asStateFlow()

    fun iniciarSesion(nombreUsuario: String, clave: String) {
        if (_estado.value.cargando) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val usuario = try {
                sesionRepository.iniciarSesion(nombreUsuario, clave)
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
        viewModelScope.launch {
            sesionRepository.cerrarSesion()
            _estado.value = SesionUiState()
        }
    }
}
