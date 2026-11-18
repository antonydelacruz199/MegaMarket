package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.MediosImagenRepository
import com.megamarket.cliente.state.CatalogoUiState
import com.megamarket.modelo.Producto
import com.megamarket.modelo.SolicitudImagenProducto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogoViewModel(
    private val catalogo: CatalogoRepository,
    soloOfertas: Boolean,
    private val medios: MediosImagenRepository
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
        cargarCatalogo()
    }

    fun cargarCatalogo() {
        viewModelScope.launch {
            try {
                // Observa Room; datos remotos llegan vía SesionRepository / SincronizacionRepository.
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

    fun solicitudImagen(producto: Producto): SolicitudImagenProducto? =
        medios.solicitud(producto.remoteId, producto.imagenKey)

    private data class FiltrosCatalogo(
        val consulta: String = "",
        val categoriaId: Long? = null,
        val soloOfertas: Boolean = false
    )
}
