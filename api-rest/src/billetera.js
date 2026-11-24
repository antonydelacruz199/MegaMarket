/**
 * Billetera interna (céntimos enteros, sin Float/Double).
 * Ledger PostgreSQL; sin pasarelas externas.
 */

const crypto = require('node:crypto');

/** S/ 1_000_000_000 en céntimos */
const SALDO_INICIAL_CLIENTE_CENTIMOS = 100_000_000_000;
/** S/ 500_000 en céntimos */
const SALDO_INICIAL_COMERCIO_CENTIMOS = 50_000_000;
const CODIGO_COMERCIO = 'MEGAMARKET';

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

function mapearCuenta(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    tipo: fila.tipo,
    usuarioId: fila.usuario_id || null,
    codigo: fila.codigo || null,
    saldoCentimos: Number(fila.saldo_centimos),
    moneda: fila.moneda,
    simulacion: true,
    updatedAt: fila.updated_at
  };
}

function mapearMovimiento(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    cuentaId: fila.cuenta_id,
    tipo: fila.tipo,
    sentido: fila.sentido,
    montoCentimos: Number(fila.monto_centimos),
    saldoPosteriorCentimos: Number(fila.saldo_posterior_centimos),
    pagoId: fila.pago_id || null,
    ajustePagoId: fila.ajuste_pago_id || null,
    pedidoId: fila.pedido_id || null,
    descripcion: fila.descripcion || null,
    createdAt: fila.created_at,
    simulacion: true
  };
}

async function asegurarCuentaComercio(clienteDb) {
  const existente = await clienteDb.query(
    `SELECT * FROM cuentas_billetera
     WHERE tipo = 'COMERCIO' AND codigo = $1
     LIMIT 1`,
    [CODIGO_COMERCIO]
  );
  if (existente.rows[0]) return existente.rows[0];

  try {
    const insertado = await clienteDb.query(
      `INSERT INTO cuentas_billetera (tipo, codigo, saldo_centimos, moneda)
       VALUES ('COMERCIO', $1, $2, 'PEN')
       RETURNING *`,
      [CODIGO_COMERCIO, SALDO_INICIAL_COMERCIO_CENTIMOS]
    );
    return insertado.rows[0];
  } catch (error) {
    if (error && error.code !== '23505') throw error;
  }

  const reintento = await clienteDb.query(
    `SELECT * FROM cuentas_billetera
     WHERE tipo = 'COMERCIO' AND codigo = $1
     LIMIT 1`,
    [CODIGO_COMERCIO]
  );
  return reintento.rows[0];
}

