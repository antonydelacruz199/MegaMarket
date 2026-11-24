const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;
const SALDO_CLIENTE = 100_000_000_000;
const SALDO_COMERCIO = 50_000_000;

async function conEsquema(fn) {
  const schema = 'megamarket_f3_' + crypto.randomBytes(6).toString('hex');
  const admin = new Pool({ connectionString: process.env.TEST_DATABASE_URL });
  let pool;
  let creado = false;
  try {
    await admin.query(`CREATE SCHEMA "${schema}"`);
    creado = true;
    pool = new Pool({
      connectionString: process.env.TEST_DATABASE_URL,
      options: `-c search_path=${schema}`
    });
    for (const file of [
      '001_esquema_inicial.sql',
      '002_reservas.sql',
      '003_operaciones_extendidas.sql',
      '004_billetera_simulada.sql',
      '005_pedido_recibido.sql'
    ]) {
      await pool.query(fs.readFileSync(path.join(__dirname, '../sql', file), 'utf8'));
    }

    const databasePath = require.resolve('../src/database');
    const original = require.cache[databasePath];
    require.cache[databasePath] = {
      id: databasePath,
      filename: databasePath,
      loaded: true,
      exports: pool
    };

    const bust = (rel) => {
      delete require.cache[require.resolve(rel)];
    };
    bust('../src/billetera');
    bust('../src/pagos');
    bust('../src/pedidosEstados');
    bust('../src/controllers/pagos.controller');
    bust('../src/controllers/billetera.controller');
    bust('../src/controllers/pedidos.controller');

    const pagos = require('../src/controllers/pagos.controller');
    const billetera = require('../src/controllers/billetera.controller');
    const pedidos = require('../src/controllers/pedidos.controller');

    await fn(pool, { pagos, billetera, pedidos });

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
    bust('../src/controllers/pagos.controller');
    bust('../src/controllers/billetera.controller');
    bust('../src/controllers/pedidos.controller');
    bust('../src/billetera');
    bust('../src/pagos');
    bust('../src/pedidosEstados');
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, body = {}, params = {}) {
  const res = {
    code: 200,
    body: null,
    status(code) {
      this.code = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    }
  };
  await handler({ usuario, body, params, query: body.query || {} }, res);
  return res;
}

async function semilla(pool) {
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES('Ana','ana','ana','x','CLIENTE','ACEPTAR') RETURNING id`
  );
  const { rows: [cliente2] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES('Bob','bob','bob','x','CLIENTE','RECHAZAR') RETURNING id`
  );
  const { rows: [admin] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Admin','admin','admin','x','ADMINISTRADOR') RETURNING id`
  );
  const { rows: [repartidor] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Rep','rep','rep','x','REPARTIDOR') RETURNING id`
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES('Abarrotes') RETURNING id`
  );
  const { rows: [producto] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES('Arroz','Marca','desc',$1,100,100) RETURNING id`,
    [cat.id]
  );
  const { rows: [franja] } = await pool.query(
    `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
     VALUES (CURRENT_DATE + 1, '10:00', '12:00', 20, true) RETURNING id`
  );
  return { cliente, cliente2, admin, repartidor, producto, franja };
}

function direccion() {
  return {
    departamento: 'Junín',
    provincia: 'Huancayo',
    distrito: 'El Tambo',
    direccion: 'Calle 1',
    telefono: '999999999'
  };
}

