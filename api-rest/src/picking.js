/**
 * Reglas de preparación (picking) sin tocar inventario de venta.
 */

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

/**
 * El picking ocurre antes del cobro ONLINE.
 * CONTRAENTREGA y ONLINE (pago pendiente) se pueden preparar.
 */
function puedePrepararSegunPago(pago) {
  if (!pago) {
    return { ok: true };
  }
  if (pago.modalidad === 'CONTRAENTREGA' || pago.modalidad === 'ONLINE') {
    if (['CANCELADO', 'RECHAZADO'].includes(pago.estado)) {
      return {
        ok: false,
        http: 409,
        codigo: 'PAGO_NO_PERMITE_PICKING',
        error: 'El pago del pedido no permite continuar la preparación'
      };
    }
    return { ok: true };
  }
  return {
    ok: false,
    http: 409,
    codigo: 'PICKING_NO_PERMITIDO',
    error: 'No se puede preparar el pedido con el estado de pago actual'
  };
}

function assertPickerAsignado(pedido, pickerId) {
  if (!pedido) {
    return {
      ok: false,
      http: 404,
      codigo: 'PEDIDO_NO_ENCONTRADO',
      error: 'Pedido no encontrado'
    };
  }
  if (!pedido.picker_id || pedido.picker_id !== pickerId) {
    return {
      ok: false,
      http: 403,
      codigo: 'PEDIDO_NO_ASIGNADO',
      error: 'El pedido no está asignado a este picker'
    };
  }
  return { ok: true };
}

function mapearLineaPreparacion(fila) {
  const sustitucionAceptadaId = fila.sustitucion_aceptada_id || null;
  const productoSustitutoId = fila.producto_sustituto_id || null;
  // Código / pasillo esperados para escanear: sustituto aceptado o producto de la línea.
  const codigoBarras = sustitucionAceptadaId
    ? fila.codigo_barras_sustituto || null
    : fila.codigo_barras || null;
  const pasillo = sustitucionAceptadaId
    ? fila.pasillo_sustituto || fila.pasillo || null
    : fila.pasillo || null;
  return {
    id: fila.id,
    pedidoId: fila.pedido_id,
    productoId: fila.producto_id,
    nombreProducto: fila.nombre_producto,
    cantidad: Number(fila.cantidad),
    cantidadArmada: Number(fila.cantidad_armada),
    estadoPicking: fila.estado_picking,
    precioUnitarioCentimos: Number(fila.precio_unitario_centimos),
    subtotalCentimos: Number(fila.subtotal_centimos),
    pasillo,
    codigoBarras,
    fechaEscaneo: fila.fecha_escaneo || null,
    escaneadoPor: fila.escaneado_por || null,
    sustitucionAceptadaId,
    productoSustitutoId,
    nombreSustituto: fila.nombre_sustituto || null,
    resolubleSinProducto: Boolean(fila.resoluble_sin_producto)
  };
}

function mapearEscaneo(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    pedidoDetalleId: fila.pedido_detalle_id,
    pickerId: fila.picker_id,
    codigoBarras: fila.codigo_barras,
    cantidad: Number(fila.cantidad),
    resultado: fila.resultado,
    fechaEscaneo: fila.fecha_escaneo,
    createdAt: fila.created_at
  };
}

/** Líneas candidatas a salida a delivery (aún requieren validar cantidades / sustituciones). */
const ESTADOS_LINEA_LISTOS = Object.freeze(['ARMADO', 'SUSTITUIDO', 'ELIMINADO']);

function marcaTiempo(valor) {
  if (!valor) return 0;
  const t = valor instanceof Date ? valor.getTime() : Date.parse(valor);
  return Number.isFinite(t) ? t : 0;
}

/**
 * Una línea pasa a ELIMINADO tras rechazo del cliente, o sin candidatos de
 * almacén (sinStock). Sin propuesta pendiente ni sustituto aceptado.
 * @param {object} [opts]
 * @param {boolean} [opts.sinCandidatos]
 */
function evaluarEliminacionLinea(linea, sustituciones, opts = {}) {
  const estado = String(linea.estado_picking || '').trim();
  const susts = sustituciones || [];
  const pendiente = susts.find((s) => s.estado === 'PENDIENTE') || null;
  const aceptada = susts.find((s) => s.estado === 'ACEPTADA') || null;
  const rechazadas = susts.filter((s) => s.estado === 'RECHAZADA');
  const sinCandidatos = Boolean(opts.sinCandidatos);

  if (pendiente) {
    return {
      ok: false,
      http: 409,
      codigo: 'SUSTITUCION_PENDIENTE',
      error: 'Hay una propuesta pendiente; espera la decisión antes de quitar la línea',
      sustitucionId: pendiente.id
    };
  }
  if (aceptada) {
    return {
      ok: false,
      http: 409,
      codigo: 'SUSTITUCION_ACEPTADA',
      error: 'Hay un sustituto aceptado: escanéalo en lugar de eliminar la línea',
      sustitucionId: aceptada.id
    };
  }
  if (rechazadas.length === 0 && !sinCandidatos) {
    return {
      ok: false,
      http: 409,
      codigo: 'ELIMINADO_SIN_RESOLUCION',
      error: 'Solo se elimina la línea tras un rechazo del cliente o sin stock en almacén'
    };
  }

  const justificante = rechazadas.length
    ? rechazadas.slice().sort((a, b) => {
        const delta = marcaTiempo(b.fecha_respuesta || b.created_at) -
          marcaTiempo(a.fecha_respuesta || a.created_at);
        if (delta !== 0) return delta;
        return String(b.id).localeCompare(String(a.id));
      })[0]
    : null;

  if (estado === 'ELIMINADO') {
    return {
      ok: true,
      yaResuelta: true,
      sustitucionId: justificante ? justificante.id : null,
      sinStock: !justificante
    };
  }
  if (estado !== 'FALTANTE') {
    return {
      ok: false,
      http: 409,
      codigo: 'ESTADO_PICKING_INVALIDO',
      error: `No se puede eliminar una línea en estado ${estado || '(vacío)'}`
    };
  }
  return {
    ok: true,
    yaResuelta: false,
    sustitucionId: justificante ? justificante.id : null,
    sinStock: !justificante && sinCandidatos
  };
}

