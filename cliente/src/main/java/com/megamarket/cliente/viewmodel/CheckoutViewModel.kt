package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.AuthRepository
import com.megamarket.cliente.data.repository.CatalogoNoDisponibleException
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.model.CheckoutException
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.estado.CheckoutUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CheckoutViewModel(
    private val auth: AuthRepository,
    private val pedidos: PedidoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(CheckoutUiState())
    val estado: StateFlow<CheckoutUiState> = _estado.asStateFlow()

    fun actualizarDepartamento(valor: String) = _estado.update { it.copy(departamento = valor, error = null) }
    fun actualizarProvincia(valor: String) = _estado.update { it.copy(provincia = valor, error = null) }
    fun actualizarDistrito(valor: String) = _estado.update { it.copy(distrito = valor, error = null) }
    fun actualizarDireccion(valor: String) = _estado.update { it.copy(direccion = valor, error = null) }
    fun actualizarTelefono(valor: String) = _estado.update { it.copy(telefono = valor, error = null) }

    fun confirmarCompra() {
        val actual = _estado.value
        if (actual.cargando || actual.pedidoCreadoId != null) return
        val direccion = Direccion(
            departamento = actual.departamento.trim(),
            provincia = actual.provincia.trim(),
            distrito = actual.distrito.trim(),
            direccion = actual.direccion.trim(),
            telefono = actual.telefono.trim()
        )
        val errorDireccion = direccion.error()
        if (errorDireccion != null) {
            _estado.update { it.copy(error = errorDireccion) }
            return
        }
        val clienteId = auth.usuario.value?.id
        if (clienteId == null) {
            _estado.update { it.copy(error = "Tu sesión terminó. Vuelve a iniciar sesión.") }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedidoId = pedidos.confirmarPedido(clienteId, direccion)
                _estado.update { it.copy(cargando = false, pedidoCreadoId = pedidoId) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CheckoutException) {
                _estado.update { it.copy(cargando = false, error = error.message) }
            } catch (error: CatalogoNoDisponibleException) {
                _estado.update { it.copy(cargando = false, error = error.message) }
            } catch (error: Exception) {
                _estado.update { it.copy(cargando = false, error = "No se pudo registrar el pedido") }
            }
        }
    }

    /** La pantalla llama a esto después de navegar para no repetir la navegación. */
    fun consumirNavegacion() {
        _estado.update { it.copy(pedidoCreadoId = null) }
    }
}