async function crearPedidoV3(pool, pedidos, cliente, producto, franja, modalidad) {
  const cart = crypto.randomUUID();
  await pool.query(`INSERT INTO carritos(id,usuario_id,revision) VALUES($1,$2,1)`, [
    cart,
    cliente.id
  ]);
  await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,2)`, [cart, producto.id]);
  const res = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
    contratoVersion: 3,
    clientUuid: cart,
    franjaEntregaId: franja.id,
    modalidadPago: modalidad,
    direccion: direccion(),
    items: [{ productoId: producto.id, cantidad: 2, precioEsperadoCentimos: 100 }]
  });
  assert.equal(res.code, 201, JSON.stringify(res.body));
  return res.body;
}

/** Simula cierre de preparación: RECIBIDO/EN_PICKING → CONFIRMADO (habilita pago ONLINE). */
async function marcarPreparacionConfirmada(pool, pedidoId) {
  await pool.query(
    `UPDATE pedidos SET estado = 'CONFIRMADO', updated_at = now() WHERE id = $1`,
    [pedidoId]
  );
}

test('004 declara billetera sin DROP y saldos iniciales', () => {
  const sql = fs.readFileSync(
    path.join(__dirname, '../sql/004_billetera_simulada.sql'),
    'utf8'
  );
  assert.match(sql, /CREATE TABLE IF NOT EXISTS cuentas_billetera/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS movimientos_billetera/);
  assert.match(sql, /100000000000/);
  assert.match(sql, /50000000/);
  assert.doesNotMatch(sql, /\bDROP TABLE\b/i);
});

test('PostgreSQL: inicialización de saldos e aislamiento entre clientes', { skip }, async () => {
  await conEsquema(async (pool, { billetera }) => {
    const { cliente, cliente2, admin } = await semilla(pool);

    const s1 = await ejecutar(billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    assert.equal(s1.code, 200);
    assert.equal(s1.body.cuenta.saldoCentimos, SALDO_CLIENTE);
    assert.equal(s1.body.simulacion, true);

    const s2 = await ejecutar(billetera.saldoPropio, { id: cliente2.id, rol: 'CLIENTE' });
    assert.equal(s2.body.cuenta.saldoCentimos, SALDO_CLIENTE);
    assert.notEqual(s1.body.cuenta.id, s2.body.cuenta.id);

    const sc = await ejecutar(billetera.saldoPropio, { id: admin.id, rol: 'ADMINISTRADOR' });
    assert.equal(sc.code, 200);
    assert.equal(sc.body.cuenta.tipo, 'COMERCIO');
    assert.equal(sc.body.cuenta.saldoCentimos, SALDO_COMERCIO);
  });
});

test('PostgreSQL: confirmación ONLINE directa sin QR y QR opcional', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });

    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    assert.equal(creado.pago.estado, 'PENDIENTE');
    assert.equal(creado.pedido.estado, 'RECIBIDO');

    const prematuro = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(prematuro.code, 409);
    assert.equal(prematuro.body.codigo, 'PAGO_REQUIERE_CONFIRMACION');

    await marcarPreparacionConfirmada(pool, creado.pedido.id);

    // Pagar ahora sin generar QR (solo tras CONFIRMADO)
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200, JSON.stringify(conf.body));
    assert.equal(conf.body.pago.estado, 'CONFIRMADO');
    assert.match(conf.body.pago.numeroOperacionBanco, /^MM-OP-/);
    assert.equal(conf.body.pedido.estado, 'LISTO');
    assert.equal(conf.body.billetera.saldoClienteCentimos, SALDO_CLIENTE - 200);
    assert.equal(conf.body.billetera.saldoComercioCentimos, SALDO_COMERCIO + 200);

    const saldo = await ejecutar(api.billetera.saldoPropio, {
      id: cliente.id,
      rol: 'CLIENTE'
    });
    assert.equal(saldo.body.cuenta.saldoCentimos, SALDO_CLIENTE - 200);

    const movs = await ejecutar(
      api.billetera.movimientosPropios,
      { id: cliente.id, rol: 'CLIENTE' },
      { query: { limite: 10 } }
    );
    assert.ok(movs.body.movimientos.some((m) => m.tipo === 'COBRO_PEDIDO'));
  });
});

test('PostgreSQL: QR opcional no confirma; generar QR deja estado QR_GENERADO', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);

    const qr = await ejecutar(
      api.pagos.generarQr,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: creado.pedido.id }
    );
    assert.equal(qr.code, 200, JSON.stringify(qr.body));
    assert.equal(qr.body.pago.estado, 'QR_GENERADO');
    assert.match(qr.body.qr.contenido, /MEGAMARKET-PAGO/);
    assert.equal(qr.body.qr.importeCentimos, 200);

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE);
  });
});

test('PostgreSQL: saldo insuficiente bloquea confirmación', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    await pool.query(
      `UPDATE cuentas_billetera SET saldo_centimos = 50 WHERE usuario_id = $1`,
      [cliente.id]
    );

    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 409);
    assert.equal(conf.body.codigo, 'SALDO_INSUFICIENTE');

    const { rows: [pago] } = await pool.query(`SELECT estado FROM pagos WHERE id=$1`, [
      creado.pago.id
    ]);
    assert.equal(pago.estado, 'PENDIENTE');
  });
});

test('PostgreSQL: confirmación duplicada e idempotencia', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);
    const uuidOp = crypto.randomUUID();
    const a = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: uuidOp },
      { id: creado.pago.id }
    );
    assert.equal(a.code, 200);
    const b = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: uuidOp },
      { id: creado.pago.id }
    );
    assert.equal(b.code, 200);
    assert.equal(b.body.idempotente, true);

    const otra = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(otra.code, 200);
    assert.equal(otra.body.yaConfirmado, true);

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE - 200);

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE pago_id=$1 AND tipo='COBRO_PEDIDO'`,
      [creado.pago.id]
    );
    assert.equal(n.c, 1);
  });
});

