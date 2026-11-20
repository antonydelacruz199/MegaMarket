package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoNoDisponibleException
import com.megamarket.cliente.data.repository.FranjasRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.ResultadoFranjas
import com.megamarket.cliente.data.repository.SesionRepository
import com.megamarket.cliente.model.CheckoutException
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.ModalidadPagoCheckout
import com.megamarket.cliente.state.CheckoutUiState
import com.megamarket.cliente.state.PasoCheckout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CheckoutViewModel(
    private val sesionRepository: SesionRepository,
    private val pedidos: PedidoRepository,
    private val franjasRepository: FranjasRepository,
    private val carrito: CarritoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(CheckoutUiState())
    val estado: StateFlow<CheckoutUiState> = _estado.asStateFlow()

    init {
        observarCarrito()
        cargarFranjas()
    }

    fun actualizarDepartamento(valor: String) = _estado.update { it.copy(departamento = valor, error = null) }
    fun actualizarProvincia(valor: String) = _estado.update { it.copy(provincia = valor, error = null) }
    fun actualizarDistrito(valor: String) = _estado.update { it.copy(distrito = valor, error = null) }
    fun actualizarDireccion(valor: String) = _estado.update { it.copy(direccion = valor, error = null) }
    fun actualizarTelefono(valor: String) = _estado.update { it.copy(telefono = valor, error = null) }

    fun seleccionarFecha(fechaIso: String) {
        _estado.update { actual ->
            val sigueValida = actual.franjaSeleccionada?.fechaIso == fechaIso
            actual.copy(
                fechaSeleccionada = fechaIso,
                franjaSeleccionadaId = if (sigueValida) actual.franjaSeleccionadaId else null,
                error = null
            )
        }
    }

    fun seleccionarFranja(franjaId: String) {
        val franja = _estado.value.franjas.firstOrNull { it.id == franjaId } ?: return
        if (!franja.disponible) {
            _estado.update {
                it.copy(error = "Esa franja está agotada. Elige otra con cupo disponible.")
            }
            return
        }
        _estado.update {
            it.copy(
                franjaSeleccionadaId = franja.id,
                fechaSeleccionada = franja.fechaIso,
                error = null
            )
        }
    }

    fun seleccionarModalidad(modalidad: ModalidadPagoCheckout) {
        _estado.update { it.copy(modalidadPago = modalidad, error = null) }
    }

    fun aumentarCantidad(productoId: Long, cantidadActual: Int) {
        aplicarCambioCarrito { carrito.actualizarCantidadCarrito(productoId, cantidadActual + 1) }
    }

    fun disminuirCantidad(productoId: Long, cantidadActual: Int) {
        if (cantidadActual <= 1) return
        aplicarCambioCarrito { carrito.actualizarCantidadCarrito(productoId, cantidadActual - 1) }
    }

    fun quitarProducto(productoId: Long) {
        aplicarCambioCarrito { carrito.quitarDelCarrito(productoId) }
    }

    fun cargarFranjas() {
        if (_estado.value.cargandoFranjas) return
        _estado.update { it.copy(cargandoFranjas = true, errorFranjas = null) }
        viewModelScope.launch {
            when (val resultado = franjasRepository.listar()) {
                is ResultadoFranjas.Exito -> {
                    val franjas = resultado.valor
                    val fechas = franjas.map { it.fechaIso }.distinct()
                    _estado.update { actual ->
                        val fecha = actual.fechaSeleccionada?.takeIf { it in fechas } ?: fechas.firstOrNull()
                        val seleccion = actual.franjaSeleccionadaId
                            ?.takeIf { id -> franjas.any { it.id == id && it.disponible } }
                        actual.copy(
                            cargandoFranjas = false,
                            franjas = franjas,
                            fechasDisponibles = fechas,
                            fechaSeleccionada = fecha,
                            franjaSeleccionadaId = seleccion,
                            errorFranjas = if (franjas.isEmpty()) {
                                "No hay franjas disponibles para hoy o mañana."
                            } else null
                        )
                    }
                }
                is ResultadoFranjas.Error -> {
                    _estado.update {
                        it.copy(
                            cargandoFranjas = false,
                            errorFranjas = resultado.mensaje,
                            franjas = emptyList(),
                            fechasDisponibles = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun irAPaso(paso: PasoCheckout) {
        val actual = _estado.value
        when (paso) {
            PasoCheckout.DIRECCION -> _estado.update { it.copy(paso = paso, error = null) }
            PasoCheckout.FRANJA -> {
                validarDireccion(actual)?.let { error ->
                    _estado.update { it.copy(error = error) }
                    return
                }
                _estado.update { it.copy(paso = paso, error = null) }
                if (actual.franjas.isEmpty() && !actual.cargandoFranjas) cargarFranjas()
            }
            PasoCheckout.PAGO -> {
                validarDireccion(actual)?.let { error ->
                    _estado.update { it.copy(error = error) }
                    return
                }
                if (actual.franjaSeleccionada == null || actual.franjaSeleccionada?.disponible != true) {
                    _estado.update { it.copy(error = "Selecciona una franja con cupo disponible.") }
                    return
                }
                _estado.update { it.copy(paso = paso, error = null) }
            }
            PasoCheckout.RESUMEN -> {
                validarDireccion(actual)?.let { error ->
                    _estado.update { it.copy(error = error) }
                    return
                }
                if (actual.franjaSeleccionada?.disponible != true) {
                    _estado.update { it.copy(error = "Selecciona una franja con cupo disponible.") }
                    return
                }
                if (actual.modalidadPago == null) {
                    _estado.update { it.copy(error = "Elige cómo quieres pagar.") }
                    return
                }
                if (actual.lineasCarrito.isEmpty()) {
                    _estado.update { it.copy(error = "Agrega al menos un producto para continuar.") }
                    return
                }
                _estado.update { it.copy(paso = paso, error = null) }
            }
        }
    }

    fun volverPaso() {
        val anterior = when (_estado.value.paso) {
            PasoCheckout.DIRECCION -> return
            PasoCheckout.FRANJA -> PasoCheckout.DIRECCION
            PasoCheckout.PAGO -> PasoCheckout.FRANJA
            PasoCheckout.RESUMEN -> PasoCheckout.PAGO
        }
        _estado.update { it.copy(paso = anterior, error = null) }
    }

    fun confirmarCompra() {
        val actual = _estado.value
        if (actual.cargando || actual.pedidoCreadoId != null) return
        validarDireccion(actual)?.let { error ->
            _estado.update { it.copy(error = error, paso = PasoCheckout.DIRECCION) }
            return
        }
        val franja = actual.franjaSeleccionada
        if (franja == null || !franja.disponible) {
            _estado.update {
                it.copy(error = "Selecciona una franja con cupo disponible.", paso = PasoCheckout.FRANJA)
            }
            return
        }
        val modalidad = actual.modalidadPago
        if (modalidad == null) {
            _estado.update { it.copy(error = "Elige cómo quieres pagar.", paso = PasoCheckout.PAGO) }
            return
        }
        if (actual.lineasCarrito.isEmpty()) {
            _estado.update { it.copy(error = "Agrega al menos un producto para confirmar.") }
            return
        }
        val clienteId = sesionRepository.usuario.value?.id
        if (clienteId == null) {
            _estado.update { it.copy(error = "Tu sesión terminó. Vuelve a iniciar sesión.") }
            return
        }
        val direccion = Direccion(
            departamento = actual.departamento.trim(),
            provincia = actual.provincia.trim(),
            distrito = actual.distrito.trim(),
            direccion = actual.direccion.trim(),
            telefono = actual.telefono.trim()
        )
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedidoId = pedidos.confirmarPedido(clienteId, direccion, franja, modalidad)
                _estado.update { it.copy(cargando = false, pedidoCreadoId = pedidoId) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CheckoutException) {
                _estado.update { it.copy(cargando = false, error = error.message) }
            } catch (error: CatalogoNoDisponibleException) {
                _estado.update { it.copy(cargando = false, error = error.message) }
            } catch (_: Exception) {
                _estado.update { it.copy(cargando = false, error = "No se pudo registrar el pedido") }
            }
        }
    }

    fun consumirNavegacion() {
        _estado.update { it.copy(pedidoCreadoId = null) }
    }

    private fun observarCarrito() {
        viewModelScope.launch {
            carrito.observarLineasCarrito()
                .catch { error ->
                    _estado.update {
                        it.copy(error = error.message ?: "No se pudo leer el carrito")
                    }
                }
                .collect { lineas ->
                    _estado.update { it.copy(lineasCarrito = lineas) }
                }
        }
    }

    private fun aplicarCambioCarrito(accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
                _estado.update { it.copy(error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.update {
                    it.copy(error = error.message ?: "No se pudo actualizar el carrito")
                }
            }
        }
    }

    private fun validarDireccion(estado: CheckoutUiState): String? {
        return Direccion(
            departamento = estado.departamento.trim(),
            provincia = estado.provincia.trim(),
            distrito = estado.distrito.trim(),
            direccion = estado.direccion.trim(),
            telefono = estado.telefono.trim()
        ).error()
    }
}
