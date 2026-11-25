package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.DatosProveedor
import com.megamarket.app.data.repository.ErroresProveedor
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.data.repository.ProveedoresRepository
import com.megamarket.app.data.repository.ResultadoAbastecimiento
import com.megamarket.app.data.repository.validarDatosProveedor
import com.megamarket.app.state.FiltroProveedor
import com.megamarket.app.state.FormularioProveedorUiState
import com.megamarket.app.state.ProveedoresUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lista/búsqueda, detalle y formulario (alta/edición) de proveedores. Cada pantalla usa su propia instancia. */
class ProveedoresViewModel(
    private val repositorio: ProveedoresRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(ProveedoresUiState())
    val estado: StateFlow<ProveedoresUiState> = _estado.asStateFlow()

    private var trabajoLista: Job? = null
    private var trabajoBusqueda: Job? = null
    private var idFormularioIniciado: String? = null
    private var formularioIniciado = false

    // ---------- Lista ----------

    fun cargar() {
        cargarPagina(reiniciar = true)
    }

    fun cargarMas() {
        cargarPagina(reiniciar = false)
    }

    fun actualizarConsulta(texto: String) {
        _estado.update { it.copy(consulta = texto) }
        trabajoBusqueda?.cancel()
        trabajoBusqueda = viewModelScope.launch {
            delay(RETARDO_BUSQUEDA_MS)
            cargarPagina(reiniciar = true)
        }
    }

    fun seleccionarFiltro(filtro: FiltroProveedor) {
        if (_estado.value.filtro == filtro) return
        _estado.update { it.copy(filtro = filtro) }
        cargarPagina(reiniciar = true)
    }

    private fun cargarPagina(reiniciar: Boolean) {
        val actual = _estado.value
        if (!reiniciar && (!actual.hayMas || actual.cargandoMas || actual.cargando)) return
        if (reiniciar) {
            trabajoLista?.cancel()
            _estado.update { it.copy(cargando = true, cargandoMas = false, error = null) }
        } else {
            _estado.update { it.copy(cargandoMas = true, error = null) }
        }
        val pagina = if (reiniciar) 1 else actual.pagina + 1
        trabajoLista = viewModelScope.launch {
            val resultado = repositorio.listar(
                consulta = actual.consulta,
                activo = actual.filtro.activo,
                page = pagina
            )
            when (resultado) {
                is ResultadoAbastecimiento.Ok -> _estado.update {
                    val nuevos = resultado.valor.items
                    it.copy(
                        cargando = false,
                        cargandoMas = false,
                        proveedores = if (reiniciar) nuevos else (it.proveedores + nuevos).distinctBy { p -> p.id },
                        pagina = resultado.valor.pagina,
                        hayMas = resultado.valor.hayMas,
                        total = resultado.valor.total,
                        error = null
                    )
                }
                is ResultadoAbastecimiento.Error -> _estado.update {
                    it.copy(cargando = false, cargandoMas = false, error = resultado.mensaje)
                }
            }
        }
    }

    // ---------- Detalle ----------

    fun cargarDetalle(id: String) {
        if (_estado.value.cargandoDetalle) return
        _estado.update { it.copy(cargandoDetalle = true, errorDetalle = null, errorOrdenesProveedor = null) }
        viewModelScope.launch {
            when (val proveedor = repositorio.obtener(id)) {
                is ResultadoAbastecimiento.Ok -> {
                    _estado.update { it.copy(detalle = proveedor.valor, errorDetalle = null) }
                    when (val ordenes = repositorio.ordenesDeProveedor(id)) {
                        is ResultadoAbastecimiento.Ok -> _estado.update {
                            it.copy(cargandoDetalle = false, ordenesProveedor = ordenes.valor.items)
                        }
                        is ResultadoAbastecimiento.Error -> _estado.update {
                            it.copy(cargandoDetalle = false, errorOrdenesProveedor = ordenes.mensaje)
                        }
                    }
                }
                is ResultadoAbastecimiento.Error -> _estado.update {
                    it.copy(cargandoDetalle = false, errorDetalle = proveedor.mensaje)
                }
            }
        }
    }

    // ---------- Formulario ----------

    /** Idempotente por id: recomposiciones o rotaciones no reinician lo que el usuario escribió. */
    fun iniciarFormulario(proveedorId: String?) {
        if (formularioIniciado && idFormularioIniciado == proveedorId) return
        formularioIniciado = true
        idFormularioIniciado = proveedorId
        if (proveedorId == null) {
            _estado.update { it.copy(formulario = FormularioProveedorUiState()) }
            return
        }
        _estado.update {
            it.copy(formulario = FormularioProveedorUiState(proveedorId = proveedorId, cargando = true))
        }
        viewModelScope.launch {
            when (val resultado = repositorio.obtener(proveedorId)) {
                is ResultadoAbastecimiento.Ok -> {
                    val p = resultado.valor
                    _estado.update {
                        it.copy(
                            formulario = FormularioProveedorUiState(
                                proveedorId = p.id,
                                ruc = p.ruc,
                                razonSocial = p.razonSocial,
                                telefono = p.telefono.orEmpty(),
                                correo = p.correo.orEmpty(),
                                cuentaBancaria = p.cuentaBancaria.orEmpty(),
                                activo = p.activo
                            )
                        )
                    }
                }
                is ResultadoAbastecimiento.Error -> _estado.update {
                    it.copy(
                        formulario = it.formulario.copy(
                            cargando = false,
                            noEncontrado = true,
                            error = resultado.mensaje
                        )
                    )
                }
            }
        }
    }

    fun actualizarRuc(valor: String) = editar { it.copy(ruc = valor.filter(Char::isDigit).take(11), errores = it.errores.copy(ruc = null)) }
    fun actualizarRazonSocial(valor: String) = editar { it.copy(razonSocial = valor, errores = it.errores.copy(razonSocial = null)) }
    fun actualizarTelefono(valor: String) = editar { it.copy(telefono = valor) }
    fun actualizarCorreo(valor: String) = editar { it.copy(correo = valor, errores = it.errores.copy(correo = null)) }
    fun actualizarCuentaBancaria(valor: String) = editar { it.copy(cuentaBancaria = valor) }
    fun actualizarActivo(valor: Boolean) = editar { it.copy(activo = valor) }

    private fun editar(transformar: (FormularioProveedorUiState) -> FormularioProveedorUiState) {
        _estado.update { it.copy(formulario = transformar(it.formulario)) }
    }

    fun consumirErrorFormulario() = editar { it.copy(error = null) }

    fun guardar() {
        val form = _estado.value.formulario
        if (form.guardando || form.cargando || form.guardado) return
        val datos = DatosProveedor(
            ruc = form.ruc,
            razonSocial = form.razonSocial,
            telefono = form.telefono,
            correo = form.correo,
            cuentaBancaria = form.cuentaBancaria,
            activo = form.activo
        )
        val errores: ErroresProveedor = validarDatosProveedor(datos)
        if (errores.hayErrores) {
            editar { it.copy(errores = errores) }
            return
        }
        editar { it.copy(guardando = true, error = null, errores = ErroresProveedor()) }
        viewModelScope.launch {
            val resultado: ResultadoAbastecimiento<Proveedor> =
                form.proveedorId?.let { repositorio.actualizar(it, datos) } ?: repositorio.crear(datos)
            when (resultado) {
                is ResultadoAbastecimiento.Ok -> editar { it.copy(guardando = false, guardado = true) }
                is ResultadoAbastecimiento.Error -> editar { it.copy(guardando = false, error = resultado.mensaje) }
            }
        }
    }

    private companion object {
        const val RETARDO_BUSQUEDA_MS = 350L
    }
}
