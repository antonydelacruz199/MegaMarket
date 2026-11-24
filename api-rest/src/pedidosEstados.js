/**
 * Reglas de transición de pedidos.estado.
 * Flujo: RECIBIDO → EN_PICKING → CONFIRMADO → (PAGADO si ONLINE) → LISTO → EN_CAMINO → ENTREGADO
 * CONFIRMADO = preparación validada (no creación).
 * PAGADO solo tras cobro ONLINE verificado en servidor.
 */

const ESTADOS = Object.freeze([
  'RECIBIDO',
  'CONFIRMADO',
  'PAGADO',
  'EN_PICKING',
  'LISTO',
  'EN_CAMINO',
  'ENTREGADO',
  'CANCELADO'
]);

const TRANSICIONES = Object.freeze({
  RECIBIDO: new Set(['EN_PICKING', 'CANCELADO']),
  EN_PICKING: new Set(['CONFIRMADO', 'CANCELADO']),
  CONFIRMADO: new Set(['PAGADO', 'LISTO', 'CANCELADO']),
  PAGADO: new Set(['LISTO', 'CANCELADO']),
  LISTO: new Set(['EN_CAMINO', 'CANCELADO']),
  EN_CAMINO: new Set(['ENTREGADO', 'CANCELADO']),
  ENTREGADO: new Set(),
  CANCELADO: new Set()
});

/**
 * @param {object} ctx
 * @param {boolean} [ctx.origenInternoBancario]
 * @param {boolean} [ctx.devolucionLiquidada]
 * @param {boolean} [ctx.origenInternoPreparacion] — cierre de picking / sistema
 */
function evaluarTransicion(ctx) {
  const {
    desde,
    hacia,
    rol,
    usuarioId,
    pedido,
    pago,
    origenInternoBancario,
    devolucionLiquidada,
    origenInternoPreparacion
  } = ctx;

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
    if (pago.modalidad !== 'ONLINE') {
      return {
        ok: false,
        http: 409,
        codigo: 'PAGADO_SOLO_ONLINE',
        error: 'PAGADO aplica solo a pedidos ONLINE'
      };
    }
  }

  // CONFIRMADO = preparación completa (picker o job interno).
  if (hacia === 'CONFIRMADO' && desde === 'EN_PICKING') {
    if (rol === 'PICKER' || rol === 'ADMINISTRADOR' || origenInternoPreparacion) {
      // ok más abajo por rol
    } else {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'Solo el picker puede confirmar la preparación'
      };
    }
  }

  // LISTO desde CONFIRMADO: CONTRAENTREGA (sin cobro previo) o sistema tras PAGADO.
  if (hacia === 'LISTO' && desde === 'CONFIRMADO') {
    const modalidad = pago ? pago.modalidad : null;
    if (modalidad === 'ONLINE' && !origenInternoPreparacion) {
      return {
        ok: false,
        http: 409,
        codigo: 'LISTO_REQUIERE_PAGO',
        error: 'El pago online debe confirmarse antes de despachar'
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

  if (hacia === 'CANCELADO') {
    const pagoConfirmado = pago && pago.estado === 'CONFIRMADO';
    if ((pagoConfirmado || desde === 'PAGADO') && !devolucionLiquidada) {
      return {
        ok: false,
        http: 409,
        codigo: 'CANCELACION_REQUIERE_DEVOLUCION',
        error:
          'No se puede cancelar un pedido con pago confirmado sin registrar la devolución bancaria'
      };
    }
  }

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
    // Cancelación cliente: solo antes de cerrar preparación (CONFIRMADO).
    if (desde === 'CONFIRMADO' || desde === 'PAGADO') {
      return {
        ok: false,
        http: 409,
        codigo: 'CANCELACION_NO_PERMITIDA',
        error:
          'El pedido ya está confirmado por el picker; ya no se puede cancelar'
      };
    }
    if (!['RECIBIDO', 'EN_PICKING'].includes(desde)) {
      return {
        ok: false,
        http: 409,
        codigo: 'CANCELACION_NO_PERMITIDA',
        error:
          'Solo se puede cancelar antes de confirmar la preparación; en ruta requiere gestión administrativa'
      };
    }
    return { ok: true };
  }

  if (rol === 'PICKER') {
    if (!['EN_PICKING', 'CONFIRMADO', 'LISTO'].includes(hacia)) {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'El picker no puede aplicar esa transición'
      };
    }
    if (hacia === 'EN_PICKING') {
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
    if (hacia === 'CONFIRMADO' || hacia === 'LISTO') {
      if (!pedido.picker_id || pedido.picker_id !== usuarioId) {
        return {
          ok: false,
          http: 403,
          codigo: 'PEDIDO_NO_ASIGNADO',
          error: 'Solo el picker asignado puede cerrar la preparación'
        };
      }
      return { ok: true };
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
