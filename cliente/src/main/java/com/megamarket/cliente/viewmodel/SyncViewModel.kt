package com.megamarket.cliente.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.connectivity.ConnectivityObserver
import com.megamarket.cliente.data.connectivity.EstadoConectividad
import com.megamarket.cliente.data.repository.SyncRepository
import com.megamarket.cliente.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncUiState(
    val pendientes: Int = 0,
    val errores: Int = 0,
    val ultimaSyncTexto: String = "Nunca",
    val ultimoError: String? = null,
    val conectado: Boolean = false,
    val sincronizando: Boolean = false
)

class SyncViewModel(
    private val appContext: Context,
    private val syncRepository: SyncRepository,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val sincronizando = MutableStateFlow(false)

    val estado: StateFlow<SyncUiState> = combine(
        syncRepository.observarResumen(),
        connectivityObserver.observar(),
        sincronizando
    ) { resumen, red, enviando ->
        SyncUiState(
            pendientes = resumen.pendientes,
            errores = resumen.errores,
            ultimaSyncTexto = formatear(resumen.ultimaExitosa),
            ultimoError = resumen.ultimoError,
            conectado = red == EstadoConectividad.CONNECTED,
            sincronizando = enviando
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SyncUiState()
    )

    fun sincronizarAhora() {
        if (sincronizando.value) return
        viewModelScope.launch {
            sincronizando.value = true
            try {
                SyncWorker.sincronizarAhora(appContext)
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