test('PostgreSQL: dos confirmaciones concurrentes no duplican cobro', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);

    const [r1, r2] = await Promise.all([
      ejecutar(
        api.pagos.confirmar,
        { id: cliente.id, rol: 'CLIENTE' },
        { uuidOperacion: crypto.randomUUID() },
        { id: creado.pago.id }
      ),
      ejecutar(
        api.pagos.confirmar,
        { id: cliente.id, rol: 'CLIENTE' },
        { uuidOperacion: crypto.randomUUID() },
        { id: creado.pago.id }
      )
    ]);

    assert.ok([r1.code, r2.code].every((c) => c === 200));
    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE - 200);
    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE pago_id=$1 AND tipo='COBRO_PEDIDO'`,
      [creado.pago.id]
    );
    assert.equal(n.c, 1);
  });
});

test('PostgreSQL: CONTRAENTREGA confirmada por repartidor asignado', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, repartidor, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'CONTRAENTREGA'
    );

    // Cliente no puede confirmar contraentrega antes de entrega
    const negado = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(negado.code, 409);
    assert.equal(negado.body.codigo, 'PEDIDO_NO_EN_ENTREGA');

    await pool.query(
      `UPDATE pedidos SET estado='EN_CAMINO', repartidor_id=$2 WHERE id=$1`,
      [creado.pedido.id, repartidor.id]
    );

    // Efectivo: confirma sin debitar billetera ni retroceder estado logístico
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      { uuidOperacion: crypto.randomUUID(), formaCobro: 'EFECTIVO' },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200, JSON.stringify(conf.body));
    assert.equal(conf.body.pago.estado, 'CONFIRMADO');
    assert.equal(conf.body.pago.modalidad, 'CONTRAENTREGA');
    assert.equal(conf.body.pedido.estado, 'EN_CAMINO');
    assert.equal(conf.body.formaCobro, 'EFECTIVO');

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE);

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE pago_id=$1 AND tipo='COBRO_PEDIDO'`,
      [creado.pago.id]
    );
    assert.equal(n.c, 0);
  });
});

test('PostgreSQL: cliente puede pagar CONTRAENTREGA con billetera en entrega', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, repartidor, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'CONTRAENTREGA'
    );
    await pool.query(
      `UPDATE pedidos SET estado='EN_CAMINO', repartidor_id=$2 WHERE id=$1`,
      [creado.pedido.id, repartidor.id]
    );
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID(), formaCobro: 'BILLETERA' },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200, JSON.stringify(conf.body));
    assert.equal(conf.body.pago.estado, 'CONFIRMADO');
    assert.equal(conf.body.pedido.estado, 'EN_CAMINO');
    assert.equal(conf.body.billetera.saldoClienteCentimos, SALDO_CLIENTE - 200);
  });
});

test('PostgreSQL: CONTRAENTREGA billetera exige autorización del cliente', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, repartidor, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'CONTRAENTREGA'
    );
    await pool.query(
      `UPDATE pedidos SET estado='EN_CAMINO', repartidor_id=$2 WHERE id=$1`,
      [creado.pedido.id, repartidor.id]
    );

    const sinAuth = await ejecutar(
      api.pagos.confirmar,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      { uuidOperacion: crypto.randomUUID(), formaCobro: 'BILLETERA' },
      { id: creado.pago.id }
    );
    assert.equal(sinAuth.code, 409);
    assert.equal(sinAuth.body.codigo, 'AUTORIZACION_CLIENTE_REQUERIDA');

    const authUuid = crypto.randomUUID();
    const auth = await ejecutar(
      api.pagos.autorizarBilletera,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: authUuid },
      { id: creado.pago.id }
    );
    assert.equal(auth.code, 200, JSON.stringify(auth.body));

    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      {
        uuidOperacion: crypto.randomUUID(),
        formaCobro: 'BILLETERA',
        autorizacionClienteUuid: authUuid
      },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200, JSON.stringify(conf.body));
    assert.equal(conf.body.pedido.estado, 'EN_CAMINO');
    assert.equal(conf.body.billetera.saldoClienteCentimos, SALDO_CLIENTE - 200);
  });
});

