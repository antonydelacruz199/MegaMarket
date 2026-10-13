package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.state.InicioUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InicioViewModel(
    private val catalogo: CatalogoRepository
) : ViewModel() {

    private val bootstrapListo = MutableStateFlow(false)

    val estado: StateFlow<InicioUiState> = combine(
        catalogo.observarProductos(),
        bootstrapListo
    ) { productos, listo ->
        InicioUiState(
            cargando = !listo,
            ofertas = productos.filter { it.ofertaValida }.take(8),
            hayProductos = productos.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InicioUiState()
    )

    init {
        cargarInicio()
    }

    fun cargarInicio() {
        viewModelScope.launch {
            try {
                catalogo.asegurarCatalogoLocal()
                catalogo.importarDesdeProvider(silencioso = true)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
            } finally {
                bootstrapListo.value = true
            }
        }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)
}
