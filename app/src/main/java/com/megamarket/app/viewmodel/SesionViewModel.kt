package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.ResultadoInicioSesion
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
            val resultado = try {
                sesionRepository.iniciarSesion(nombreUsuario, clave)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.update { it.copy(cargando = false, error = "No se pudo iniciar sesión") }
                return@launch
            }
            _estado.update {
                when (resultado) {
                    is ResultadoInicioSesion.Exito ->
                        it.copy(cargando = false, error = null, usuario = resultado.usuario)
                    is ResultadoInicioSesion.Error ->
                        it.copy(cargando = false, error = resultado.mensaje, usuario = null)
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
