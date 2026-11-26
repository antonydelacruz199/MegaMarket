package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.ComprasRepository
import com.megamarket.app.data.repository.DetalleOrden
import com.megamarket.app.data.repository.EstadoOrden
import com.megamarket.app.data.repository.EstadoTransferencia
import com.megamarket.app.data.repository.ItemRecepcion
import com.megamarket.app.data.repository.OrdenCompra
import com.megamarket.app.data.repository.ProductoOpcion
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.data.repository.ProveedoresRepository
import com.megamarket.app.data.repository.ResultadoAbastecimiento
import com.megamarket.app.state.AccionOrden
import com.megamarket.app.state.DetalleOrdenUiState
import com.megamarket.app.state.LineaOrdenEditable
import com.megamarket.app.state.NuevaOrdenUiState
import com.megamarket.app.state.OrdenesCompraUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Resultado de validar las cantidades capturadas en el formulario de recepción. */
data class ValidacionRecepcion(
    val items: List<ItemRecepcion>,
    val error: String?
)

/**
 * Convierte las cantidades de texto en ítems de recepción. Reglas: enteros ≥ 0, nunca más que lo pendiente
 * y al menos un ítem > 0. Las líneas en 0 o vacías no se envían.
 */
fun validarRecepcion(detalles: List<DetalleOrden>, cantidades: Map<String, String>): ValidacionRecepcion {
    val items = mutableListOf<ItemRecepcion>()
    for (detalle in detalles) {
        val texto = cantidades[detalle.id].orEmpty().trim()
        if (texto.isEmpty()) continue
        val cantidad = texto.toIntOrNull()
            ?: return ValidacionRecepcion(emptyList(), "Cantidad inválida para ${detalle.nombreProducto}.")
        if (cantidad < 0) {
            return ValidacionRecepcion(emptyList(), "Cantidad inválida para ${detalle.nombreProducto}.")
        }
        if (cantidad > detalle.cantidadPendiente) {
            return ValidacionRecepcion(
                emptyList(),
                "${detalle.nombreProducto}: no puedes recibir más de lo pendiente (${detalle.cantidadPendiente})."
            )
        }
        if (cantidad > 0) items += ItemRecepcion(detalle.id, cantidad)
    }
    if (items.isEmpty()) {
        return ValidacionRecepcion(emptyList(), "Indica al menos una cantidad a recibir.")
    }
    return ValidacionRecepcion(items, null)
}

/**
 * Órdenes de compra: lista/filtro, alta, detalle y acciones críticas (aprobar, cancelar, pagar, recibir).
 * Las acciones críticas generan un uuidOperacion que se conserva mientras el resultado sea incierto
 * (red caída, PENDIENTE, DESCONOCIDA) para poder reintentar sin duplicar el efecto en el servidor.
 */
