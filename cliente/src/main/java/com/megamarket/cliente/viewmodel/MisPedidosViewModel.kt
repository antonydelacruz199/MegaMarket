package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.MisPedidosRepository
import com.megamarket.cliente.data.repository.PaginaMisPedidos
import com.megamarket.cliente.data.repository.ResultadoMisPedidos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.MisPedidosUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MisPedidosViewModel(
    private val repositorio: MisPedidosRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(MisPedidosUiState())
    val estado: StateFlow<MisPedidosUiState> = _estado.asStateFlow()
    private var carga: Job? = null
    private var busqueda: Job? = null

    init {
        cargar(reiniciar = true)
    }

    fun seleccionarFiltro(filtro: FiltroMisPedidos) {
        if (_estado.value.filtro == filtro) return
        busqueda?.cancel()
        _estado.update {
            it.copy(
                filtro = filtro,
                pedidos = emptyList(),
                pagina = 1,
                hayMas = false,
                total = 0,
                cargandoMas = false
            )
        }
        cargar(reiniciar = true)
    }

    fun actualizarConsulta(consulta: String) {
        _estado.update { it.copy(consulta = consulta) }
        busqueda?.cancel()
        busqueda = viewModelScope.launch {
            delay(300)
            _estado.update {
                it.copy(
                    pedidos = emptyList(),
                    pagina = 1,
                    hayMas = false,
                    total = 0,
                    cargandoMas = false
                )
            }
            cargar(reiniciar = true)
        }
    }

    fun cargar(reiniciar: Boolean = true) {
        if (carga?.isActive == true && !reiniciar) return
        carga?.cancel()
        carga = viewModelScope.launch {
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
            aplicar(listarPagina(1), anexar = false)
        }
    }

    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas || actual.cargando || actual.refrescando) return
        val filtro = actual.filtro
        val consulta = actual.consulta
        val siguiente = actual.pagina + 1
        carga = viewModelScope.launch {
            _estado.update { it.copy(cargandoMas = true, error = null) }
            aplicar(
                listarPagina(siguiente, filtro = filtro, consulta = consulta),
                anexar = true
            )
        }
    }

    private suspend fun listarPagina(
        page: Int,
        filtro: FiltroMisPedidos = _estado.value.filtro,
        consulta: String = _estado.value.consulta
    ): ResultadoMisPedidos<PaginaMisPedidos> =
        try {
            repositorio.listar(
                filtro = filtro,
                page = page,
                q = consulta
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoMisPedidos.Error(TipoErrorSustitucion.RED, "Sin conexión")
        }

    private fun aplicar(resultado: ResultadoMisPedidos<PaginaMisPedidos>, anexar: Boolean) {
        _estado.update {
            when (resultado) {
                is ResultadoMisPedidos.Exito -> {
                    val pedidos = if (anexar) {
                        (it.pedidos + resultado.valor.pedidos).distinctBy { p -> p.id }
                    } else {
                        resultado.valor.pedidos
                    }
                    it.copy(
                        cargando = false,
                        refrescando = false,
                        cargandoMas = false,
                        pedidos = pedidos,
                        pagina = resultado.valor.pagina,
                        hayMas = resultado.valor.hayMas,
                        total = resultado.valor.total,
                        error = null,
                        codigoError = null
                    )
                }
                is ResultadoMisPedidos.Error -> it.copy(
                    cargando = false,
                    refrescando = false,
                    cargandoMas = false,
                    error = resultado.mensaje,
                    codigoError = resultado.codigo,
                    sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                        resultado.tipo == TipoErrorSustitucion.CUENTA
                )
            }
        }
    }
}
