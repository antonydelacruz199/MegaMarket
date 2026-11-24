package com.megamarket.picker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PaginaReparto
import com.megamarket.picker.data.repository.PedidosRepartoRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.state.ListaRepartidorUiState
import com.megamarket.picker.ui.navigation.SeccionReparto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListaRepartidorViewModel(
    private val repositorio: PedidosRepartoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaRepartidorUiState())
    val estado: StateFlow<ListaRepartidorUiState> = _estado.asStateFlow()
    private var carga: Job? = null

    fun mostrarSeccion(seccion: SeccionReparto) {
        val actual = _estado.value
        if (actual.seccion == seccion && (actual.pedidos.isNotEmpty() || actual.cargando)) {
            return
        }
        carga?.cancel()
        _estado.update {
            it.copy(
                seccion = seccion,
                pedidos = emptyList(),
                pagina = 1,
                hayMas = false,
                total = 0,
                cargando = true,
                refrescando = false,
                cargandoMas = false,
                error = null,
                codigoError = null
            )
        }
        cargar(reiniciar = true)
    }

    fun cargar(reiniciar: Boolean = true) {
        if (carga?.isActive == true && !reiniciar) return
        carga?.cancel()
        carga = viewModelScope.launch {
            val seccion = _estado.value.seccion
            val hayDatos = _estado.value.pedidos.isNotEmpty()
            _estado.update {
                it.copy(
                    cargando = reiniciar && !hayDatos,
                    refrescando = reiniciar && hayDatos,
                    cargandoMas = false,
                    error = null,
                    codigoError = null
                )
            }
            aplicar(
                resultado = cargarPagina(seccion, 1),
                anexar = false,
                seccion = seccion
            )
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
            aplicar(
                resultado = cargarPagina(seccion, siguiente),
                anexar = true,
                seccion = seccion
            )
        }
    }

    fun consumirPedidoTomado() {
        _estado.update { it.copy(pedidoTomadoId = null) }
    }

    fun tomar(pedidoId: String) {
        if (_estado.value.tomandoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(tomandoId = pedidoId, error = null, codigoError = null) }
            when (val resultado = repositorio.tomar(pedidoId)) {
                is ResultadoOperacion.Exito -> {
                    _estado.update { actual ->
                        actual.copy(
                            tomandoId = null,
                            pedidoTomadoId = pedidoId,
                            pedidos = actual.pedidos.filterNot { it.id == pedidoId }
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
                        cargar(reiniciar = true)
                    }
                }
            }
        }
    }

    private suspend fun cargarPagina(
        seccion: SeccionReparto,
        page: Int
    ): ResultadoOperacion<PaginaReparto> =
        try {
            repositorio.listarPorEstado(seccion.estadoApi, page = page)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
        }

    private fun aplicar(
        resultado: ResultadoOperacion<PaginaReparto>,
        anexar: Boolean,
        seccion: SeccionReparto
    ) {
        when (resultado) {
            is ResultadoOperacion.Exito -> _estado.update { actual ->
                if (actual.seccion != seccion) return@update actual
                val pedidos = if (anexar) {
                    actual.pedidos + resultado.valor.pedidos
                } else {
                    resultado.valor.pedidos
                }
                actual.copy(
                    cargando = false,
                    refrescando = false,
                    cargandoMas = false,
                    pedidos = pedidos.distinctBy { it.id },
                    pagina = resultado.valor.pagina,
                    hayMas = resultado.valor.hayMas,
                    total = resultado.valor.total,
                    error = null,
                    codigoError = null,
                    sesionVencida = false
                )
            }
            is ResultadoOperacion.Error -> _estado.update {
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
}
