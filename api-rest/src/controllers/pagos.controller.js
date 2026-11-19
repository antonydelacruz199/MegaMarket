const crypto = require('node:crypto');
const pool = require('../database');
const { evaluarTransicion } = require('../pedidosEstados');
const {
  esUuid,
  mapearPago,
  mapearAjuste,
  construirQrContenido,
  nuevaReferenciaSimulada,
  nuevoNumeroOperacionSimulado
} = require('../pagos');
const {
  asegurarCuentaCliente,
  liquidarCobroPedido,
  liquidarDevolucionAjuste,
  liquidarDescuentoAjuste
} = require('../billetera');

const SELECT_PAGO = `
  id, uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad,
  estado, referencia_banco, numero_operacion_banco, qr_contenido,
  fecha_expiracion_qr, fecha_confirmacion, created_at, updated_at
`;

async function cargarPedido(clienteDb, pedidoId) {
  const { rows } = await clienteDb.query(
    `SELECT id, cliente_id, estado, total_centimos, picker_id, repartidor_id,
            franja_entrega_id, version, updated_at, deleted_at
     FROM pedidos
     WHERE id = $1`,
    [pedidoId]
  );
  return rows[0] || null;
}

async function cargarPagoPorId(clienteDb, pagoId, { forUpdate = false } = {}) {
  const { rows } = await clienteDb.query(
    `SELECT ${SELECT_PAGO}
     FROM pagos
     WHERE id = $1
     ${forUpdate ? 'FOR UPDATE' : ''}`,
    [pagoId]
  );
  return rows[0] || null;
}

async function cargarPagoPorPedido(clienteDb, pedidoId, { forUpdate = false } = {}) {
  const { rows } = await clienteDb.query(
    `SELECT ${SELECT_PAGO}
     FROM pagos
     WHERE pedido_id = $1
     ORDER BY created_at DESC
     LIMIT 1
     ${forUpdate ? 'FOR UPDATE' : ''}`,
    [pedidoId]
  );
  return rows[0] || null;
}

function puedeVerPago(usuario, pedido) {
  if (!usuario || !pedido) return false;
  if (usuario.rol === 'ADMINISTRADOR') return true;
  if (usuario.rol === 'CLIENTE') return pedido.cliente_id === usuario.id;
  if (usuario.rol === 'REPARTIDOR') {
    return !pedido.repartidor_id || pedido.repartidor_id === usuario.id;
  }
  if (usuario.rol === 'PICKER') {
    return !pedido.picker_id || pedido.picker_id === usuario.id;
  }
  return false;
}

function puedeConfirmarPago(usuario, pedido, pago) {
  if (!usuario || !pedido || !pago) {
    return { ok: false, http: 403, codigo: 'PERMISO_DENEGADO', error: 'No autorizado' };
  }
  if (usuario.rol === 'ADMINISTRADOR') return { ok: true };
  if (usuario.rol === 'CLIENTE') {
    if (pedido.cliente_id !== usuario.id) {
      return {
        ok: false,
        http: 403,
        codigo: 'PERMISO_DENEGADO',
        error: 'Solo el cliente del pedido puede confirmar este pago'
      };
    }
    if (pago.modalidad === 'ONLINE') {
      // ONLINE solo tras preparación validada (CONFIRMADO).
      if (pedido.estado !== 'CONFIRMADO') {
        return {
          ok: false,
          http: 409,
          codigo: 'PAGO_REQUIERE_CONFIRMACION',
          error:
            'El pago online solo está disponible cuando el pedido está CONFIRMADO tras la preparación'
        };
      }
      return { ok: true };
    }
    if (pago.modalidad === 'CONTRAENTREGA') {
      // Cliente puede pagar con billetera en entrega (alternativa al cobro del repartidor).
      if (!['LISTO', 'EN_CAMINO'].includes(pedido.estado)) {
        return {
          ok: false,
          http: 409,
          codigo: 'PEDIDO_NO_EN_ENTREGA',
          error: 'El pago contraentrega del cliente solo aplica cuando el pedido está en entrega'
        };
      }
      return { ok: true };
    }
    return {
      ok: false,
      http: 409,
      codigo: 'CONFIRMACION_NO_PERMITIDA',
      error: 'Modalidad de pago no confirmable por el cliente'
    };
  }
  if (usuario.rol === 'REPARTIDOR') {
    if (!pedido.repartidor_id || pedido.repartidor_id !== usuario.id) {
      return {
        ok: false,
        http: 403,
        codigo: 'PEDIDO_NO_ASIGNADO',
        error: 'El pedido no está asignado a este repartidor'
      };
    }
    if (pago.modalidad !== 'CONTRAENTREGA') {
      return {
        ok: false,
        http: 409,
        codigo: 'CONFIRMACION_NO_PERMITIDA',
        error: 'El pago online lo confirma el cliente'
      };
    }
    if (!['LISTO', 'EN_CAMINO'].includes(pedido.estado)) {
      return {
        ok: false,
        http: 409,
        codigo: 'PEDIDO_NO_EN_ENTREGA',
        error: 'Solo se confirma contraentrega cuando el pedido está en entrega'
      };
    }
    return { ok: true };
  }
  return {
    ok: false,
    http: 403,
    codigo: 'PERMISO_DENEGADO',
    error: 'Rol no autorizado para confirmar pagos'
  };
}