function lineasListasParaSalida(estados) {
  if (!Array.isArray(estados) || estados.length === 0) return false;
  return estados.every((e) => ESTADOS_LINEA_LISTOS.includes(String(e || '').trim()));
}

/**
 * Validación centralizada de preparación completa (Fase B).
 * No muta datos. `lineas` y `sustituciones` deben corresponder al mismo pedido.
 *
 * @param {Array<{id:string,estado_picking:string,cantidad:number,cantidad_armada:number}>} lineas
 * @param {Array<{id:string,pedido_detalle_id:string,estado:string}>} sustituciones
 * @returns {{ok:true}|{ok:false,codigo:string,error:string,impedimentos:object[]}}
 */
function evaluarPreparacionCompleta(lineas, sustituciones = []) {
  if (!Array.isArray(lineas) || lineas.length === 0) {
    return {
      ok: false,
      codigo: 'PICKING_INCOMPLETO',
      error: 'El pedido no tiene líneas de preparación',
      impedimentos: []
    };
  }

  const porDetalle = new Map();
  for (const s of sustituciones || []) {
    const detalleId = s.pedido_detalle_id;
    if (!porDetalle.has(detalleId)) porDetalle.set(detalleId, []);
    porDetalle.get(detalleId).push(s);
  }

  const impedimentos = [];

  for (const linea of lineas) {
    const estado = String(linea.estado_picking || '').trim();
    const solicitada = Number(linea.cantidad);
    const armada = Number(linea.cantidad_armada || 0);
    const susts = porDetalle.get(linea.id) || [];
    const pendiente = susts.find((s) => s.estado === 'PENDIENTE') || null;
    const aceptada = susts.find((s) => s.estado === 'ACEPTADA') || null;
    const rechazada = susts.find((s) => s.estado === 'RECHAZADA') || null;

    if (pendiente) {
      impedimentos.push({
        pedidoDetalleId: linea.id,
        estadoPicking: estado,
        sustitucionId: pendiente.id,
        codigo: 'SUSTITUCION_PENDIENTE',
        motivo: 'Hay una propuesta de sustitución pendiente de decisión del cliente'
      });
      continue;
    }

    if (estado === 'PENDIENTE') {
      impedimentos.push({
        pedidoDetalleId: linea.id,
        estadoPicking: estado,
        codigo: 'LINEA_PENDIENTE',
        motivo: 'La línea aún no ha sido preparada ni marcada como faltante'
      });
      continue;
    }

    if (estado === 'FALTANTE') {
      if (aceptada) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          sustitucionId: aceptada.id,
          codigo: 'SUSTITUTO_SIN_ESCANEAR',
          motivo: 'Sustitución aceptada: falta escanear el producto sustituto'
        });
      } else {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          codigo: 'LINEA_FALTANTE',
          motivo: 'Producto faltante sin resolución válida (sustitución aceptada o eliminación)'
        });
      }
      continue;
    }

    if (estado === 'ARMADO') {
      if (aceptada) {
        // Inconsistencia: con sustituto aceptado la línea debe terminar en SUSTITUIDO.
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          sustitucionId: aceptada.id,
          codigo: 'SUSTITUTO_SIN_ESCANEAR',
          motivo: 'Hay sustitución aceptada; el escaneo del sustituto no está completo'
        });
        continue;
      }
      if (!(armada >= solicitada && solicitada > 0)) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          codigo: 'CANTIDAD_INCOMPLETA',
          motivo: `Cantidad armada (${armada}) inferior a la solicitada (${solicitada})`
        });
      }
      continue;
    }

    if (estado === 'SUSTITUIDO') {
      if (!aceptada) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          codigo: 'SUSTITUCION_NO_ACEPTADA',
          motivo: 'Línea SUSTITUIDO sin sustitución ACEPTADA asociada'
        });
        continue;
      }
      if (!(armada >= solicitada && solicitada > 0)) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          sustitucionId: aceptada.id,
          codigo: 'CANTIDAD_INCOMPLETA',
          motivo: `Sustituto aceptado con cantidad armada (${armada}) incompleta (${solicitada})`
        });
      }
      continue;
    }

    if (estado === 'ELIMINADO') {
      // Resolución válida: rechazo del cliente o sin stock (sin aceptación pendiente).
      if (aceptada) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          sustitucionId: aceptada.id,
          codigo: 'SUSTITUTO_SIN_ESCANEAR',
          motivo: 'Hay sustitución aceptada; la línea no puede quedar ELIMINADO'
        });
        continue;
      }
      if (armada !== 0) {
        impedimentos.push({
          pedidoDetalleId: linea.id,
          estadoPicking: estado,
          codigo: 'ELIMINADO_SIN_RESOLUCION',
          motivo: 'ELIMINADO exige cantidad armada en cero'
        });
      }
      continue;
    }

    impedimentos.push({
      pedidoDetalleId: linea.id,
      estadoPicking: estado,
      codigo: 'ESTADO_PICKING_INVALIDO',
      motivo: `Estado de picking no apto para cerrar: ${estado || '(vacío)'}`
    });
  }

  if (impedimentos.length === 0) {
    return { ok: true };
  }

  const codigoPrincipal = impedimentos[0].codigo || 'PICKING_INCOMPLETO';
  return {
    ok: false,
    codigo: codigoPrincipal === 'LINEA_PENDIENTE' || codigoPrincipal === 'LINEA_FALTANTE'
      ? 'PICKING_INCOMPLETO'
      : codigoPrincipal,
    error: 'El pedido no puede pasar a LISTO: preparación incompleta o sustituciones sin resolver',
    impedimentos
  };
}

