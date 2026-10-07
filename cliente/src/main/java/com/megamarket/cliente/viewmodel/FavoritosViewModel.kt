package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.model.estado.FavoritosUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FavoritosViewModel(
    private val favoritos: FavoritosRepository,
    private val catalogo: CatalogoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(FavoritosUiState())
    val estado: StateFlow<FavoritosUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            favoritos.observar()
                .catch { error ->
                    _estado.value = FavoritosUiState(
                        cargando = false,
                        error = error.message ?: "No se pudieron leer los favoritos"
                    )
                }
                .collect { productos ->
                    _estado.value = FavoritosUiState(cargando = false, productos = productos)
                }
        }
    }

    fun alternar(productoId: Long) {
        viewModelScope.launch {
            try {
                favoritos.alternar(productoId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.value = _estado.value.copy(error = "No se pudo actualizar el favorito")
            }
        }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)
}
