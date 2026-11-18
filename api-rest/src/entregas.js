/**
 * Reglas de entrega: evidencia GPS/foto, pago y asignación del repartidor.
 * foto_referencia es solo clave/URL remota — nunca bytes en PostgreSQL.
 */

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

function assertRepartidorAsignado(pedido, repartidorId) {
  if (!pedido) {
    return {
      ok: false,
      http: 404,
      codigo: 'PEDIDO_NO_ENCONTRADO',
      error: 'Pedido no encontrado'
    };
  }
  if (!pedido.repartidor_id || pedido.repartidor_id !== repartidorId) {
    return {
      ok: false,
      http: 403,
      codigo: 'PEDIDO_NO_ASIGNADO',
      error: 'El pedido no está asignado a este repartidor'
    };
  }
  return { ok: true };
}

/**
 * ENTREGADO solo con confirmación bancaria válida, salvo legado explícito:
 * pedidos históricos sin fila en `pagos` y sin `solicitud_hash` (protocolo anterior).
 * CONTRAENTREGA/ONLINE con pago PENDIENTE (u otro no CONFIRMADO) → bloqueado.
 * Pedidos nuevos sin pagar (p. ej. v2 con hash y sin pagos) NO usan la excepción.
 */
function puedeMarcarEntregadoSegunPago(pago, pedido = null) {
  if (pago) {
    const confirmadoBancario =
      pago.estado === 'CONFIRMADO' &&
      Boolean(pago.numero_operacion_banco) &&
      Boolean(pago.fecha_confirmacion);
    if (confirmadoBancario) {
      return { ok: true, legado: false };
    }
    const modalidad = pago.modalidad || 'ONLINE';
    return {
      ok: false,
      http: 409,
      codigo: 'ENTREGA_REQUIERE_PAGO',
      error:
        modalidad === 'CONTRAENTREGA'
          ? 'El pago contraentrega debe confirmarse en banco antes de registrar la entrega'
          : 'El pago online debe estar confirmado antes de registrar la entrega'
    };
  }

  // Sin fila en pagos: solo legado histórico explícito.
  if (pedido && pedido.solicitud_hash == null) {
    return { ok: true, legado: true };
  }

  return {
    ok: false,
    http: 409,
    codigo: 'ENTREGA_REQUIERE_PAGO',
    error:
      'Pedidos nuevos requieren pago bancario confirmado; la compatibilidad solo aplica a históricos sin pagos'
  };
}

function validarEvidencia({ fotoReferencia, latitud, longitud, fechaEntrega, pedidoId = null }) {
  const foto = String(fotoReferencia || '').trim();
  if (!foto) {
    return {
      ok: false,
      http: 400,
      codigo: 'FOTO_REQUERIDA',
      error: 'fotoReferencia es obligatoria (clave o URL remota, no el archivo)'
    };
  }
  if (foto.length > 2000) {
    return {
      ok: false,
      http: 400,
      codigo: 'FOTO_INVALIDA',
      error: 'fotoReferencia supera la longitud permitida'
    };
  }
  // Referencias remotas deben pertenecer al pedido (evita claves ajenas).
  if (pedidoId && foto.startsWith('entregas/')) {
    const { perteneceAPedido, esClaveRemotaEntrega } = require('./storage/keys');
    if (!perteneceAPedido(foto, pedidoId) || !esClaveRemotaEntrega(foto)) {
      return {
        ok: false,
        http: 400,
        codigo: 'FOTO_AJENA',
        error: 'fotoReferencia no pertenece a este pedido'
      };
    }
  }

  const lat = Number(latitud);
  const lon = Number(longitud);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
    return {
      ok: false,
      http: 400,
      codigo: 'GPS_INVALIDO',
      error: 'latitud debe estar entre -90 y 90'
    };
  }
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) {
    return {
      ok: false,
      http: 400,
      codigo: 'GPS_INVALIDO',
      error: 'longitud debe estar entre -180 y 180'
    };
  }

  let fecha = null;
  if (fechaEntrega !== undefined && fechaEntrega !== null && String(fechaEntrega).trim() !== '') {
    const parsed = new Date(fechaEntrega);
    if (Number.isNaN(parsed.getTime())) {
      return {
        ok: false,
        http: 400,
        codigo: 'FECHA_INVALIDA',
        error: 'fechaEntrega no es una fecha válida'
      };
    }
    fecha = parsed.toISOString();
  }

  return {
    ok: true,
    fotoReferencia: foto,
    latitud: lat,
    longitud: lon,
    fechaEntrega: fecha
  };
}

function mapearEntrega(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    pedidoId: fila.pedido_id,
    repartidorId: fila.repartidor_id,
    fotoReferencia: fila.foto_referencia,
    latitud: Number(fila.latitud),
    longitud: Number(fila.longitud),
    fechaEntrega: fila.fecha_entrega,
    createdAt: fila.created_at
  };
}

function mapearPedidoEntrega(fila) {
  return {
    id: fila.id,
    clienteId: fila.cliente_id,
    estado: fila.estado,
    totalCentimos: Number(fila.total_centimos),
    franjaEntregaId: fila.franja_entrega_id || null,
    repartidorId: fila.repartidor_id,
    fecha: fila.fecha,
    version: Number(fila.version),
    updatedAt: fila.updated_at
  };
}

module.exports = {
  UUID_RE,
  esUuid,
  assertRepartidorAsignado,
  puedeMarcarEntregadoSegunPago,
  validarEvidencia,
  mapearEntrega,
  mapearPedidoEntrega
};
