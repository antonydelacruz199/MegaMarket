const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_sj_' + crypto.randomBytes(6).toString('hex');
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
    for (const mod of [
      '../src/jobs/sustitucionesExpiradas',
      '../src/controllers/sustituciones.controller',
      '../src/sustituciones',
      '../src/picking'
    ]) {
      delete require.cache[require.resolve(mod)];
    }
    const job = require('../src/jobs/sustitucionesExpiradas');
    const ctl = require('../src/controllers/sustituciones.controller');
    await fn(pool, job, ctl);

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function semilla(pool, { preferencia = 'ACEPTAR', estadoPedido = 'EN_PICKING', precioSust = 100 } = {}) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES($1,$1,$1,'x','CLIENTE',$2) RETURNING id`,
    [`cj_${tag}`, preferencia]
  );
  const { rows: [picker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','PICKER') RETURNING id`,
    [`pj_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Cat_${tag}`]
  );
  const { rows: [orig] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES($1,'A','d',$2,100,20) RETURNING id`,
    [`Orig ${tag}`, cat.id]
  );
  const { rows: [sust] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES($1,'B','d',$2,$3,20) RETURNING id`,
    [`Sust ${tag}`, cat.id, precioSust]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id, preferencia_sustitucion)
     VALUES($1,$2,100,$3,$4,$5) RETURNING *`,
    [cliente.id, crypto.randomUUID(), estadoPedido, picker.id, preferencia]
  );
  const { rows: [detalle] } = await pool.query(
    `INSERT INTO pedido_detalle(
       pedido_id, producto_id, nombre_producto, cantidad,
       precio_unitario_centimos, subtotal_centimos, estado_picking
     ) VALUES($1,$2,$3,1,100,100,'FALTANTE') RETURNING *`,
    [pedido.id, orig.id, `Orig ${tag}`]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',1)`,
    [crypto.randomUUID(), orig.id, pedido.id, cliente.id]
  );
  await pool.query(`UPDATE productos SET stock = stock - 1 WHERE id=$1`, [orig.id]);
  await pool.query(
    `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
     VALUES($1,$2,100,'PEN','QR','CONTRAENTREGA','PENDIENTE')`,
    [crypto.randomUUID(), pedido.id]
  );
  const { rows: [sustitucion] } = await pool.query(
    `INSERT INTO sustituciones_pedido(
       pedido_detalle_id, producto_original_id, producto_sustituto_id, picker_id,
       cantidad, precio_original_centimos, precio_sustituto_centimos, estado,
       fecha_propuesta, fecha_limite
     ) VALUES ($1,$2,$3,$4,1,100,$5,'PENDIENTE', now() - interval '6 minutes', now() - interval '1 minute')
     RETURNING *`,
    [detalle.id, orig.id, sust.id, picker.id, precioSust]
  );
  return { cliente, picker, orig, sust, pedido, detalle, sustitucion };
}