/**
 * Carga líneas + sustituciones con bloqueo y evalúa preparación completa.
 * Requiere transacción abierta; usa FOR UPDATE sobre pedido_detalle.
 */
async function validarPreparacionCompleta(cliente, pedidoId) {
  const { rows: lineas } = await cliente.query(
    `SELECT id, estado_picking, cantidad, cantidad_armada
     FROM pedido_detalle
     WHERE pedido_id = $1 AND deleted_at IS NULL
     FOR UPDATE`,
    [pedidoId]
  );
  const { rows: sustituciones } = await cliente.query(
    `SELECT s.id, s.pedido_detalle_id, s.estado
     FROM sustituciones_pedido s
     JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
     WHERE d.pedido_id = $1 AND d.deleted_at IS NULL`,
    [pedidoId]
  );
  return evaluarPreparacionCompleta(lineas, sustituciones);
}

/**
 * Si la preparación está completa: EN_PICKING → CONFIRMADO.
 * CONTRAENTREGA avanza también a LISTO (pago en entrega).
 * ONLINE permanece en CONFIRMADO hasta el cobro del cliente.
 * @returns {Promise<string|null>} estado resultante o null si no cerró
 */
async function intentarMarcarListo(cliente, pedidoId) {
  const validacion = await validarPreparacionCompleta(cliente, pedidoId);
  if (!validacion.ok) {
    return null;
  }
  const { rows: pedRows } = await cliente.query(
    `UPDATE pedidos
     SET estado = 'CONFIRMADO', updated_at = now()
     WHERE id = $1 AND estado = 'EN_PICKING' AND deleted_at IS NULL
     RETURNING id, estado`,
    [pedidoId]
  );
  if (!pedRows[0]) return null;

  const { rows: pagoRows } = await cliente.query(
    `SELECT modalidad, estado FROM pagos WHERE pedido_id = $1 ORDER BY created_at DESC LIMIT 1`,
    [pedidoId]
  );
  const modalidad = pagoRows[0]?.modalidad || null;
  const pagoEstado = pagoRows[0]?.estado || null;

  // ONLINE sin cobro: queda CONFIRMADO hasta que el cliente pague.
  if (modalidad === 'ONLINE' && pagoEstado !== 'CONFIRMADO') {
    return 'CONFIRMADO';
  }

  // CONTRAENTREGA, ONLINE ya cobrado, o legado: despachar a LISTO.
  if (modalidad === 'ONLINE' && pagoEstado === 'CONFIRMADO') {
    await cliente.query(
      `UPDATE pedidos SET estado = 'PAGADO', updated_at = now()
       WHERE id = $1 AND estado = 'CONFIRMADO' AND deleted_at IS NULL`,
      [pedidoId]
    );
  }
  const { rows: listo } = await cliente.query(
    `UPDATE pedidos
     SET estado = 'LISTO', updated_at = now()
     WHERE id = $1 AND estado IN ('CONFIRMADO', 'PAGADO') AND deleted_at IS NULL
     RETURNING estado`,
    [pedidoId]
  );
  return listo[0]?.estado || 'CONFIRMADO';
}

module.exports = {
  UUID_RE,
  esUuid,
  puedePrepararSegunPago,
  assertPickerAsignado,
  mapearLineaPreparacion,
  mapearEscaneo,
  ESTADOS_LINEA_LISTOS,
  lineasListasParaSalida,
  evaluarEliminacionLinea,
  evaluarPreparacionCompleta,
  validarPreparacionCompleta,
  intentarMarcarListo
};