class OrdenesCompraViewModel(
    private val compras: ComprasRepository,
    private val proveedores: ProveedoresRepository,
    private val generarUuid: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val _estado = MutableStateFlow(OrdenesCompraUiState())
    val estado: StateFlow<OrdenesCompraUiState> = _estado.asStateFlow()

    private var trabajoLista: Job? = null
    private var trabajoProveedores: Job? = null
    private var trabajoProductos: Job? = null
    private var nuevaIniciada = false
    private var ordenDetalleId: String? = null

    // uuid de operaciones críticas, conservados mientras el resultado sea incierto
    private var uuidAprobacion: String? = null
    private var uuidPago: String? = null
    private var uuidRecepcion: String? = null
    private var firmaRecepcion: String? = null

    // ---------- Lista ----------

    fun cargar() = cargarPagina(reiniciar = true)

    fun cargarMas() = cargarPagina(reiniciar = false)

    fun seleccionarFiltro(estado: EstadoOrden?) {
        if (_estado.value.filtroEstado == estado) return
        _estado.update { it.copy(filtroEstado = estado) }
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
            when (val r = compras.listarOrdenes(estado = actual.filtroEstado, page = pagina)) {
                is ResultadoAbastecimiento.Ok -> _estado.update {
                    it.copy(
                        cargando = false,
                        cargandoMas = false,
                        ordenes = if (reiniciar) r.valor.items else (it.ordenes + r.valor.items).distinctBy { o -> o.id },
                        pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas,
                        total = r.valor.total,
                        error = null
                    )
                }
                is ResultadoAbastecimiento.Error -> _estado.update {
                    it.copy(cargando = false, cargandoMas = false, error = r.mensaje)
                }
            }
        }
    }

    // ---------- Nueva orden ----------

    /** Idempotente: no reinicia lo capturado ante recomposiciones. */
    fun iniciarNuevaOrden(proveedorIdInicial: String? = null) {
        if (nuevaIniciada) return
        nuevaIniciada = true
        _estado.update { it.copy(nueva = NuevaOrdenUiState(proveedorId = proveedorIdInicial)) }
        buscarProveedores("")
        if (proveedorIdInicial != null) {
            viewModelScope.launch {
                val r = proveedores.obtener(proveedorIdInicial)
                if (r is ResultadoAbastecimiento.Ok) seleccionarProveedor(r.valor)
            }
        }
    }

    fun buscarProveedores(texto: String) {
        editarNueva { it.copy(consultaProveedor = texto, cargandoProveedores = true, errorProveedores = null) }
        trabajoProveedores?.cancel()
        trabajoProveedores = viewModelScope.launch {
            if (texto.isNotBlank()) delay(RETARDO_BUSQUEDA_MS)
            when (val r = proveedores.listar(consulta = texto, activo = true, limit = 20)) {
                is ResultadoAbastecimiento.Ok -> editarNueva {
                    it.copy(proveedores = r.valor.items, cargandoProveedores = false)
                }
                is ResultadoAbastecimiento.Error -> editarNueva {
                    it.copy(cargandoProveedores = false, errorProveedores = r.mensaje)
                }
            }
        }
    }

    fun seleccionarProveedor(proveedor: Proveedor) {
        editarNueva { it.copy(proveedorSeleccionado = proveedor, proveedorId = proveedor.id, error = null) }
    }

    fun buscarProductos(texto: String) {
        editarNueva { it.copy(consultaProducto = texto, buscandoProductos = true, errorProductos = null) }
        trabajoProductos?.cancel()
        trabajoProductos = viewModelScope.launch {
            if (texto.isNotBlank()) delay(RETARDO_BUSQUEDA_MS)
            when (val r = compras.buscarProductos(consulta = texto, limit = 20)) {
                is ResultadoAbastecimiento.Ok -> editarNueva {
                    it.copy(productos = r.valor.items, buscandoProductos = false)
                }
                is ResultadoAbastecimiento.Error -> editarNueva {
                    it.copy(buscandoProductos = false, errorProductos = r.mensaje)
                }
            }
        }
    }

    fun agregarProducto(producto: ProductoOpcion) {
        editarNueva {
            if (it.lineas.any { l -> l.producto.id == producto.id }) it
            else it.copy(lineas = it.lineas + LineaOrdenEditable(producto), error = null)
        }
    }

    fun quitarLinea(productoId: String) {
        editarNueva { it.copy(lineas = it.lineas.filterNot { l -> l.producto.id == productoId }) }
    }

    fun actualizarCantidad(productoId: String, texto: String) {
        val limpio = texto.filter(Char::isDigit).take(7)
        editarNueva { n ->
            n.copy(lineas = n.lineas.map { if (it.producto.id == productoId) it.copy(cantidadTexto = limpio) else it })
        }
    }

    fun actualizarCosto(productoId: String, texto: String) {
        val limpio = texto.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
        editarNueva { n ->
            n.copy(lineas = n.lineas.map { if (it.producto.id == productoId) it.copy(costoTexto = limpio) else it })
        }
    }

    fun consumirErrorNueva() = editarNueva { it.copy(error = null) }

    fun crearOrden() {
        val nueva = _estado.value.nueva
        if (nueva.guardando || nueva.ordenCreadaId != null) return
        nueva.errorValidacion?.let { mensaje ->
            editarNueva { it.copy(error = mensaje) }
            return
        }
        val proveedorId = nueva.proveedorSeleccionado?.id ?: return
        val lineas = nueva.lineas.mapNotNull { it.aLinea() }
        editarNueva { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            when (val r = compras.crearOrden(proveedorId, lineas)) {
                is ResultadoAbastecimiento.Ok ->
                    editarNueva { it.copy(guardando = false, ordenCreadaId = r.valor.id) }
                is ResultadoAbastecimiento.Error -> editarNueva { it.copy(guardando = false, error = r.mensaje) }
            }
        }
    }

    private fun editarNueva(transformar: (NuevaOrdenUiState) -> NuevaOrdenUiState) {
        _estado.update { it.copy(nueva = transformar(it.nueva)) }
    }

    // ---------- Detalle ----------

    fun cargarDetalle(id: String) {
        if (_estado.value.detalle.cargando || _estado.value.detalle.procesando) return
        if (ordenDetalleId != id) {
            ordenDetalleId = id
            uuidAprobacion = null
            uuidPago = null
            uuidRecepcion = null
            firmaRecepcion = null
            _estado.update { it.copy(detalle = DetalleOrdenUiState()) }
        }
        editarDetalle { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = compras.obtenerOrden(id)) {
                is ResultadoAbastecimiento.Ok -> {
                    aplicarOrden(r.valor)
                    cargarRecepciones(r.valor)
                    editarDetalle { it.copy(cargando = false, error = null) }
                }
                is ResultadoAbastecimiento.Error -> editarDetalle { it.copy(cargando = false, error = r.mensaje) }
            }
        }
    }

    private suspend fun cargarRecepciones(orden: OrdenCompra) {
        if (orden.estado != EstadoOrden.PAGADA && orden.estado != EstadoOrden.RECIBIDA) {
            editarDetalle { it.copy(recepciones = emptyList()) }
            return
        }
        val r = compras.historialRecepciones(orden.id)
        if (r is ResultadoAbastecimiento.Ok) {
            editarDetalle { it.copy(recepciones = r.valor) }
        }
    }

    private fun aplicarOrden(orden: OrdenCompra) {
        val transferencia = orden.transferencia
        when {
            orden.estado == EstadoOrden.PAGADA || orden.estado == EstadoOrden.RECIBIDA -> uuidPago = null
            // Una transferencia abierta de una sesión anterior obliga a reutilizar su uuid.
            transferencia != null && transferencia.estado.esAbierta -> uuidPago = transferencia.uuidOperacion
        }
        editarDetalle { d ->
            // Conserva lo que el usuario ya escribió mientras siga siendo válido; si no, propone lo pendiente.
            val cantidades = orden.detalles.associate { det ->
                val previa = d.cantidadesRecepcion[det.id]
                    ?.takeIf { it.isNotEmpty() && (it.toIntOrNull() ?: Int.MAX_VALUE) <= det.cantidadPendiente }
                det.id to (previa ?: det.cantidadPendiente.toString())
            }
            d.copy(
                orden = orden,
                cantidadesRecepcion = cantidades,
                estadoPago = transferencia?.estado ?: d.estadoPago,
                mensajePago = transferencia?.mensaje ?: d.mensajePago
            )
        }
    }

    fun consumirMensajeDetalle() = editarDetalle { it.copy(mensaje = null) }

    fun aprobar() {
        val orden = _estado.value.detalle.orden ?: return
        if (_estado.value.detalle.procesando || !orden.puedeAprobar) return
        val uuid = uuidAprobacion ?: generarUuid().also { uuidAprobacion = it }
        editarDetalle { it.copy(accion = AccionOrden.APROBAR, mensaje = null) }
        viewModelScope.launch {
            when (val r = compras.aprobar(orden.id, uuid)) {
                is ResultadoAbastecimiento.Ok -> {
                    uuidAprobacion = null
                    aplicarOrden(r.valor)
                    editarDetalle { it.copy(accion = null, mensaje = "Orden aprobada") }
                }
                is ResultadoAbastecimiento.Error -> editarDetalle { it.copy(accion = null, mensaje = r.mensaje) }
            }
        }
    }

    fun cancelar() {
        val orden = _estado.value.detalle.orden ?: return
        if (_estado.value.detalle.procesando || !orden.puedeCancelar) return
        editarDetalle { it.copy(accion = AccionOrden.CANCELAR, mensaje = null) }
        viewModelScope.launch {
            when (val r = compras.cancelar(orden.id)) {
                is ResultadoAbastecimiento.Ok -> {
                    uuidAprobacion = null
                    uuidPago = null
                    aplicarOrden(r.valor)
                    editarDetalle { it.copy(accion = null, mensaje = "Orden cancelada") }
                }
                is ResultadoAbastecimiento.Error -> editarDetalle { it.copy(accion = null, mensaje = r.mensaje) }
            }
        }
    }

    /**
     * Pago a proveedor. El uuid se conserva si la transferencia queda PENDIENTE/DESCONOCIDA
     * o si falla la red; se descarta solo al CONFIRMARSE o RECHAZARSE (en ese caso un nuevo intento
     * necesita un uuid nuevo).
     */
    fun pagar() {
        val orden = _estado.value.detalle.orden ?: return
        if (_estado.value.detalle.procesando || !orden.puedePagar) return
        val uuid = uuidPago ?: generarUuid().also { uuidPago = it }
        editarDetalle { it.copy(accion = AccionOrden.PAGAR, mensaje = null) }
        viewModelScope.launch {
            when (val r = compras.pagar(orden.id, uuid, orden.totalCentimos)) {
                is ResultadoAbastecimiento.Ok -> {
                    val respuesta = r.valor
                    val estadoT = respuesta.transferencia?.estado ?: EstadoTransferencia.DESCONOCIDA
                    when {
                        respuesta.confirmada || estadoT == EstadoTransferencia.RECHAZADA -> uuidPago = null
                        else -> uuidPago = respuesta.transferencia?.uuidOperacion ?: uuid
                    }
                    respuesta.orden?.let { aplicarOrden(it) }
                    editarDetalle {
                        it.copy(
                            accion = null,
                            estadoPago = estadoT,
                            avisoPago = respuesta.aviso,
                            mensajePago = respuesta.mensajeServidor,
                            mensaje = when {
                                respuesta.confirmada -> "Pago confirmado"
                                estadoT == EstadoTransferencia.RECHAZADA -> "La transferencia fue rechazada"
                                estadoT == EstadoTransferencia.PENDIENTE -> "Transferencia pendiente"
                                else -> "Resultado desconocido: no reintentes sin consultar"
                            }
                        )
                    }
                    if (respuesta.confirmada) respuesta.orden?.let { cargarRecepciones(it) }
                }
                is ResultadoAbastecimiento.Error -> {
                    r.uuidOperacionExistente?.let { uuidPago = it }
                    editarDetalle { it.copy(accion = null, mensaje = r.mensaje) }
                }
            }
        }
    }

    /** Consulta (sin reenviar el pago) el estado de la transferencia con el uuid conservado. */
    fun consultarTransferencia() {
        val d = _estado.value.detalle
        val orden = d.orden ?: return
        if (d.procesando) return
        val uuid = uuidPago ?: orden.transferencia?.uuidOperacion
        if (uuid == null) {
            editarDetalle { it.copy(mensaje = "No hay una transferencia que consultar.") }
            return
        }
        editarDetalle { it.copy(accion = AccionOrden.CONSULTAR_TRANSFERENCIA, mensaje = null) }
        viewModelScope.launch {
            when (val r = compras.consultarTransferencia(uuid)) {
                is ResultadoAbastecimiento.Ok -> {
                    val t = r.valor
                    uuidPago = if (t.estado.esAbierta) t.uuidOperacion else null
                    editarDetalle {
                        it.copy(accion = null, estadoPago = t.estado, mensajePago = t.mensaje, mensaje = "Estado: ${t.estado.etiqueta}")
                    }
                    // Refresca la orden (por si el pago se confirmó) sin pisar el mensaje de pago.
                    when (val o = compras.obtenerOrden(orden.id)) {
                        is ResultadoAbastecimiento.Ok -> {
                            aplicarOrden(o.valor)
                            cargarRecepciones(o.valor)
                        }
                        is ResultadoAbastecimiento.Error -> Unit
                    }
                }
                is ResultadoAbastecimiento.Error -> editarDetalle { it.copy(accion = null, mensaje = r.mensaje) }
            }
        }
    }

    fun actualizarCantidadRecepcion(detalleId: String, texto: String) {
        val limpio = texto.filter(Char::isDigit).take(7)
        editarDetalle { it.copy(cantidadesRecepcion = it.cantidadesRecepcion + (detalleId to limpio), errorRecepcion = null) }
    }

    /** Valida el formulario de recepción; devuelve true si se puede abrir el diálogo de confirmación. */
    fun validarFormularioRecepcion(): Boolean {
        val d = _estado.value.detalle
        val orden = d.orden ?: return false
        if (d.procesando || !orden.puedeRecibir) return false
        val validacion = validarRecepcion(orden.detalles, d.cantidadesRecepcion)
        editarDetalle { it.copy(errorRecepcion = validacion.error) }
        return validacion.error == null
    }

    fun recibir() {
        val d = _estado.value.detalle
        val orden = d.orden ?: return
        if (d.procesando || !orden.puedeRecibir) return
        val validacion = validarRecepcion(orden.detalles, d.cantidadesRecepcion)
        if (validacion.error != null) {
            editarDetalle { it.copy(errorRecepcion = validacion.error) }
            return
        }
        // El mismo uuid solo se reutiliza si el contenido no cambió (la respuesta idempotente es por uuid).
        val firma = validacion.items.sortedBy { it.detalleId }.joinToString("|") { "${it.detalleId}:${it.cantidad}" }
        val uuid = if (firma == firmaRecepcion && uuidRecepcion != null) {
            uuidRecepcion!!
        } else {
            generarUuid().also { uuidRecepcion = it; firmaRecepcion = firma }
        }
        editarDetalle { it.copy(accion = AccionOrden.RECIBIR, mensaje = null, errorRecepcion = null) }
        viewModelScope.launch {
            when (val r = compras.recibir(orden.id, uuid, validacion.items)) {
                is ResultadoAbastecimiento.Ok -> {
                    uuidRecepcion = null
                    firmaRecepcion = null
                    // Reinicia el formulario con lo pendiente real.
                    editarDetalle { it.copy(cantidadesRecepcion = emptyMap()) }
                    aplicarOrden(r.valor.orden)
                    cargarRecepciones(r.valor.orden)
                    editarDetalle {
                        it.copy(
                            accion = null,
                            mensaje = if (r.valor.recepcionCompleta) "Recepción completa. Stock actualizado."
                            else "Recepción parcial registrada. Stock actualizado."
                        )
                    }
                }
                is ResultadoAbastecimiento.Error -> editarDetalle { it.copy(accion = null, mensaje = r.mensaje) }
            }
        }
    }

    private fun editarDetalle(transformar: (DetalleOrdenUiState) -> DetalleOrdenUiState) {
        _estado.update { it.copy(detalle = transformar(it.detalle)) }
    }

    private companion object {
        const val RETARDO_BUSQUEDA_MS = 350L
    }
}