test('PostgreSQL: QR EXPIRADO se recupera sin duplicar cobro', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);

    const qr = await ejecutar(
      api.pagos.generarQr,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: creado.pedido.id }
    );
    assert.equal(qr.code, 200);

    await pool.query(
      `UPDATE pagos
       SET estado='EXPIRADO', fecha_expiracion_qr = now() - interval '1 minute'
       WHERE id=$1`,
      [creado.pago.id]
    );

    const qrNuevo = await ejecutar(
      api.pagos.generarQr,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: creado.pedido.id }
    );
    assert.equal(qrNuevo.code, 200, JSON.stringify(qrNuevo.body));
    assert.equal(qrNuevo.body.pago.estado, 'QR_GENERADO');

    // También se puede pagar directo desde EXPIRADO (otro pedido)
    const creado2 = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado2.pedido.id);
    await pool.query(`UPDATE pagos SET estado='EXPIRADO' WHERE id=$1`, [creado2.pago.id]);
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado2.pago.id }
    );
    assert.equal(conf.code, 200, JSON.stringify(conf.body));
    assert.equal(conf.body.pedido.estado, 'LISTO');
    assert.equal(conf.body.pago.estado, 'CONFIRMADO');

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE pago_id=$1 AND tipo='COBRO_PEDIDO'`,
      [creado2.pago.id]
    );
    assert.equal(n.c, 1);
  });
});

test('PostgreSQL: cancelación ONLINE pagado con devolución idempotente', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, admin, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200);
    assert.equal(conf.body.pedido.estado, 'LISTO');

    // Tras despacho (LISTO) la cancelación con devolución la hace el administrador.
    const cancel = await ejecutar(
      api.pedidos.transicionar,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { estado: 'CANCELADO' },
      { id: creado.pedido.id }
    );
    assert.equal(cancel.code, 200, JSON.stringify(cancel.body));
    assert.equal(cancel.body.pedido.estado, 'CANCELADO');
    assert.equal(cancel.body.devolucion.aplicada, true);

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE);

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE pedido_id=$1 AND tipo='DEVOLUCION_CLIENTE'`,
      [creado.pedido.id]
    );
    assert.equal(n.c, 1);

    // Stock restituido exactamente una vez
    const { rows: [prod] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      producto.id
    ]);
    assert.equal(Number(prod.stock), 100);
  });
});

test('PostgreSQL: cancelación de pago pendiente sin tocar billetera', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );

    const cancel = await ejecutar(
      api.pedidos.transicionar,
      { id: cliente.id, rol: 'CLIENTE' },
      { estado: 'CANCELADO' },
      { id: creado.pedido.id }
    );
    assert.equal(cancel.code, 200, JSON.stringify(cancel.body));

    const { rows: [pago] } = await pool.query(`SELECT estado FROM pagos WHERE pedido_id=$1`, [
      creado.pedido.id
    ]);
    assert.equal(pago.estado, 'CANCELADO');

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE);
  });
});

