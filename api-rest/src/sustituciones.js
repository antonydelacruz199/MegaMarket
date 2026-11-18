/**
 * Reglas de sustitución: categoría, ±20 %, disponibilidad, timeout 5 min.
 * Inventario compensatorio con AJUSTE_*; nunca SALIDA_VENTA de sustitución.
 */

const TOLERANCIA_PRECIO = 0.2;

function precioVigente(producto) {
  const precio = Number(producto.precio_centimos);
  const oferta = producto.precio_oferta_centimos;
  if (
    producto.es_oferta &&
    oferta !== null &&
    oferta !== undefined &&
    Number(oferta) > 0 &&
    Number(oferta) < precio
  ) {
    return Number(oferta);
  }
  return precio;
}

function diferenciaPrecioDentroDeTolerancia(precioOriginal, precioSustituto) {
  const orig = Number(precioOriginal);
  const sust = Number(precioSustituto);
  if (!Number.isFinite(orig) || orig <= 0 || !Number.isFinite(sust) || sust <= 0) {
    return false;
  }
  return Math.abs(sust - orig) / orig <= TOLERANCIA_PRECIO + Number.EPSILON;
}

function montoAjusteCentimos(precioOriginal, precioSustituto, cantidad) {
  const diffUnit = Number(precioSustituto) - Number(precioOriginal);
  const monto = Math.abs(diffUnit) * Number(cantidad);
  if (!Number.isSafeInteger(monto) || monto <= 0) return null;
  if (diffUnit > 0) {
    // Sustituto más caro: no se cobra de más → DESCUENTO absorbido
    return { tipo: 'DESCUENTO', montoCentimos: monto };
  }
  // Sustituto más barato → DEVOLUCION de la diferencia
  return { tipo: 'DEVOLUCION', montoCentimos: monto };
}