function normalizarFormaCobro(raw) {
  const v = String(raw || '').trim().toUpperCase();
  if (!v) return null;
  if (v === 'EFECTIVO' || v === 'BILLETERA') return v;
  return undefined;
}

async function cargarAutorizacionBilletera(clienteDb, pagoId, autorizacionUuid) {
  if (esUuid(autorizacionUuid)) {
    const { rows } = await clienteDb.query(
      `SELECT uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id, estado, respuesta
       FROM operaciones_idempotentes
       WHERE uuid_operacion = $1
         AND tipo_operacion = 'AUTORIZAR_BILLETERA'
         AND recurso = 'pagos'
         AND entidad_id = $2
       LIMIT 1`,
      [autorizacionUuid, pagoId]
    );
    return rows[0] || null;
  }
  // Sin uuid explícito: usar cualquier autorización vigente del pago.
  const { rows } = await clienteDb.query(
    `SELECT uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id, estado, respuesta
     FROM operaciones_idempotentes
     WHERE tipo_operacion = 'AUTORIZAR_BILLETERA'
       AND recurso = 'pagos'
       AND entidad_id = $1
       AND estado = 'PROCESADA'
     LIMIT 1`,
    [pagoId]
  );
  return rows[0] || null;
}

async function obtenerPorPedido(req, res) {
  try {
    const pedidoId = String(req.params.pedidoId || '').trim();
    if (!esUuid(pedidoId)) {
      return res.status(400).json({ error: 'pedidoId inválido', codigo: 'UUID_INVALIDO' });
    }
    const pedido = await cargarPedido(pool, pedidoId);
    if (!pedido || pedido.deleted_at) {
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }
    if (!puedeVerPago(req.usuario, pedido)) {
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    const pago = await cargarPagoPorPedido(pool, pedidoId);
    if (!pago) {
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }
    return res.status(200).json({ pago: mapearPago(pago), pedidoId: pedido.id });
  } catch (error) {
    console.error('Error al obtener pago:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el pago' });
  }
}

async function obtenerPorId(req, res) {
  try {
    const pagoId = String(req.params.id || '').trim();
    if (!esUuid(pagoId)) {
      return res.status(400).json({ error: 'id inválido', codigo: 'UUID_INVALIDO' });
    }
    const pago = await cargarPagoPorId(pool, pagoId);
    if (!pago) {
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }
    const pedido = await cargarPedido(pool, pago.pedido_id);
    if (!pedido || pedido.deleted_at || !puedeVerPago(req.usuario, pedido)) {
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    return res.status(200).json({ pago: mapearPago(pago) });
  } catch (error) {
    console.error('Error al obtener pago por id:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el pago' });
  }
}

/**
 * Genera QR informativo. No confirma el pago.
 * POST /api/pagos/qr  body: { pedidoId }
 */
