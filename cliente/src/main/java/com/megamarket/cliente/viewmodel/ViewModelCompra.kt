package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repositorio.RepositorioCarrito
import com.megamarket.cliente.data.repositorio.RepositorioCompra
import com.megamarket.cliente.estado.EstadoUiCompra
import com.megamarket.cliente.modelo.Direccion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewModelCompra(
    private val carrito: RepositorioCarrito,
    private val compra: RepositorioCompra
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUiCompra())
    val estado: StateFlow<EstadoUiCompra> = _estado.asStateFlow()

    fun actualizarDepartamento(valor: String) = actualizar { it.copy(departamento = valor, error = null) }
    fun actualizarProvincia(valor: String) = actualizar { it.copy(provincia = valor, error = null) }
    fun actualizarDistrito(valor: String) = actualizar { it.copy(distrito = valor, error = null) }
    fun actualizarDireccion(valor: String) = actualizar { it.copy(direccion = valor, error = null) }
    fun actualizarTelefono(valor: String) = actualizar { it.copy(telefono = valor, error = null) }

    fun confirmar() {
        val actual = _estado.value
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
        viewModelScope.launch {
            val fallo = try {
                compra.confirmar(carrito.observar().first(), direccion)
            } catch (error: Exception) {
                error.message ?: "No se pudo confirmar la compra"
            }
            _estado.update {
                if (fallo == null) it.copy(error = null, confirmado = true) else it.copy(error = fallo)
            }
        }
    }

    private fun actualizar(cambio: (EstadoUiCompra) -> EstadoUiCompra) {
        _estado.update(cambio)
    }
}