test('job: resuelve vencidas por preferencia y es idempotente al repetir', { skip }, async () => {
  await conEsquema(async (pool, job) => {
    const s = await semilla(pool, { preferencia: 'ACEPTAR', precioSust: 90 });
    const { rows: [stockSustAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sust.id]
    );

    const r1 = await job.ejecutarCicloExpiradas();
    assert.equal(r1.skipped, false);
    assert.equal(r1.resueltas, 1);
    assert.equal(r1.sustituciones[0].estado, 'ACEPTADA');
    assert.equal(r1.sustituciones[0].respuestaAutomatica, true);

    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, precio_unitario_centimos, cantidad_armada FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    // Auto-aceptar no prepara ni mueve stock; queda pendiente de escaneo del sustituto.
    assert.equal(det.estado_picking, 'FALTANTE');
    assert.equal(Number(det.cantidad_armada), 0);
    assert.equal(Number(det.precio_unitario_centimos), 100);

    const { rows: [ajuste] } = await pool.query(
      `SELECT tipo, monto_centimos, estado FROM ajustes_pago WHERE sustitucion_id=$1`,
      [s.sustitucion.id]
    );
    assert.equal(ajuste.tipo, 'DEVOLUCION');
    assert.equal(Number(ajuste.monto_centimos), 10);
    assert.equal(ajuste.estado, 'PENDIENTE');

    const { rows: [stockSust] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sust.id]
    );
    assert.equal(Number(stockSust.stock), Number(stockSustAntes.stock));

    const r2 = await job.ejecutarCicloExpiradas();
    assert.equal(r2.resueltas, 0);

    const { rows: [salidas] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [s.pedido.id]
    );
    assert.equal(salidas.n, 1);
    const { rows: [ajustesN] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM ajustes_pago WHERE sustitucion_id=$1`,
      [s.sustitucion.id]
    );
    assert.equal(ajustesN.n, 1);
  });
});

test('job: decisión manual previa tiene prioridad; cancelado no aplica preferencia', { skip }, async () => {
  await conEsquema(async (pool, job, ctl) => {
    const manual = await semilla(pool, { preferencia: 'ACEPTAR', precioSust: 100 });
    // Aún no vencida: simular decisión manual antes del límite
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() + interval '4 minutes' WHERE id=$1`,
      [manual.sustitucion.id]
    );
    const res = {
      code: 200,
      body: null,
      status(c) {
        this.code = c;
        return this;
      },
      json(b) {
        this.body = b;
        return this;
      }
    };
    await ctl.rechazar(
      { usuario: { id: manual.cliente.id, rol: 'CLIENTE' }, params: { id: manual.sustitucion.id }, body: {} },
      res
    );
    assert.equal(res.code, 200);
    assert.equal(res.body.sustitucion.estado, 'RECHAZADA');
    assert.equal(res.body.sustitucion.respuestaAutomatica, false);

    // Vencer después: el job no debe cambiar la decisión manual
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() - interval '1 minute' WHERE id=$1`,
      [manual.sustitucion.id]
    );
    const ciclo = await job.ejecutarCicloExpiradas();
    assert.equal(ciclo.resueltas, 0);
    const { rows: [fila] } = await pool.query(
      `SELECT estado, respuesta_automatica FROM sustituciones_pedido WHERE id=$1`,
      [manual.sustitucion.id]
    );
    assert.equal(fila.estado, 'RECHAZADA');
    assert.equal(fila.respuesta_automatica, false);

    const cancelado = await semilla(pool, {
      preferencia: 'ACEPTAR',
      estadoPedido: 'CANCELADO',
      precioSust: 100
    });
    const { rows: [stockAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [cancelado.sust.id]
    );
    const rCancel = await job.ejecutarCicloExpiradas();
    assert.ok(rCancel.sustituciones.some((s) => s.id === cancelado.sustitucion.id));
    const { rows: [sc] } = await pool.query(
      `SELECT estado FROM sustituciones_pedido WHERE id=$1`,
      [cancelado.sustitucion.id]
    );
    assert.equal(sc.estado, 'CANCELADA');
    const { rows: [stockDespues] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [cancelado.sust.id]
    );
    assert.equal(Number(stockDespues.stock), Number(stockAntes.stock));
    const { rows: [det] } = await pool.query(
      `SELECT estado_picking FROM pedido_detalle WHERE id=$1`,
      [cancelado.detalle.id]
    );
    assert.equal(det.estado_picking, 'FALTANTE');
  });
});

test('job: concurrencia con advisory lock — un ciclo gana', { skip }, async () => {
  await conEsquema(async (pool, job) => {
    await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 100 });
    const holder = await pool.connect();
    try {
      const { rows } = await holder.query(
        `SELECT pg_try_advisory_lock($1::bigint) AS ok`,
        [job.ADVISORY_LOCK_KEY]
      );
      assert.equal(rows[0].ok, true);
      const omitido = await job.ejecutarCicloExpiradas();
      assert.equal(omitido.skipped, true);
      assert.equal(omitido.resueltas, 0);
    } finally {
      await holder.query(`SELECT pg_advisory_unlock($1::bigint)`, [job.ADVISORY_LOCK_KEY]);
      holder.release();
    }
    const activo = await job.ejecutarCicloExpiradas();
    assert.equal(activo.skipped, false);
    assert.equal(activo.resueltas, 1);
    assert.equal(activo.sustituciones[0].estado, 'RECHAZADA');
  });
});

test('Fase D: ACEPTAR auto no pasa a LISTO ni mueve inventario; RECHAZAR deja sin preparar', { skip }, async () => {
  await conEsquema(async (pool, job) => {
    const aceptar = await semilla(pool, { preferencia: 'ACEPTAR', precioSust: 90 });
    const { rows: [movAntes] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1`,
      [aceptar.pedido.id]
    );

    const r = await job.ejecutarCicloExpiradas();
    assert.equal(r.resueltas, 1);
    assert.equal(r.sustituciones[0].estado, 'ACEPTADA');
    assert.equal(r.sustituciones[0].respuestaAutomatica, true);

    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [
      aceptar.pedido.id
    ]);
    assert.equal(ped.estado, 'EN_PICKING');

    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, cantidad_armada FROM pedido_detalle WHERE id=$1`,
      [aceptar.detalle.id]
    );
    assert.equal(det.estado_picking, 'FALTANTE');
    assert.equal(Number(det.cantidad_armada), 0);

    const { rows: [movDespues] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1`,
      [aceptar.pedido.id]
    );
    assert.equal(movDespues.n, movAntes.n);

    const rechazar = await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 100 });
    const r2 = await job.ejecutarCicloExpiradas();
    assert.ok(r2.sustituciones.some((s) => s.id === rechazar.sustitucion.id && s.estado === 'RECHAZADA'));
    const { rows: [detR] } = await pool.query(
      `SELECT estado_picking, cantidad_armada FROM pedido_detalle WHERE id=$1`,
      [rechazar.detalle.id]
    );
    assert.equal(detR.estado_picking, 'FALTANTE');
    assert.equal(Number(detR.cantidad_armada), 0);
    const { rows: [pedR] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [
      rechazar.pedido.id
    ]);
    assert.equal(pedR.estado, 'EN_PICKING');
  });
});

test('Fase D: ENTREGADO cancela propuesta vencida sin LISTO ni stock', { skip }, async () => {
  await conEsquema(async (pool, job) => {
    const s = await semilla(pool, {
      preferencia: 'ACEPTAR',
      estadoPedido: 'ENTREGADO',
      precioSust: 100
    });
    const { rows: [stockAntes] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.sust.id
    ]);
    const r = await job.ejecutarCicloExpiradas();
    assert.ok(r.sustituciones.some((x) => x.id === s.sustitucion.id));
    const { rows: [sc] } = await pool.query(
      `SELECT estado FROM sustituciones_pedido WHERE id=$1`,
      [s.sustitucion.id]
    );
    assert.equal(sc.estado, 'CANCELADA');
    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.sust.id
    ]);
    assert.equal(Number(stock.stock), Number(stockAntes.stock));
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [
      s.pedido.id
    ]);
    assert.equal(ped.estado, 'ENTREGADO');
  });
});