async function generarQr(req, res) {
  const clienteDb = await pool.connect();
  try {
    const pedidoId = String(req.body.pedidoId || req.body.pedido_id || '').trim();
    if (!esUuid(pedidoId)) {
      return res.status(400).json({ error: 'pedidoId inválido', codigo: 'UUID_INVALIDO' });
    }

    await clienteDb.query('BEGIN');
    const pedido = await cargarPedido(clienteDb, pedidoId);
    if (!pedido || pedido.deleted_at) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }
    if (!puedeVerPago(req.usuario, pedido)) {
      await clienteDb.query('ROLLBACK');
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    if (['CANCELADO', 'ENTREGADO'].includes(pedido.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'El pedido no admite generación de QR',
        codigo: 'PEDIDO_NO_PAGABLE'
      });
    }

    const pago = await cargarPagoPorPedido(clienteDb, pedidoId, { forUpdate: true });
    if (!pago) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }
    if (pago.modalidad === 'ONLINE' && pedido.estado !== 'CONFIRMADO') {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error:
          'El QR online solo está disponible cuando el pedido está CONFIRMADO tras la preparación',
        codigo: 'PAGO_REQUIERE_CONFIRMACION'
      });
    }
    if (pago.estado === 'CONFIRMADO') {
      await clienteDb.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        yaConfirmado: true,
        pago: mapearPago(pago),
        simulacion: true
      });
    }
    // EXPIRADO se puede recuperar regenerando QR mientras el pedido siga vigente.
    if (['CANCELADO', 'RECHAZADO'].includes(pago.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: `El pago está ${pago.estado} y no admite QR`,
        codigo: 'PAGO_NO_PAGABLE'
      });
    }
    if (!['PENDIENTE', 'QR_GENERADO', 'EXPIRADO'].includes(pago.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: `El pago está ${pago.estado} y no admite QR`,
        codigo: 'PAGO_NO_PAGABLE'
      });
    }

    // Autorización de generación: cliente (ONLINE o propio), repartidor (CONTRAENTREGA asignado), admin.
    if (req.usuario.rol === 'CLIENTE' && pedido.cliente_id !== req.usuario.id) {
      await clienteDb.query('ROLLBACK');
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    if (req.usuario.rol === 'REPARTIDOR') {
      if (pago.modalidad !== 'CONTRAENTREGA') {
        await clienteDb.query('ROLLBACK');
        return res.status(409).json({
          error: 'El repartidor solo genera QR de contraentrega',
          codigo: 'QR_NO_PERMITIDO'
        });
      }
      if (!pedido.repartidor_id || pedido.repartidor_id !== req.usuario.id) {
        await clienteDb.query('ROLLBACK');
        return res.status(403).json({
          error: 'Pedido no asignado a este repartidor',
          codigo: 'PEDIDO_NO_ASIGNADO'
        });
      }
    }
    if (req.usuario.rol === 'PICKER') {
      await clienteDb.query('ROLLBACK');
      return res.status(403).json({ error: 'El picker no genera QR de cobro', codigo: 'PERMISO_DENEGADO' });
    }

    const generadoEn = new Date().toISOString();
    const referencia =
      pago.referencia_banco &&
      (String(pago.referencia_banco).startsWith('MM-REF-') ||
        String(pago.referencia_banco).startsWith('SIM-REF-'))
        ? pago.referencia_banco
        : nuevaReferenciaSimulada(pago.id);
    const qrContenido = construirQrContenido({
      pago,
      pedidoId: pedido.id,
      referencia,
      generadoEn
    });
    const expiracion = new Date(Date.now() + 60 * 60 * 1000).toISOString();

    const { rows } = await clienteDb.query(
      `UPDATE pagos
       SET estado = 'QR_GENERADO',
           referencia_banco = $2,
           qr_contenido = $3,
           fecha_expiracion_qr = $4,
           updated_at = now()
       WHERE id = $1
       RETURNING ${SELECT_PAGO}`,
      [pago.id, referencia, qrContenido, expiracion]
    );

    await clienteDb.query('COMMIT');
    return res.status(200).json({
      idempotente: Boolean(pago.qr_contenido),
      pago: mapearPago(rows[0]),
      qr: {
        contenido: qrContenido,
        referencia,
        importeCentimos: Number(rows[0].monto_centimos),
        pedidoId: pedido.id,
        generadoEn,
        expiraEn: expiracion,
        estado: 'QR_GENERADO',
        etiqueta: 'Código QR',
        aviso: 'Ver el código no confirma el pago.'
      },
      simulacion: true
    });
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al generar QR:', error.message);
    return res.status(500).json({ error: 'No se pudo generar el código QR' });
  } finally {
    clienteDb.release();
  }
}

