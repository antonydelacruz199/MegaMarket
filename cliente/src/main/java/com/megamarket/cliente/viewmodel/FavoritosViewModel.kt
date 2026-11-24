package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.MediosImagenRepository
import com.megamarket.cliente.state.FavoritosUiState
import com.megamarket.cliente.state.TAMANO_PAGINA_LISTA
import com.megamarket.modelo.Producto
import com.megamarket.modelo.SolicitudImagenProducto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FavoritosViewModel(
    private val favoritos: FavoritosRepository,
    private val medios: MediosImagenRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(FavoritosUiState())
    val estado: StateFlow<FavoritosUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            favoritos.observarProductosFavoritos()
                .catch { error ->
                    _estado.value = FavoritosUiState(
                        cargando = false,
                        error = error.message ?: "No se pudieron leer los favoritos"
                    )
                }
                .collect { productos ->
                    _estado.update {
                        it.copy(
                            cargando = false,
                            productos = productos,
                            error = null
                        )
                    }
                }
        }
    }

    fun cargarMas() {
        _estado.update {
            if (!it.hayMas) it
            else it.copy(limiteVisible = it.limiteVisible + TAMANO_PAGINA_LISTA)
        }
    }

    fun alternarFavorito(productoId: Long) {
        viewModelScope.launch {
            try {
                favoritos.alternarFavorito(productoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.update { it.copy(error = "No se pudo actualizar el favorito") }
            }
        }
    }

    fun solicitudImagen(producto: Producto): SolicitudImagenProducto? =
        medios.solicitud(producto.remoteId, producto.imagenKey)
}
