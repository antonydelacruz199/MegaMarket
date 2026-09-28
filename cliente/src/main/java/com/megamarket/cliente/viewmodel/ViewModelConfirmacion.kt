package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import com.megamarket.cliente.data.repositorio.RepositorioCompra
import com.megamarket.cliente.estado.EstadoUiConfirmacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewModelConfirmacion(
    compra: RepositorioCompra
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoUiConfirmacion(resumen = compra.ultimo()))
    val estado: StateFlow<EstadoUiConfirmacion> = _estado.asStateFlow()
}