/**
 * Confirma el pago: valida saldo cuando aplica, mueve billetera y marca CONFIRMADO.
 * No marca PAGADO solo por mostrar el QR.
 * POST /api/pagos/:id/confirmar
 * body: { uuidOperacion, formaCobro?, autorizacionClienteUuid? }
 * - ONLINE: debita billetera del cliente.
 * - CONTRAENTREGA + EFECTIVO (default): confirma sin debitar billetera.
 * - CONTRAENTREGA + BILLETERA: exige autorización previa del cliente.
 */
async function confirmar(req, res) {
  const clienteDb = await pool.connect();
  try {
    const pagoId = String(req.params.id || '').trim();
    const uuidOperacion = String(
      req.body.uuidOperacion || req.body.uuid_operacion || ''
    ).trim();
    const formaRaw = normalizarFormaCobro(
      req.body.formaCobro || req.body.forma_cobro || req.body.medioCobro
    );
    const autorizacionClienteUuid = String(
      req.body.autorizacionClienteUuid ||
        req.body.autorizacion_cliente_uuid ||
        req.body.autorizacionUuid ||
        ''
    ).trim();

    if (!esUuid(pagoId)) {
      return res.status(400).json({ error: 'id inválido', codigo: 'UUID_INVALIDO' });
    }
    if (!esUuid(uuidOperacion)) {
      return res.status(400).json({
        error: 'uuidOperacion es obligatorio y debe ser UUID',
        codigo: 'UUID_OPERACION_INVALIDO'
      });
    }
    if (formaRaw === undefined) {
      return res.status(400).json({
        error: 'formaCobro debe ser EFECTIVO o BILLETERA',
        codigo: 'FORMA_COBRO_INVALIDA'
      });
    }

    await clienteDb.query('BEGIN');

    // Idempotencia por uuid de confirmación en operaciones_idempotentes
    const previaOp = await clienteDb.query(
      `SELECT estado, codigo_http, respuesta
       FROM operaciones_idempotentes
       WHERE uuid_operacion = $1
       LIMIT 1`,
      [uuidOperacion]
    );
    if (previaOp.rows[0]) {
      await clienteDb.query('COMMIT');
      return res.status(previaOp.rows[0].codigo_http).json({
        ...previaOp.rows[0].respuesta,
        idempotente: true
      });
    }

    const pago = await cargarPagoPorId(clienteDb, pagoId, { forUpdate: true });
    if (!pago) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }

    const pedido = await cargarPedido(clienteDb, pago.pedido_id);
    if (!pedido || pedido.deleted_at) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }

    if (['CANCELADO', 'ENTREGADO'].includes(pedido.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'El pedido ya no admite confirmación de pago',
        codigo: 'PEDIDO_NO_PAGABLE'
      });
    }

    // Idempotencia antes de auth de estado: tras ONLINE el pedido ya puede ser PAGADO/LISTO.
    if (pago.estado === 'CONFIRMADO') {
      if (req.usuario.rol === 'CLIENTE' && pedido.cliente_id !== req.usuario.id) {
        await clienteDb.query('ROLLBACK');
        return res.status(403).json({
          error: 'Solo el cliente del pedido puede confirmar este pago',
          codigo: 'PERMISO_DENEGADO'
        });
      }
      if (
        req.usuario.rol !== 'CLIENTE' &&
        req.usuario.rol !== 'ADMINISTRADOR' &&
        req.usuario.rol !== 'REPARTIDOR'
      ) {
        await clienteDb.query('ROLLBACK');
        return res.status(403).json({
          error: 'Rol no autorizado para confirmar pagos',
          codigo: 'PERMISO_DENEGADO'
        });
      }
      const respuesta = {
        idempotente: true,
        yaConfirmado: true,
        pago: mapearPago(pago),
        pedido: { id: pedido.id, estado: pedido.estado },
        simulacion: true
      };
      await clienteDb.query(
        `INSERT INTO operaciones_idempotentes (
           uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
           estado, codigo_http, respuesta
         ) VALUES ($1,$2,'CONFIRMAR_PAGO','pagos',$3,'PROCESADA',200,$4::jsonb)
         ON CONFLICT (uuid_operacion) DO NOTHING`,
        [uuidOperacion, req.usuario.id, pago.id, JSON.stringify(respuesta)]
      );
      await clienteDb.query('COMMIT');
      return res.status(200).json(respuesta);
    }

    const auth = puedeConfirmarPago(req.usuario, pedido, pago);
    if (!auth.ok) {
      await clienteDb.query('ROLLBACK');
      return res.status(auth.http).json({ error: auth.error, codigo: auth.codigo });
    }

    // PENDIENTE / QR_GENERADO / EXPIRADO recuperable (sin cobro previo).
    if (!['PENDIENTE', 'QR_GENERADO', 'EXPIRADO'].includes(pago.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: `El pago en estado ${pago.estado} no se puede confirmar`,
        codigo: 'PAGO_NO_CONFIRMABLE'
      });
    }

    // QR vencido: marcar EXPIRADO y pedir regeneración (sin mover saldos).
    if (
      pago.fecha_expiracion_qr &&
      pago.estado === 'QR_GENERADO' &&
      new Date(pago.fecha_expiracion_qr).getTime() < Date.now()
    ) {
      await clienteDb.query(
        `UPDATE pagos SET estado = 'EXPIRADO', updated_at = now() WHERE id = $1`,
        [pago.id]
      );
      await clienteDb.query('COMMIT');
      return res.status(409).json({
        error: 'El código QR expiró; genera uno nuevo o paga sin QR',
        codigo: 'QR_EXPIRADO'
      });
    }

    const esContraentrega = pago.modalidad === 'CONTRAENTREGA';
    // Cliente que paga su propia contraentrega: billetera. Repartidor: efectivo por defecto.
    const formaCobro = esContraentrega
      ? formaRaw || (req.usuario.rol === 'CLIENTE' ? 'BILLETERA' : 'EFECTIVO')
      : 'BILLETERA';
    let liquidacion = {
      ok: true,
      idempotente: false,
      saldoCliente: null,
      saldoComercio: null,
      movimientos: []
    };

    if (formaCobro === 'BILLETERA') {
      if (esContraentrega && req.usuario.rol !== 'CLIENTE') {
        const autorizacion = await cargarAutorizacionBilletera(
          clienteDb,
          pago.id,
          autorizacionClienteUuid
        );
        if (!autorizacion || autorizacion.estado !== 'PROCESADA') {
          await clienteDb.query('ROLLBACK');
          return res.status(409).json({
            error:
              'Para debitar la billetera del cliente se requiere su autorización verificable',
            codigo: 'AUTORIZACION_CLIENTE_REQUERIDA'
          });
        }
        if (autorizacion.usuario_id !== pedido.cliente_id) {
          await clienteDb.query('ROLLBACK');
          return res.status(409).json({
            error: 'La autorización no pertenece al cliente del pedido',
            codigo: 'AUTORIZACION_CLIENTE_INVALIDA'
          });
        }
      }

      await asegurarCuentaCliente(clienteDb, pedido.cliente_id);
      liquidacion = await liquidarCobroPedido(clienteDb, {
        clienteUsuarioId: pedido.cliente_id,
        montoCentimos: Number(pago.monto_centimos),
        pagoId: pago.id,
        pedidoId: pedido.id,
        uuidOperacionDebito: uuidOperacion,
        uuidOperacionCredito: crypto.randomUUID()
      });

      if (!liquidacion.ok) {
        await clienteDb.query('ROLLBACK');
        return res.status(liquidacion.http || 409).json({
          error: liquidacion.error,
          codigo: liquidacion.codigo,
          saldoCentimos: liquidacion.saldoCentimos,
          montoCentimos: liquidacion.montoCentimos
        });
      }
    }
    // EFECTIVO: confirma cobro sin tocar billeteras.

    const numeroOp = nuevoNumeroOperacionSimulado(uuidOperacion);
    const confirmadoEn = new Date().toISOString();
    const { rows: pagoRows } = await clienteDb.query(
      `UPDATE pagos
       SET estado = 'CONFIRMADO',
           numero_operacion_banco = $2,
           fecha_confirmacion = $3,
           updated_at = now()
       WHERE id = $1
       RETURNING ${SELECT_PAGO}`,
      [pago.id, numeroOp, confirmadoEn]
    );
    const pagoConfirmado = pagoRows[0];

    // ONLINE en CONFIRMADO → PAGADO → LISTO (despacho). No retroceder EN_CAMINO/LISTO.
    let pedidoEstado = pedido.estado;
    if (pedido.estado === 'CONFIRMADO' && pago.modalidad === 'ONLINE') {
      const evalT = evaluarTransicion({
        desde: pedido.estado,
        hacia: 'PAGADO',
        rol: 'ADMINISTRADOR',
        usuarioId: req.usuario.id,
        pedido,
        pago: pagoConfirmado,
        origenInternoBancario: true
      });
      if (evalT.ok) {
        await clienteDb.query(
          `UPDATE pedidos SET estado = 'PAGADO', updated_at = now() WHERE id = $1`,
          [pedido.id]
        );
        const evalListo = evaluarTransicion({
          desde: 'PAGADO',
          hacia: 'LISTO',
          rol: 'ADMINISTRADOR',
          usuarioId: req.usuario.id,
          pedido: { ...pedido, estado: 'PAGADO' },
          pago: pagoConfirmado,
          origenInternoBancario: false,
          origenInternoPreparacion: true
        });
        if (evalListo.ok) {
          const { rows: pedRows } = await clienteDb.query(
            `UPDATE pedidos SET estado = 'LISTO', updated_at = now()
             WHERE id = $1
             RETURNING estado`,
            [pedido.id]
          );
          pedidoEstado = pedRows[0].estado;
        } else {
          pedidoEstado = 'PAGADO';
        }
      }
    }

    const respuesta = {
      idempotente: Boolean(liquidacion.idempotente),
      pago: mapearPago(pagoConfirmado),
      pedido: { id: pedido.id, estado: pedidoEstado },
      formaCobro,
      billetera:
        formaCobro === 'BILLETERA'
          ? {
              saldoClienteCentimos: liquidacion.saldoCliente,
              saldoComercioCentimos: liquidacion.saldoComercio
            }
          : null,
      movimientos: liquidacion.movimientos || [],
      simulacion: true,
      aviso:
        formaCobro === 'EFECTIVO'
          ? 'Cobro en efectivo confirmado sin movimiento de billetera.'
          : 'Pago confirmado.'
    };

    await clienteDb.query(
      `INSERT INTO operaciones_idempotentes (
         uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
         estado, codigo_http, respuesta
       ) VALUES ($1,$2,'CONFIRMAR_PAGO','pagos',$3,'PROCESADA',200,$4::jsonb)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [uuidOperacion, req.usuario.id, pago.id, JSON.stringify(respuesta)]
    );

    await clienteDb.query('COMMIT');
    return res.status(200).json(respuesta);
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'Confirmación duplicada o referencia ya usada',
        codigo: 'PAGO_DUPLICADO'
      });
    }
    console.error('Error al confirmar pago:', error.message);
    return res.status(500).json({ error: 'No se pudo confirmar el pago' });
  } finally {
    clienteDb.release();
  }
}

/**
 * El cliente autoriza un cobro CONTRAENTREGA contra su billetera.
 * El repartidor debe presentar este uuid al confirmar con formaCobro=BILLETERA.
 * POST /api/pagos/:id/autorizar-billetera  body: { uuidOperacion }
 */
async function autorizarBilletera(req, res) {
  const clienteDb = await pool.connect();
  try {
    const pagoId = String(req.params.id || '').trim();
    const uuidOperacion = String(
      req.body.uuidOperacion || req.body.uuid_operacion || ''
    ).trim();

    if (!esUuid(pagoId) || !esUuid(uuidOperacion)) {
      return res.status(400).json({
        error: 'id y uuidOperacion deben ser UUID',
        codigo: 'UUID_INVALIDO'
      });
    }
    if (req.usuario.rol !== 'CLIENTE' && req.usuario.rol !== 'ADMINISTRADOR') {
      return res.status(403).json({
        error: 'Solo el cliente puede autorizar el débito de su billetera',
        codigo: 'PERMISO_DENEGADO'
      });
    }

    await clienteDb.query('BEGIN');

    const previaOp = await clienteDb.query(
      `SELECT codigo_http, respuesta FROM operaciones_idempotentes
       WHERE uuid_operacion = $1 LIMIT 1`,
      [uuidOperacion]
    );
    if (previaOp.rows[0]) {
      await clienteDb.query('COMMIT');
      return res.status(previaOp.rows[0].codigo_http).json({
        ...previaOp.rows[0].respuesta,
        idempotente: true
      });
    }

    const pago = await cargarPagoPorId(clienteDb, pagoId, { forUpdate: true });
    if (!pago) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }
    const pedido = await cargarPedido(clienteDb, pago.pedido_id);
    if (!pedido || pedido.deleted_at) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }
    if (req.usuario.rol === 'CLIENTE' && pedido.cliente_id !== req.usuario.id) {
      await clienteDb.query('ROLLBACK');
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    if (pago.modalidad !== 'CONTRAENTREGA') {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'La autorización de billetera aplica solo a contraentrega',
        codigo: 'AUTORIZACION_NO_APLICA'
      });
    }
    if (pago.estado === 'CONFIRMADO') {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'El pago ya está confirmado',
        codigo: 'PAGO_YA_CONFIRMADO'
      });
    }
    if (!['LISTO', 'EN_CAMINO'].includes(pedido.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se autoriza el cobro durante la entrega',
        codigo: 'PEDIDO_NO_EN_ENTREGA'
      });
    }
    if (['CANCELADO', 'RECHAZADO'].includes(pago.estado)) {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: `El pago está ${pago.estado}`,
        codigo: 'PAGO_NO_AUTORIZABLE'
      });
    }

    await asegurarCuentaCliente(clienteDb, pedido.cliente_id);

    const respuesta = {
      autorizacionUuid: uuidOperacion,
      pagoId: pago.id,
      pedidoId: pedido.id,
      montoCentimos: Number(pago.monto_centimos),
      vigencia: 'Hasta confirmar el cobro o cancelar el pedido',
      simulacion: true,
      aviso: 'Autorización registrada. El repartidor puede confirmar el cobro con billetera.'
    };

    await clienteDb.query(
      `INSERT INTO operaciones_idempotentes (
         uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
         estado, codigo_http, respuesta
       ) VALUES ($1,$2,'AUTORIZAR_BILLETERA','pagos',$3,'PROCESADA',200,$4::jsonb)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [uuidOperacion, pedido.cliente_id, pago.id, JSON.stringify(respuesta)]
    );

    await clienteDb.query('COMMIT');
    return res.status(200).json(respuesta);
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al autorizar billetera:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar la autorización' });
  } finally {
    clienteDb.release();
  }
}

