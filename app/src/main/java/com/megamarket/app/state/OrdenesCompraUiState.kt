package com.megamarket.app.state

import com.megamarket.app.data.repository.EstadoOrden
import com.megamarket.app.data.repository.EstadoTransferencia
import com.megamarket.app.data.repository.LineaOrdenNueva
import com.megamarket.app.data.repository.MovimientoRecepcion
import com.megamarket.app.data.repository.OrdenCompra
import com.megamarket.app.data.repository.ProductoOpcion
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.data.repository.parsearSolesACentimos

/** Línea editable en la pantalla de nueva orden (cantidad y costo se capturan como texto). */
data class LineaOrdenEditable(
    val producto: ProductoOpcion,
    val cantidadTexto: String = "1",
    val costoTexto: String = ""
) {
    val cantidad: Int? get() = cantidadTexto.trim().toIntOrNull()?.takeIf { it > 0 }
    val costoCentimos: Long? get() = parsearSolesACentimos(costoTexto)?.takeIf { it > 0 }
    val valida: Boolean get() = cantidad != null && costoCentimos != null
    val subtotalCentimos: Long get() = (cantidad ?: 0).toLong() * (costoCentimos ?: 0L)

    fun aLinea(): LineaOrdenNueva? {
        val c = cantidad ?: return null
        val p = costoCentimos ?: return null
        return LineaOrdenNueva(producto.id, c, p)
    }
}

data class NuevaOrdenUiState(
    val proveedorId: String? = null,
    val proveedorSeleccionado: Proveedor? = null,
    val proveedores: List<Proveedor> = emptyList(),
    val consultaProveedor: String = "",
    val cargandoProveedores: Boolean = false,
    val errorProveedores: String? = null,
    val productos: List<ProductoOpcion> = emptyList(),
    val consultaProducto: String = "",
    val buscandoProductos: Boolean = false,
    val errorProductos: String? = null,
    val lineas: List<LineaOrdenEditable> = emptyList(),
    val guardando: Boolean = false,
    val ordenCreadaId: String? = null,
    val error: String? = null
) {
    val totalCentimos: Long get() = lineas.sumOf { it.subtotalCentimos }

    /** Mensaje de la primera regla incumplida o null si la orden puede enviarse. */
    val errorValidacion: String?
        get() = when {
            proveedorSeleccionado == null -> "Selecciona un proveedor."
            proveedorSeleccionado.activo.not() -> "El proveedor está inactivo."
            lineas.isEmpty() -> "Agrega al menos un producto."
            lineas.any { it.cantidad == null } -> "Todas las cantidades deben ser enteros positivos."
            lineas.any { it.costoCentimos == null } -> "Todos los costos unitarios deben ser mayores a cero."
            totalCentimos <= 0 -> "El total de la orden debe ser positivo."
            else -> null
        }
}

/** Acción crítica en curso sobre la orden; mientras no sea null se bloquean los demás botones. */
enum class AccionOrden { APROBAR, CANCELAR, PAGAR, CONSULTAR_TRANSFERENCIA, RECIBIR }

data class DetalleOrdenUiState(
    val cargando: Boolean = false,
    val orden: OrdenCompra? = null,
    val recepciones: List<MovimientoRecepcion> = emptyList(),
    val error: String? = null,
    val accion: AccionOrden? = null,
    /** Mensaje efímero para Snackbar (éxito o error de una acción). */
    val mensaje: String? = null,
    /** Resultado del último intento de pago, para mostrarlo de forma persistente. */
    val estadoPago: EstadoTransferencia? = null,
    val avisoPago: String? = null,
    val mensajePago: String? = null,
    /** Cantidades a recibir por detalleId (texto editable). */
    val cantidadesRecepcion: Map<String, String> = emptyMap(),
    val errorRecepcion: String? = null
) {
    val procesando: Boolean get() = accion != null
}

data class OrdenesCompraUiState(
    // Lista
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val ordenes: List<OrdenCompra> = emptyList(),
    val filtroEstado: EstadoOrden? = null,
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val total: Int = 0,
    val error: String? = null,
    // Nueva orden y detalle
    val nueva: NuevaOrdenUiState = NuevaOrdenUiState(),
    val detalle: DetalleOrdenUiState = DetalleOrdenUiState()
)
