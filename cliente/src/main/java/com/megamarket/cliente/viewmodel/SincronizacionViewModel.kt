package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.SincronizacionRepository
import com.megamarket.cliente.state.SincronizacionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SincronizacionViewModel(
    private val sincronizacionRepository: SincronizacionRepository
) : ViewModel() {

    private val sincronizando = MutableStateFlow(false)

    val estado: StateFlow<SincronizacionUiState> = combine(
        sincronizacionRepository.observarResumen(),
        sincronizando
    ) { resumen, enviando ->
        SincronizacionUiState(
            pendientes = resumen.pendientes,
            errores = resumen.errores,
            textoUltimaSincronizacion = formatear(resumen.ultimaExitosa),
            ultimoError = resumen.ultimoError,
            conectado = resumen.conectado,
            sincronizando = enviando
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SincronizacionUiState()
    )

    fun sincronizarPendientes() {
        if (sincronizando.value) return
        viewModelScope.launch {
            sincronizando.value = true
            try {
                sincronizacionRepository.sincronizarPendientes()
            } finally {
                sincronizando.value = false
            }
        }
    }

    private fun formatear(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Nunca"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