/**
 * Confirma un ajuste_pago pendiente (devolución/descuento) de forma controlada.
 * POST /api/pagos/ajustes/:id/confirmar  body: { uuidOperacion }
 */
async function confirmarAjuste(req, res) {
  const clienteDb = await pool.connect();
  try {
    const ajusteId = String(req.params.id || '').trim();
    const uuidOperacion = String(
      req.body.uuidOperacion || req.body.uuid_operacion || ''
    ).trim();

    if (!esUuid(ajusteId) || !esUuid(uuidOperacion)) {
      return res.status(400).json({
        error: 'id y uuidOperacion deben ser UUID',
        codigo: 'UUID_INVALIDO'
      });
    }

    if (!['ADMINISTRADOR', 'CLIENTE'].includes(req.usuario.rol)) {
      return res.status(403).json({
        error: 'Solo administrador o cliente pueden confirmar ajustes',
        codigo: 'PERMISO_DENEGADO'
      });
    }

    await clienteDb.query('BEGIN');

    const previaOp = await clienteDb.query(
      `SELECT codigo_http, respuesta FROM operaciones_idempotentes
       WHERE uuid_operacion = $1 LIMIT 1`,
      [uuidOperacion]
    );
    if (previaOp.rows[0]) {
      await clienteDb.query('COMMIT');
      return res.status(previaOp.rows[0].codigo_http).json({
        ...previaOp.rows[0].respuesta,
        idempotente: true
      });
    }

    const { rows: ajustes } = await clienteDb.query(
      `SELECT * FROM ajustes_pago WHERE id = $1 FOR UPDATE`,
      [ajusteId]
    );
    const ajuste = ajustes[0];
    if (!ajuste) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Ajuste no encontrado', codigo: 'AJUSTE_NO_ENCONTRADO' });
    }

    const pago = await cargarPagoPorId(clienteDb, ajuste.pago_id, { forUpdate: true });
    if (!pago) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pago no encontrado', codigo: 'PAGO_NO_ENCONTRADO' });
    }
    const pedido = await cargarPedido(clienteDb, pago.pedido_id);
    if (!pedido || pedido.deleted_at) {
      await clienteDb.query('ROLLBACK');
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }
    if (req.usuario.rol === 'CLIENTE' && pedido.cliente_id !== req.usuario.id) {
      await clienteDb.query('ROLLBACK');
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }

    if (ajuste.estado === 'CONFIRMADO') {
      const respuesta = {
        idempotente: true,
        yaConfirmado: true,
        ajuste: mapearAjuste(ajuste),
        simulacion: true
      };
      await clienteDb.query(
        `INSERT INTO operaciones_idempotentes (
           uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
           estado, codigo_http, respuesta
         ) VALUES ($1,$2,'CONFIRMAR_AJUSTE','ajustes_pago',$3,'PROCESADA',200,$4::jsonb)
         ON CONFLICT (uuid_operacion) DO NOTHING`,
        [uuidOperacion, req.usuario.id, ajuste.id, JSON.stringify(respuesta)]
      );
      await clienteDb.query('COMMIT');
      return res.status(200).json(respuesta);
    }

    if (ajuste.estado !== 'PENDIENTE') {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: `El ajuste en estado ${ajuste.estado} no se puede confirmar`,
        codigo: 'AJUSTE_NO_CONFIRMABLE'
      });
    }

    if (pago.estado !== 'CONFIRMADO') {
      await clienteDb.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se restituyen saldos si el pago original está CONFIRMADO',
        codigo: 'PAGO_NO_CONFIRMADO'
      });
    }

    let liquidacion;
    if (ajuste.tipo === 'DEVOLUCION') {
      liquidacion = await liquidarDevolucionAjuste(clienteDb, {
        clienteUsuarioId: pedido.cliente_id,
        montoCentimos: Number(ajuste.monto_centimos),
        pagoId: pago.id,
        ajustePagoId: ajuste.id,
        pedidoId: pedido.id,
        uuidOperacionCredito: uuidOperacion,
        uuidOperacionDebito: crypto.randomUUID()
      });
    } else if (ajuste.tipo === 'DESCUENTO') {
      liquidacion = await liquidarDescuentoAjuste(clienteDb, {
        clienteUsuarioId: pedido.cliente_id,
        montoCentimos: Number(ajuste.monto_centimos),
        pagoId: pago.id,
        ajustePagoId: ajuste.id,
        pedidoId: pedido.id,
        uuidOperacionDebito: uuidOperacion,
        uuidOperacionCredito: crypto.randomUUID()
      });
    } else {
      await clienteDb.query('ROLLBACK');
      return res.status(400).json({ error: 'Tipo de ajuste no soportado', codigo: 'TIPO_INVALIDO' });
    }

    if (!liquidacion.ok) {
      await clienteDb.query('ROLLBACK');
      return res.status(liquidacion.http || 409).json({
        error: liquidacion.error,
        codigo: liquidacion.codigo
      });
    }

    const numeroOp = nuevoNumeroOperacionSimulado(uuidOperacion);
    const confirmadoEn = new Date().toISOString();
    const { rows: ajRows } = await clienteDb.query(
      `UPDATE ajustes_pago
       SET estado = 'CONFIRMADO',
           numero_operacion_banco = $2,
           referencia_banco = COALESCE(referencia_banco, $3),
           fecha_confirmacion = $4,
           updated_at = now()
       WHERE id = $1
       RETURNING *`,
      [ajuste.id, numeroOp, `MM-AJ-${ajuste.id.replace(/-/g, '').slice(0, 12)}`, confirmadoEn]
    );

    const respuesta = {
      idempotente: Boolean(liquidacion.idempotente),
      ajuste: mapearAjuste(ajRows[0]),
      pago: mapearPago(pago),
      movimientos: liquidacion.movimientos || [],
      billetera: {
        saldoClienteCentimos: liquidacion.saldoCliente,
        saldoComercioCentimos: liquidacion.saldoComercio
      },
      simulacion: true
    };

    await clienteDb.query(
      `INSERT INTO operaciones_idempotentes (
         uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
         estado, codigo_http, respuesta
       ) VALUES ($1,$2,'CONFIRMAR_AJUSTE','ajustes_pago',$3,'PROCESADA',200,$4::jsonb)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [uuidOperacion, req.usuario.id, ajuste.id, JSON.stringify(respuesta)]
    );

    await clienteDb.query('COMMIT');
    return res.status(200).json(respuesta);
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'Ajuste ya liquidado o operación duplicada',
        codigo: 'AJUSTE_DUPLICADO'
      });
    }
    console.error('Error al confirmar ajuste:', error.message);
    return res.status(500).json({ error: 'No se pudo confirmar el ajuste' });
  } finally {
    clienteDb.release();
  }
}

module.exports = {
  obtenerPorPedido,
  obtenerPorId,
  generarQr,
  confirmar,
  confirmarAjuste,
  autorizarBilletera
};