test('PostgreSQL: cancelación CONTRAENTREGA sin cobro restituye stock y cupo', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'CONTRAENTREGA'
    );
    const ocupadosAntes = await pool.query(
      `SELECT COUNT(*)::int AS c FROM pedidos
       WHERE franja_entrega_id=$1 AND estado <> 'CANCELADO'`,
      [franja.id]
    );
    assert.equal(ocupadosAntes.rows[0].c, 1);

    const cancel = await ejecutar(
      api.pedidos.transicionar,
      { id: cliente.id, rol: 'CLIENTE' },
      { estado: 'CANCELADO' },
      { id: creado.pedido.id }
    );
    assert.equal(cancel.code, 200, JSON.stringify(cancel.body));

    const { rows: [pago] } = await pool.query(`SELECT estado FROM pagos WHERE pedido_id=$1`, [
      creado.pedido.id
    ]);
    assert.equal(pago.estado, 'CANCELADO');

    const { rows: [prod] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      producto.id
    ]);
    assert.equal(Number(prod.stock), 100);

    const ocupados = await pool.query(
      `SELECT COUNT(*)::int AS c FROM pedidos
       WHERE franja_entrega_id=$1 AND estado <> 'CANCELADO'`,
      [franja.id]
    );
    assert.equal(ocupados.rows[0].c, 0);

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera WHERE pedido_id=$1`,
      [creado.pedido.id]
    );
    assert.equal(n.c, 0);
  });
});

test('PostgreSQL: dos pickers no toman el mismo pedido', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, producto, franja } = await semilla(pool);
    const { rows: [picker1] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES('P1','p1','p1','x','PICKER') RETURNING id`
    );
    const { rows: [picker2] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES('P2','p2','p2','x','PICKER') RETURNING id`
    );
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    assert.equal(creado.pedido.estado, 'RECIBIDO');

    const [a, b] = await Promise.all([
      ejecutar(
        api.pedidos.transicionar,
        { id: picker1.id, rol: 'PICKER' },
        { estado: 'EN_PICKING' },
        { id: creado.pedido.id }
      ),
      ejecutar(
        api.pedidos.transicionar,
        { id: picker2.id, rol: 'PICKER' },
        { estado: 'EN_PICKING' },
        { id: creado.pedido.id }
      )
    ]);

    const codigos = [a.code, b.code].sort();
    assert.deepEqual(codigos, [200, 403]);
    const ok = a.code === 200 ? a : b;
    const fail = a.code === 200 ? b : a;
    assert.equal(ok.body.pedido.estado, 'EN_PICKING');
    assert.equal(fail.body.codigo, 'PEDIDO_NO_ASIGNADO');

    const { rows: [ped] } = await pool.query(
      `SELECT picker_id, estado FROM pedidos WHERE id=$1`,
      [creado.pedido.id]
    );
    assert.equal(ped.estado, 'EN_PICKING');
    assert.ok([picker1.id, picker2.id].includes(ped.picker_id));
  });
});

test('PostgreSQL: devolución controlada restituye saldo una sola vez', { skip }, async () => {
  await conEsquema(async (pool, api) => {
    const { cliente, admin, producto, franja } = await semilla(pool);
    await ejecutar(api.billetera.saldoPropio, { id: cliente.id, rol: 'CLIENTE' });
    const creado = await crearPedidoV3(
      pool,
      api.pedidos,
      cliente,
      producto,
      franja,
      'ONLINE'
    );
    await marcarPreparacionConfirmada(pool, creado.pedido.id);
    const conf = await ejecutar(
      api.pagos.confirmar,
      { id: cliente.id, rol: 'CLIENTE' },
      { uuidOperacion: crypto.randomUUID() },
      { id: creado.pago.id }
    );
    assert.equal(conf.code, 200);

    const { rows: [ajuste] } = await pool.query(
      `INSERT INTO ajustes_pago (
         uuid_operacion, pago_id, tipo, motivo, monto_centimos, estado
       ) VALUES ($1,$2,'DEVOLUCION','PRODUCTO_FALTANTE',80,'PENDIENTE')
       RETURNING id`,
      [crypto.randomUUID(), creado.pago.id]
    );

    const uuidAj = crypto.randomUUID();
    const dev = await ejecutar(
      api.pagos.confirmarAjuste,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { uuidOperacion: uuidAj },
      { id: ajuste.id }
    );
    assert.equal(dev.code, 200, JSON.stringify(dev.body));
    assert.equal(dev.body.ajuste.estado, 'CONFIRMADO');

    const { rows: [cuenta] } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE usuario_id=$1`,
      [cliente.id]
    );
    assert.equal(Number(cuenta.saldo_centimos), SALDO_CLIENTE - 200 + 80);

    const dup = await ejecutar(
      api.pagos.confirmarAjuste,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { uuidOperacion: uuidAj },
      { id: ajuste.id }
    );
    assert.equal(dup.body.idempotente, true);

    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM movimientos_billetera
       WHERE ajuste_pago_id=$1 AND tipo='DEVOLUCION_CLIENTE'`,
      [ajuste.id]
    );
    assert.equal(n.c, 1);
  });
});

test('PostgreSQL: pedidos previos CONFIRMADO siguen intactos tras 004', { skip }, async () => {
  await conEsquema(async (pool) => {
    const { cliente, producto, franja } = await semilla(pool);
    const pedidoId = crypto.randomUUID();
    await pool.query(
      `INSERT INTO pedidos(id, cliente_id, client_uuid, total_centimos, estado, franja_entrega_id)
       VALUES ($1,$2,$3,500,'CONFIRMADO',$4)`,
      [pedidoId, cliente.id, crypto.randomUUID(), franja.id]
    );
    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
       VALUES ($1,$2,500,'PEN','QR','ONLINE','PENDIENTE')`,
      [crypto.randomUUID(), pedidoId]
    );
    const { rows: [p] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [pedidoId]);
    assert.equal(p.estado, 'CONFIRMADO');
    const { rows: comercios } = await pool.query(
      `SELECT saldo_centimos FROM cuentas_billetera WHERE tipo='COMERCIO'`
    );
    assert.equal(comercios.length, 1);
    assert.equal(Number(comercios[0].saldo_centimos), SALDO_COMERCIO);
  });
});
