package com.megamarket.cliente.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.model.estado.CatalogoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogoViewModel(
    private val catalogo: CatalogoRepository,
    soloOfertas: Boolean
) : ViewModel() {

    private val filtros = MutableStateFlow(
        FiltrosCatalogo(soloOfertas = soloOfertas)
    )
    private val bootstrapListo = MutableStateFlow(false)

    val estado: StateFlow<CatalogoUiState> = combine(
        catalogo.observarProductos(),
        catalogo.observarCategorias(),
        filtros,
        bootstrapListo
    ) { productos, categorias, filtro, listo ->
        CatalogoUiState(
            cargando = !listo,
            productos = productos,
            categorias = categorias,
            consulta = filtro.consulta,
            categoriaId = filtro.categoriaId,
            soloOfertas = filtro.soloOfertas,
            error = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogoUiState(soloOfertas = soloOfertas)
    )

    init {
        cargar()
    }

    fun cargar() {
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

    fun actualizarConsulta(consulta: String) {
        filtros.value = filtros.value.copy(consulta = consulta)
    }

    fun seleccionarCategoria(categoriaId: Long?) {
        filtros.value = filtros.value.copy(categoriaId = categoriaId)
    }

    fun limpiarFiltros() {
        filtros.value = filtros.value.copy(consulta = "", categoriaId = null)
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? =
        catalogo.cargarImagen(productoId, lado)

    private data class FiltrosCatalogo(
        val consulta: String = "",
        val categoriaId: Long? = null,
        val soloOfertas: Boolean = false
    )
}