async function asegurarCuentaCliente(clienteDb, usuarioId) {
  const existente = await clienteDb.query(
    `SELECT * FROM cuentas_billetera
     WHERE tipo = 'CLIENTE' AND usuario_id = $1
     LIMIT 1`,
    [usuarioId]
  );
  if (existente.rows[0]) return existente.rows[0];

  let creada = null;
  try {
    const insertado = await clienteDb.query(
      `INSERT INTO cuentas_billetera (tipo, usuario_id, saldo_centimos, moneda)
       VALUES ('CLIENTE', $1, $2, 'PEN')
       RETURNING *`,
      [usuarioId, SALDO_INICIAL_CLIENTE_CENTIMOS]
    );
    creada = insertado.rows[0];
  } catch (error) {
    if (error && error.code !== '23505') throw error;
  }

  if (creada) {
    await clienteDb.query(
      `INSERT INTO movimientos_billetera (
         uuid_operacion, cuenta_id, tipo, sentido, monto_centimos,
         saldo_posterior_centimos, descripcion
       ) VALUES ($1, $2, 'ABONO_INICIAL', 'CREDITO', $3, $3, $4)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [
        crypto.randomUUID(),
        creada.id,
        SALDO_INICIAL_CLIENTE_CENTIMOS,
        'Saldo inicial del cliente'
      ]
    );
    return creada;
  }

  const reintento = await clienteDb.query(
    `SELECT * FROM cuentas_billetera
     WHERE tipo = 'CLIENTE' AND usuario_id = $1
     LIMIT 1`,
    [usuarioId]
  );
  return reintento.rows[0];
}

async function bloquearCuenta(clienteDb, cuentaId) {
  const { rows } = await clienteDb.query(
    `SELECT * FROM cuentas_billetera WHERE id = $1 FOR UPDATE`,
    [cuentaId]
  );
  return rows[0] || null;
}

/**
 * Orden estable de bloqueo: comercio primero, luego cliente (anti-deadlock).
 */
async function bloquearCuentasCobro(clienteDb, cuentaClienteId, cuentaComercioId) {
  const comercio = await bloquearCuenta(clienteDb, cuentaComercioId);
  const cliente = await bloquearCuenta(clienteDb, cuentaClienteId);
  return { cliente, comercio };
}

async function insertarMovimiento(clienteDb, datos) {
  const { rows } = await clienteDb.query(
    `INSERT INTO movimientos_billetera (
       uuid_operacion, cuenta_id, tipo, sentido, monto_centimos,
       saldo_posterior_centimos, pago_id, ajuste_pago_id, pedido_id, descripcion
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
     ON CONFLICT (uuid_operacion) DO NOTHING
     RETURNING *`,
    [
      datos.uuidOperacion,
      datos.cuentaId,
      datos.tipo,
      datos.sentido,
      datos.montoCentimos,
      datos.saldoPosteriorCentimos,
      datos.pagoId || null,
      datos.ajustePagoId || null,
      datos.pedidoId || null,
      datos.descripcion || null
    ]
  );
  return rows[0] || null;
}

/**
 * Débito cliente + crédito comercio por cobro de pedido.
 * Requiere transacción abierta y pago bloqueado por el llamador.
 * @returns {{ ok, codigo?, error?, http?, movimientos?, saldoCliente?, saldoComercio?, idempotente? }}
 */
async function liquidarCobroPedido(clienteDb, ctx) {
  const monto = Number(ctx.montoCentimos);
  if (!Number.isInteger(monto) || monto <= 0) {
    return {
      ok: false,
      http: 400,
      codigo: 'MONTO_INVALIDO',
      error: 'El monto del cobro debe ser un entero positivo en céntimos'
    };
  }

  const prev = await clienteDb.query(
    `SELECT * FROM movimientos_billetera
     WHERE pago_id = $1 AND tipo = 'COBRO_PEDIDO'
     LIMIT 1`,
    [ctx.pagoId]
  );
  if (prev.rows[0]) {
    const cuenta = await clienteDb.query(
      `SELECT * FROM cuentas_billetera WHERE id = $1`,
      [prev.rows[0].cuenta_id]
    );
    const comercio = await asegurarCuentaComercio(clienteDb);
    return {
      ok: true,
      idempotente: true,
      saldoCliente: Number(cuenta.rows[0]?.saldo_centimos || 0),
      saldoComercio: Number(comercio.saldo_centimos),
      movimientos: [mapearMovimiento(prev.rows[0])]
    };
  }

  await asegurarCuentaCliente(clienteDb, ctx.clienteUsuarioId);
  const comercioBase = await asegurarCuentaComercio(clienteDb);
  const cuentaClienteBase = await clienteDb.query(
    `SELECT id FROM cuentas_billetera WHERE tipo = 'CLIENTE' AND usuario_id = $1`,
    [ctx.clienteUsuarioId]
  );
  const { cliente, comercio } = await bloquearCuentasCobro(
    clienteDb,
    cuentaClienteBase.rows[0].id,
    comercioBase.id
  );

  const saldoCliente = Number(cliente.saldo_centimos);
  if (saldoCliente < monto) {
    return {
      ok: false,
      http: 409,
      codigo: 'SALDO_INSUFICIENTE',
      error: 'Saldo insuficiente para confirmar el pago',
      saldoCentimos: saldoCliente,
      montoCentimos: monto
    };
  }

  const nuevoCliente = saldoCliente - monto;
  const nuevoComercio = Number(comercio.saldo_centimos) + monto;

  await clienteDb.query(
    `UPDATE cuentas_billetera
     SET saldo_centimos = $2, updated_at = now()
     WHERE id = $1`,
    [cliente.id, nuevoCliente]
  );
  await clienteDb.query(
    `UPDATE cuentas_billetera
     SET saldo_centimos = $2, updated_at = now()
     WHERE id = $1`,
    [comercio.id, nuevoComercio]
  );

  const uuidDebito = ctx.uuidOperacionDebito || crypto.randomUUID();
  const uuidCredito = ctx.uuidOperacionCredito || crypto.randomUUID();

  const movDebito = await insertarMovimiento(clienteDb, {
    uuidOperacion: uuidDebito,
    cuentaId: cliente.id,
    tipo: 'COBRO_PEDIDO',
    sentido: 'DEBITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoCliente,
    pagoId: ctx.pagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Cobro del pedido'
  });

  const movCredito = await insertarMovimiento(clienteDb, {
    uuidOperacion: uuidCredito,
    cuentaId: comercio.id,
    tipo: 'ACREDITACION_COMERCIO',
    sentido: 'CREDITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoComercio,
    pagoId: ctx.pagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Acreditación MegaMarket'
  });

  if (!movDebito) {
    // Carrera: otro proceso insertó el cobro; releer.
    const otra = await clienteDb.query(
      `SELECT * FROM movimientos_billetera
       WHERE pago_id = $1 AND tipo = 'COBRO_PEDIDO'
       LIMIT 1`,
      [ctx.pagoId]
    );
    if (otra.rows[0]) {
      return {
        ok: true,
        idempotente: true,
        saldoCliente: Number(
          (
            await clienteDb.query(`SELECT saldo_centimos FROM cuentas_billetera WHERE id=$1`, [
              cliente.id
            ])
          ).rows[0].saldo_centimos
        ),
        saldoComercio: Number(
          (
            await clienteDb.query(`SELECT saldo_centimos FROM cuentas_billetera WHERE id=$1`, [
              comercio.id
            ])
          ).rows[0].saldo_centimos
        ),
        movimientos: [mapearMovimiento(otra.rows[0])]
      };
    }
    return {
      ok: false,
      http: 409,
      codigo: 'COBRO_CONCURRENTE',
      error: 'No se pudo registrar el cobro de forma exclusiva'
    };
  }

  return {
    ok: true,
    idempotente: false,
    saldoCliente: nuevoCliente,
    saldoComercio: nuevoComercio,
    movimientos: [mapearMovimiento(movDebito), mapearMovimiento(movCredito)]
  };
}

/**
 * Restituye saldo al cliente debitando comercio (devolución de ajuste).
 */
async function liquidarDevolucionAjuste(clienteDb, ctx) {
  const monto = Number(ctx.montoCentimos);
  if (!Number.isInteger(monto) || monto <= 0) {
    return {
      ok: false,
      http: 400,
      codigo: 'MONTO_INVALIDO',
      error: 'El monto de la devolución debe ser un entero positivo en céntimos'
    };
  }

  const prev = await clienteDb.query(
    `SELECT * FROM movimientos_billetera
     WHERE ajuste_pago_id = $1 AND tipo = 'DEVOLUCION_CLIENTE'
     LIMIT 1`,
    [ctx.ajustePagoId]
  );
  if (prev.rows[0]) {
    return { ok: true, idempotente: true, movimientos: [mapearMovimiento(prev.rows[0])] };
  }

  await asegurarCuentaCliente(clienteDb, ctx.clienteUsuarioId);
  const comercioBase = await asegurarCuentaComercio(clienteDb);
  const cuentaClienteBase = await clienteDb.query(
    `SELECT id FROM cuentas_billetera WHERE tipo = 'CLIENTE' AND usuario_id = $1`,
    [ctx.clienteUsuarioId]
  );
  const { cliente, comercio } = await bloquearCuentasCobro(
    clienteDb,
    cuentaClienteBase.rows[0].id,
    comercioBase.id
  );

  const saldoComercio = Number(comercio.saldo_centimos);
  if (saldoComercio < monto) {
    return {
      ok: false,
      http: 409,
      codigo: 'SALDO_COMERCIO_INSUFICIENTE',
      error: 'La cuenta comercial no tiene saldo para la devolución'
    };
  }

  const nuevoComercio = saldoComercio - monto;
  const nuevoCliente = Number(cliente.saldo_centimos) + monto;

  await clienteDb.query(
    `UPDATE cuentas_billetera SET saldo_centimos = $2, updated_at = now() WHERE id = $1`,
    [comercio.id, nuevoComercio]
  );
  await clienteDb.query(
    `UPDATE cuentas_billetera SET saldo_centimos = $2, updated_at = now() WHERE id = $1`,
    [cliente.id, nuevoCliente]
  );

  const movCliente = await insertarMovimiento(clienteDb, {
    uuidOperacion: ctx.uuidOperacionCredito || crypto.randomUUID(),
    cuentaId: cliente.id,
    tipo: 'DEVOLUCION_CLIENTE',
    sentido: 'CREDITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoCliente,
    pagoId: ctx.pagoId,
    ajustePagoId: ctx.ajustePagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Devolución simulada controlada'
  });

  await insertarMovimiento(clienteDb, {
    uuidOperacion: ctx.uuidOperacionDebito || crypto.randomUUID(),
    cuentaId: comercio.id,
    tipo: 'DEBITO_COMERCIO_DEVOLUCION',
    sentido: 'DEBITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoComercio,
    pagoId: ctx.pagoId,
    ajustePagoId: ctx.ajustePagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Débito comercial por devolución simulada'
  });

  return {
    ok: true,
    idempotente: false,
    saldoCliente: nuevoCliente,
    saldoComercio: nuevoComercio,
    movimientos: [mapearMovimiento(movCliente)]
  };
}

/**
 * Cobro adicional al cliente por DESCUENTO de ajuste (p. ej. sustituto más caro).
 */
async function liquidarDescuentoAjuste(clienteDb, ctx) {
  const monto = Number(ctx.montoCentimos);
  if (!Number.isInteger(monto) || monto <= 0) {
    return {
      ok: false,
      http: 400,
      codigo: 'MONTO_INVALIDO',
      error: 'El monto del descuento debe ser un entero positivo en céntimos'
    };
  }

  const prev = await clienteDb.query(
    `SELECT * FROM movimientos_billetera
     WHERE ajuste_pago_id = $1 AND tipo = 'AJUSTE_DESCUENTO_CLIENTE'
     LIMIT 1`,
    [ctx.ajustePagoId]
  );
  if (prev.rows[0]) {
    return { ok: true, idempotente: true, movimientos: [mapearMovimiento(prev.rows[0])] };
  }

  await asegurarCuentaCliente(clienteDb, ctx.clienteUsuarioId);
  const comercioBase = await asegurarCuentaComercio(clienteDb);
  const cuentaClienteBase = await clienteDb.query(
    `SELECT id FROM cuentas_billetera WHERE tipo = 'CLIENTE' AND usuario_id = $1`,
    [ctx.clienteUsuarioId]
  );
  const { cliente, comercio } = await bloquearCuentasCobro(
    clienteDb,
    cuentaClienteBase.rows[0].id,
    comercioBase.id
  );

  const saldoCliente = Number(cliente.saldo_centimos);
  if (saldoCliente < monto) {
    return {
      ok: false,
      http: 409,
      codigo: 'SALDO_INSUFICIENTE',
      error: 'Saldo insuficiente para confirmar el ajuste'
    };
  }

  const nuevoCliente = saldoCliente - monto;
  const nuevoComercio = Number(comercio.saldo_centimos) + monto;

  await clienteDb.query(
    `UPDATE cuentas_billetera SET saldo_centimos = $2, updated_at = now() WHERE id = $1`,
    [cliente.id, nuevoCliente]
  );
  await clienteDb.query(
    `UPDATE cuentas_billetera SET saldo_centimos = $2, updated_at = now() WHERE id = $1`,
    [comercio.id, nuevoComercio]
  );

  const mov = await insertarMovimiento(clienteDb, {
    uuidOperacion: ctx.uuidOperacionDebito || crypto.randomUUID(),
    cuentaId: cliente.id,
    tipo: 'AJUSTE_DESCUENTO_CLIENTE',
    sentido: 'DEBITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoCliente,
    pagoId: ctx.pagoId,
    ajustePagoId: ctx.ajustePagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Ajuste por descuento adicional'
  });

  await insertarMovimiento(clienteDb, {
    uuidOperacion: ctx.uuidOperacionCredito || crypto.randomUUID(),
    cuentaId: comercio.id,
    tipo: 'AJUSTE_ACREDITACION_COMERCIO',
    sentido: 'CREDITO',
    montoCentimos: monto,
    saldoPosteriorCentimos: nuevoComercio,
    pagoId: ctx.pagoId,
    ajustePagoId: ctx.ajustePagoId,
    pedidoId: ctx.pedidoId,
    descripcion: 'Acreditación comercial por ajuste'
  });

  return {
    ok: true,
    idempotente: false,
    saldoCliente: nuevoCliente,
    saldoComercio: nuevoComercio,
    movimientos: [mapearMovimiento(mov)]
  };
}

async function listarMovimientosCuenta(clienteDb, cuentaId, limite = 50, offset = 0) {
  const lim = Math.min(Math.max(Number(limite) || 50, 1), 200);
  const off = Math.max(0, Number(offset) || 0);
  const { rows } = await clienteDb.query(
    `SELECT * FROM movimientos_billetera
     WHERE cuenta_id = $1
     ORDER BY created_at DESC
     LIMIT $2 OFFSET $3`,
    [cuentaId, lim, off]
  );
  return rows.map(mapearMovimiento);
}

async function contarMovimientosCuenta(clienteDb, cuentaId) {
  const { rows } = await clienteDb.query(
    `SELECT COUNT(*)::int AS total FROM movimientos_billetera WHERE cuenta_id = $1`,
    [cuentaId]
  );
  return rows[0]?.total ?? 0;
}

module.exports = {
  SALDO_INICIAL_CLIENTE_CENTIMOS,
  SALDO_INICIAL_COMERCIO_CENTIMOS,
  CODIGO_COMERCIO,
  esUuid,
  mapearCuenta,
  mapearMovimiento,
  asegurarCuentaComercio,
  asegurarCuentaCliente,
  liquidarCobroPedido,
  liquidarDevolucionAjuste,
  liquidarDescuentoAjuste,
  listarMovimientosCuenta,
  contarMovimientosCuenta
};
