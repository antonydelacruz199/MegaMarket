/**
 * Reglas de transición de pedidos.estado (Fase 2.1 / 2.2).
 * PAGADO nunca se acepta desde clientes Android: solo tras pago bancario verificado.
 */

const ESTADOS = Object.freeze([
  'CONFIRMADO',
  'PAGADO',
  'EN_PICKING',
  'LISTO',
  'EN_CAMINO',
  'ENTREGADO',
  'CANCELADO'
]);

/** Transiciones estructurales (sin reglas de pago/rol). */
const TRANSICIONES = Object.freeze({
  CONFIRMADO: new Set(['PAGADO', 'EN_PICKING', 'CANCELADO']),
  PAGADO: new Set(['EN_PICKING', 'CANCELADO']),
  EN_PICKING: new Set(['PAGADO', 'LISTO', 'CANCELADO']),
  LISTO: new Set(['PAGADO', 'EN_CAMINO', 'CANCELADO']),
  EN_CAMINO: new Set(['PAGADO', 'ENTREGADO', 'CANCELADO']),
  ENTREGADO: new Set(['PAGADO']),
  CANCELADO: new Set()
});

/**
 * @param {object} ctx
 * @param {string} ctx.desde
 * @param {string} ctx.hacia
 * @param {string} ctx.rol
 * @param {string} ctx.usuarioId
 * @param {object} ctx.pedido
 * @param {object|null} ctx.pago — fila pagos o null (legado)
 * @param {boolean} [ctx.origenInternoBancario] — solo true en verificación servidor
 */
