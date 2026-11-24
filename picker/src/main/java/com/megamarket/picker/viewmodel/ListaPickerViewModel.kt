package com.megamarket.picker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.state.ListaPickerUiState
import com.megamarket.picker.ui.navigation.SeccionPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListaPickerViewModel(
    private val repositorio: PedidosPickerRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaPickerUiState())
    val estado: StateFlow<ListaPickerUiState> = _estado.asStateFlow()
    private var carga: Job? = null

    fun mostrarSeccion(seccion: SeccionPicker) {
        val actual = _estado.value
        if (actual.seccion == seccion && (listaDe(seccion).isNotEmpty() || actual.cargando)) {
            return
        }
        carga?.cancel()
        _estado.update {
            it.copy(
                seccion = seccion,
                disponibles = emptyList(),
                asignados = emptyList(),
                despachables = emptyList(),
                pagina = 1,
                hayMas = false,
                cargando = true,
                refrescando = false,
                cargandoMas = false,
                error = null
            )
        }
        cargar(reiniciar = true)
    }

    fun cargar(reiniciar: Boolean = true) {
        if (carga?.isActive == true && !reiniciar) return
        carga?.cancel()
        carga = viewModelScope.launch {
            val seccion = _estado.value.seccion
            val hayDatos = listaDe(seccion).isNotEmpty()
            _estado.update {
                it.copy(
                    cargando = reiniciar && !hayDatos,
                    refrescando = reiniciar && hayDatos,
                    cargandoMas = false,
                    error = null
                )
            }
            cargarPagina(seccion, 1, anexar = false)
        }
    }

    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas || actual.cargando || actual.refrescando) {
            return
        }
        val seccion = actual.seccion
        val siguiente = actual.pagina + 1
        carga = viewModelScope.launch {
            _estado.update { it.copy(cargandoMas = true, error = null) }
            cargarPagina(seccion, siguiente, anexar = true)
        }
    }

    fun tomar(pedidoId: String) {
        if (_estado.value.tomandoId != null || _estado.value.despachandoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(tomandoId = pedidoId, error = null, codigoError = null) }
            when (val resultado = repositorio.tomar(pedidoId)) {
                is ResultadoOperacion.Exito -> {
                    _estado.update {
                        it.copy(
                            tomandoId = null,
                            pedidoTomadoId = pedidoId,
                            disponibles = it.disponibles.filterNot { p -> p.id == pedidoId }
                        )
                    }
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            tomandoId = null,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    if (resultado.tipo == TipoErrorPedido.CONFLICTO ||
                        resultado.tipo == TipoErrorPedido.PROHIBIDO
                    ) {
                        cargar()
                    }
                }
            }
        }
    }

    fun mandarADelivery(pedidoId: String) {
        if (_estado.value.despachandoId != null || _estado.value.tomandoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(despachandoId = pedidoId, error = null, codigoError = null) }
            when (val resultado = repositorio.mandarADelivery(pedidoId)) {
                is ResultadoOperacion.Exito -> {
                    _estado.update {
                        it.copy(
                            despachandoId = null,
                            despachables = it.despachables.filterNot { p -> p.id == pedidoId }
                        )
                    }
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            despachandoId = null,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    if (resultado.tipo == TipoErrorPedido.CONFLICTO ||
                        resultado.tipo == TipoErrorPedido.PROHIBIDO
                    ) {
                        cargar()
                    }
                }
            }
        }
    }

    fun consumirPedidoTomado() {
        _estado.update { it.copy(pedidoTomadoId = null) }
    }

    private fun listaDe(seccion: SeccionPicker): List<*> = when (seccion) {
        SeccionPicker.DISPONIBLES -> _estado.value.disponibles
        SeccionPicker.PREPARACION -> _estado.value.asignados
        SeccionPicker.DESPACHO -> _estado.value.despachables
    }

    private suspend fun cargarPagina(seccion: SeccionPicker, page: Int, anexar: Boolean) {
        when (seccion) {
            SeccionPicker.DISPONIBLES -> when (
                val r = safe { repositorio.cargarDisponibles(page = page) }
            ) {
                is ResultadoOperacion.Exito -> _estado.update {
                    if (it.seccion != seccion) return@update it
                    it.copy(
                        cargando = false,
                        refrescando = false,
                        cargandoMas = false,
                        disponibles = if (anexar) {
                            (it.disponibles + r.valor.items).distinctBy { p -> p.id }
                        } else {
                            r.valor.items
                        },
                        pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas,
                        error = null
                    )
                }
                is ResultadoOperacion.Error -> errorLista(r, seccion)
            }
            SeccionPicker.PREPARACION -> when (
                val r = safe { repositorio.cargarAsignados(page = page) }
            ) {
                is ResultadoOperacion.Exito -> _estado.update {
                    if (it.seccion != seccion) return@update it
                    it.copy(
                        cargando = false,
                        refrescando = false,
                        cargandoMas = false,
                        asignados = if (anexar) {
                            (it.asignados + r.valor.items).distinctBy { p -> p.id }
                        } else {
                            r.valor.items
                        },
                        pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas,
                        error = null
                    )
                }
                is ResultadoOperacion.Error -> errorLista(r, seccion)
            }
            SeccionPicker.DESPACHO -> when (
                val r = safe { repositorio.cargarDespachables(page = page) }
            ) {
                is ResultadoOperacion.Exito -> _estado.update {
                    if (it.seccion != seccion) return@update it
                    it.copy(
                        cargando = false,
                        refrescando = false,
                        cargandoMas = false,
                        despachables = if (anexar) {
                            (it.despachables + r.valor.items).distinctBy { p -> p.id }
                        } else {
                            r.valor.items
                        },
                        pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas,
                        error = null
                    )
                }
                is ResultadoOperacion.Error -> errorLista(r, seccion)
            }
        }
    }

    private suspend fun <T> safe(
        bloque: suspend () -> ResultadoOperacion<T>
    ): ResultadoOperacion<T> =
        try {
            bloque()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
        }

    private fun errorLista(resultado: ResultadoOperacion.Error, seccion: SeccionPicker) {
        _estado.update {
            if (it.seccion != seccion) return@update it
            it.copy(
                cargando = false,
                refrescando = false,
                cargandoMas = false,
                error = resultado.mensaje,
                codigoError = resultado.codigo,
                sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                    resultado.tipo == TipoErrorPedido.CUENTA
            )
        }
    }
}
