package com.megamarket.picker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.RestauracionSesion
import com.megamarket.picker.data.repository.ResultadoInicioSesion
import com.megamarket.picker.data.repository.SesionRepository
import com.megamarket.picker.state.SesionUiState
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

    init {
        restaurar()
    }

    fun iniciarSesion(nombreUsuario: String, clave: String) {
        if (_estado.value.cargando || _estado.value.restaurando) return
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
        sesionRepository.cerrarSesion()
        _estado.value = SesionUiState(restaurando = false)
    }

    private fun restaurar() {
        viewModelScope.launch {
            val resultado = try {
                sesionRepository.restaurar()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.update {
                    it.copy(restaurando = false, cargando = false, usuario = null)
                }
                return@launch
            }
            _estado.update {
                when (resultado) {
                    is RestauracionSesion.Restaurada ->
                        it.copy(restaurando = false, cargando = false, error = null, usuario = resultado.usuario)
                    is RestauracionSesion.Ausente ->
                        it.copy(restaurando = false, cargando = false, usuario = null)
                    is RestauracionSesion.Invalida ->
                        it.copy(restaurando = false, cargando = false, usuario = null, error = resultado.mensaje)
                }
            }
        }
    }
}