function evaluarTransicion(ctx) {
  const { desde, hacia, rol, usuarioId, pedido, pago, origenInternoBancario } = ctx;

  if (!ESTADOS.includes(desde) || !ESTADOS.includes(hacia)) {
    return { ok: false, http: 400, codigo: 'ESTADO_INVALIDO', error: 'Estado no reconocido' };
  }
  if (desde === hacia) {
    return { ok: false, http: 409, codigo: 'TRANSICION_NOOP', error: 'El pedido ya tiene ese estado' };
  }
  if (!TRANSICIONES[desde] || !TRANSICIONES[desde].has(hacia)) {
    return {
      ok: false,
      http: 409,
      codigo: 'TRANSICION_INVALIDA',
      error: `No se permite pasar de ${desde} a ${hacia}`
    };
  }

  if (hacia === 'PAGADO') {
    if (!origenInternoBancario) {
      return {
        ok: false,
        http: 403,
        codigo: 'PAGO_REQUIERE_BANCO',
        error: 'PAGADO solo puede establecerse tras verificación bancaria en el servidor'
      };
    }
    if (
      !pago ||
      pago.estado !== 'CONFIRMADO' ||
      !pago.numero_operacion_banco ||
      !pago.fecha_confirmacion
    ) {
      return {
        ok: false,
        http: 409,
        codigo: 'PAGO_NO_VERIFICADO',
        error: 'No hay cobro bancario confirmado para este pedido'
      };
    }
  }

  if (hacia === 'EN_PICKING' && desde === 'CONFIRMADO') {
    const modalidad = pago ? pago.modalidad : null;
    const legadoSinPago = !pago;
    const contraentrega = modalidad === 'CONTRAENTREGA';
    const onlinePagado = false; // desde CONFIRMADO online no está PAGADO
    if (!legadoSinPago && !contraentrega && !onlinePagado) {
      // ONLINE con pago pendiente
      if (modalidad === 'ONLINE') {
        return {
          ok: false,
          http: 409,
          codigo: 'PICKING_REQUIERE_PAGO',
          error: 'El pago online debe confirmarse antes de iniciar el picking'
        };
      }
      return {
        ok: false,
        http: 409,
        codigo: 'PICKING_NO_PERMITIDO',
        error: 'No se puede iniciar picking en el estado de pago actual'
      };
    }
  }

  if (hacia === 'CANCELADO' && desde === 'ENTREGADO') {
    return {
      ok: false,
      http: 409,
      codigo: 'TRANSICION_INVALIDA',
      error: 'Un pedido entregado no se puede cancelar'
    };
  }

  // Cobro bancario confirmado: no cancelar sin gestionar devolución primero.
  if (hacia === 'CANCELADO') {
    const pagoConfirmado = pago && pago.estado === 'CONFIRMADO';
    if (pagoConfirmado || desde === 'PAGADO') {
      return {
        ok: false,
        http: 409,
        codigo: 'CANCELACION_REQUIERE_DEVOLUCION',
        error:
          'No se puede cancelar un pedido con pago confirmado sin registrar la devolución bancaria'
      };
    }
  }

  // Autorización por rol
  if (rol === 'ADMINISTRADOR') {
    if (hacia === 'PAGADO' && !origenInternoBancario) {
      return {
        ok: false,
        http: 403,
        codigo: 'PAGO_REQUIERE_BANCO',
        error: 'PAGADO solo puede establecerse tras verificación bancaria en el servidor'
      };
    }
    return { ok: true };
  }

  if (rol === 'CLIENTE') {
    if (hacia !== 'CANCELADO' || pedido.cliente_id !== usuarioId) {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'El cliente solo puede cancelar sus propios pedidos permitidos'
      };
    }
    if (desde !== 'CONFIRMADO') {
      return {
        ok: false,
        http: 409,
        codigo: 'CANCELACION_NO_PERMITIDA',
        error: 'Solo se puede cancelar un pedido CONFIRMADO sin cobro bancario confirmado'
      };
    }
    return { ok: true };
  }

  if (rol === 'PICKER') {
    if (!['EN_PICKING', 'LISTO'].includes(hacia)) {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'El picker no puede aplicar esa transición'
      };
    }
    // Cerrar a LISTO exige asignación explícita del picker.
    if (hacia === 'LISTO') {
      if (!pedido.picker_id || pedido.picker_id !== usuarioId) {
        return {
          ok: false,
          http: 403,
          codigo: 'PEDIDO_NO_ASIGNADO',
          error: 'Solo el picker asignado puede marcar el pedido como LISTO'
        };
      }
      return { ok: true };
    }
    if (pedido.picker_id && pedido.picker_id !== usuarioId) {
      return {
        ok: false,
        http: 403,
        codigo: 'PEDIDO_NO_ASIGNADO',
        error: 'El pedido está asignado a otro picker'
      };
    }
    return { ok: true };
  }

  if (rol === 'REPARTIDOR') {
    if (!['EN_CAMINO', 'ENTREGADO'].includes(hacia)) {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'El repartidor no puede aplicar esa transición'
      };
    }
    if (pedido.repartidor_id && pedido.repartidor_id !== usuarioId) {
      return {
        ok: false,
        http: 403,
        codigo: 'PEDIDO_NO_ASIGNADO',
        error: 'El pedido está asignado a otro repartidor'
      };
    }
    return { ok: true };
  }

  return {
    ok: false,
    http: 403,
    codigo: 'PERMISO_DENEGADO',
    error: 'Rol no autorizado para cambiar el estado del pedido'
  };
}

function puedeVerPedido(usuario, pedido) {
  if (!usuario || !pedido) return false;
  if (usuario.rol === 'ADMINISTRADOR') return true;
  if (usuario.rol === 'CLIENTE') return pedido.cliente_id === usuario.id;
  if (usuario.rol === 'PICKER') {
    return !pedido.picker_id || pedido.picker_id === usuario.id;
  }
  if (usuario.rol === 'REPARTIDOR') {
    return !pedido.repartidor_id || pedido.repartidor_id === usuario.id;
  }
  return false;
}

module.exports = {
  ESTADOS,
  TRANSICIONES,
  evaluarTransicion,
  puedeVerPedido
};