function mapearSustitucion(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    pedidoDetalleId: fila.pedido_detalle_id,
    pedidoId: fila.pedido_id || null,
    productoOriginalId: fila.producto_original_id,
    productoSustitutoId: fila.producto_sustituto_id,
    pickerId: fila.picker_id,
    cantidad: Number(fila.cantidad),
    precioOriginalCentimos: Number(fila.precio_original_centimos),
    precioSustitutoCentimos: Number(fila.precio_sustituto_centimos),
    estado: fila.estado,
    fechaPropuesta: fila.fecha_propuesta,
    fechaLimite: fila.fecha_limite,
    fechaRespuesta: fila.fecha_respuesta || null,
    respuestaAutomatica: Boolean(fila.respuesta_automatica),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearCandidato(fila) {
  return {
    id: fila.id,
    nombre: fila.nombre,
    marca: fila.marca,
    categoriaId: fila.categoria_id,
    pasillo: fila.pasillo || null,
    codigoBarras: fila.codigo_barras || null,
    precioCentimos: precioVigente(fila),
    stockDisponible: Number(fila.disponible),
    dentroTolerancia: true
  };
}

/**
 * UUID v4-like determinista (sin migración): evita AJUSTE duplicado por sustitución.
 * Clave estable → mismo uuid_operacion en reintentos.
 */
function uuidDesdeClave(clave) {
  const crypto = require('crypto');
  const h = crypto.createHash('sha256').update(clave).digest();
  const b = Buffer.from(h.subarray(0, 16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const hex = b.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

function uuidOperacionSustitucion(sustitucionId, tipo) {
  return uuidDesdeClave(`mm-sust:${tipo}:${sustitucionId}`);
}

function uuidOperacionEliminacion(detalleId) {
  return uuidDesdeClave(`mm-elim:AJUSTE_ENTRADA:${detalleId}`);
}

function uuidAjusteEliminacion(detalleId) {
  return uuidDesdeClave(`mm-elim:DEVOLUCION:${detalleId}`);
}

/**
 * Aplica AJUSTE_ENTRADA (original) + AJUSTE_SALIDA (sustituto) una sola vez por sustitución.
 * Se llama al completar el escaneo (línea → SUSTITUIDO), no al aceptar.
 */
async function aplicarAjustesInventarioSustitucion(cliente, {
  sustitucionId,
  pedidoId,
  productoOriginalId,
  productoSustitutoId,
  cantidad,
  actorId,
  disponibleOriginal,
  disponibleSustituto
}) {
  const cant = Number(cantidad);
  const uuidSalida = uuidOperacionSustitucion(sustitucionId, 'AJUSTE_SALIDA');
  const uuidEntrada = uuidOperacionSustitucion(sustitucionId, 'AJUSTE_ENTRADA');

  // Insert-first como candado: solo el ganador mueve stock (sin duplicar AJUSTE).
  const { rowCount: salidasInsertadas } = await cliente.query(
    `INSERT INTO movimientos_inventario (
       uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
     ) VALUES ($1, $2, $3, $4, 'AJUSTE_SALIDA', $5)
     ON CONFLICT (uuid_operacion) DO NOTHING`,
    [uuidSalida, productoSustitutoId, pedidoId, actorId, cant]
  );
  if (!salidasInsertadas) {
    return { aplicado: false, idempotente: true, ok: true };
  }

  if (!disponibleSustituto || Number(disponibleSustituto.disponible) < cant) {
    return {
      aplicado: false,
      ok: false,
      http: 409,
      codigo: 'STOCK_INSUFICIENTE',
      error: 'Stock insuficiente del producto sustituto al preparar'
    };
  }

  await cliente.query(`UPDATE productos SET stock = stock - $1 WHERE id = $2`, [
    cant,
    productoSustitutoId
  ]);

  if (disponibleOriginal) {
    await cliente.query(
      `INSERT INTO movimientos_inventario (
         uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
       ) VALUES ($1, $2, $3, $4, 'AJUSTE_ENTRADA', $5)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [uuidEntrada, productoOriginalId, pedidoId, actorId, cant]
    );
    await cliente.query(`UPDATE productos SET stock = stock + $1 WHERE id = $2`, [
      cant,
      productoOriginalId
    ]);
  }

  return { aplicado: true, idempotente: false, ok: true };
}

/**
 * Devuelve al stock el original que ya no se entrega.
 * Solo el neto aún no compensado (SALIDA_VENTA − AJUSTE_ENTRADA) y como máximo la cantidad de la línea.
 * UUID estable por detalle: un reintento no suma stock otra vez.
 */
async function restituirInventarioLineaEliminada(cliente, {
  detalleId,
  pedidoId,
  productoId,
  cantidadLinea,
  actorId
}) {
  await cliente.query(`SELECT id FROM productos WHERE id = $1 FOR UPDATE`, [productoId]);
  const { rows: [neto] } = await cliente.query(
    `SELECT
       COALESCE(SUM(cantidad) FILTER (WHERE tipo = 'SALIDA_VENTA'), 0)::int AS salidas,
       COALESCE(SUM(cantidad) FILTER (WHERE tipo = 'AJUSTE_ENTRADA'), 0)::int AS entradas
     FROM movimientos_inventario
     WHERE pedido_id = $1 AND producto_id = $2 AND deleted_at IS NULL`,
    [pedidoId, productoId]
  );
  const pendiente = Number(neto.salidas) - Number(neto.entradas);
  const cant = Math.min(Number(cantidadLinea), pendiente);
  if (!Number.isInteger(cant) || cant <= 0) {
    return { aplicado: false, idempotente: true, cantidad: 0 };
  }

  const uuid = uuidOperacionEliminacion(detalleId);
  const { rowCount } = await cliente.query(
    `INSERT INTO movimientos_inventario (
       uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
     ) VALUES ($1, $2, $3, $4, 'AJUSTE_ENTRADA', $5)
     ON CONFLICT (uuid_operacion) DO NOTHING`,
    [uuid, productoId, pedidoId, actorId, cant]
  );
  if (!rowCount) {
    return { aplicado: false, idempotente: true, cantidad: 0 };
  }
  await cliente.query(`UPDATE productos SET stock = stock + $1 WHERE id = $2`, [
    cant,
    productoId
  ]);
  return { aplicado: true, idempotente: false, cantidad: cant };
}

module.exports = {
  TOLERANCIA_PRECIO,
  precioVigente,
  diferenciaPrecioDentroDeTolerancia,
  montoAjusteCentimos,
  mapearSustitucion,
  mapearCandidato,
  uuidOperacionSustitucion,
  uuidOperacionEliminacion,
  uuidAjusteEliminacion,
  aplicarAjustesInventarioSustitucion,
  restituirInventarioLineaEliminada
};
