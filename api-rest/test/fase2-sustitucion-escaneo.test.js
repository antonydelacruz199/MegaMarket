/**
 * Fase A: aceptación ≠ preparación; escaneo del sustituto; AJUSTE único al completar.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_se_' + crypto.randomBytes(6).toString('hex');
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
      '../src/controllers/sustituciones.controller',
      '../src/controllers/picking.controller',
      '../src/sustituciones',
      '../src/picking'
    ]) {
      delete require.cache[require.resolve(mod)];
    }
    const sust = require('../src/controllers/sustituciones.controller');
    const picking = require('../src/controllers/picking.controller');
    await fn(pool, sust, picking);

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, params = {}, body = {}) {
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
  await handler({ usuario, params, body, query: {} }, res);
  return res;
}

async function semilla(pool) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES($1,$1,$1,'x','CLIENTE','RECHAZAR') RETURNING id`,
    [`c_${tag}`]
  );
  const { rows: [picker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','PICKER') RETURNING id`,
    [`p_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Cat_${tag}`]
  );
  const codOrig = `77${tag}01`;
  const codSust = `77${tag}02`;
  const { rows: [orig] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'A','d',$1,100,20,$3,'A') RETURNING id`,
    [cat.id, `Orig ${tag}`, codOrig]
  );
  const { rows: [sustProd] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'B','d',$1,90,20,$3,'B') RETURNING id`,
    [cat.id, `Sust ${tag}`, codSust]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id, preferencia_sustitucion)
     VALUES($1,$2,200,'EN_PICKING',$3,'RECHAZAR') RETURNING *`,
    [cliente.id, crypto.randomUUID(), picker.id]
  );
  const { rows: [detalle] } = await pool.query(
    `INSERT INTO pedido_detalle(
       pedido_id, producto_id, nombre_producto, cantidad,
       precio_unitario_centimos, subtotal_centimos, estado_picking, cantidad_armada
     ) VALUES($1,$2,$3,2,100,200,'FALTANTE',0) RETURNING *`,
    [pedido.id, orig.id, `Orig ${tag}`]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',2)`,
    [crypto.randomUUID(), orig.id, pedido.id, cliente.id]
  );
  await pool.query(`UPDATE productos SET stock = stock - 2 WHERE id=$1`, [orig.id]);
  await pool.query(
    `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
     VALUES($1,$2,200,'PEN','QR','CONTRAENTREGA','PENDIENTE')`,
    [crypto.randomUUID(), pedido.id]
  );
  return {
    cliente,
    picker,
    orig,
    sustProd,
    pedido,
    detalle,
    codOrig,
    codSust
  };
}

async function proponerYAceptar(pool, sustCtl, s) {
  const prop = await ejecutar(
    sustCtl.proponer,
    { id: s.picker.id, rol: 'PICKER' },
    { id: s.pedido.id, detalleId: s.detalle.id },
    { productoSustitutoId: s.sustProd.id }
  );
  assert.ok([200, 201].includes(prop.code), prop.body && prop.body.error);
  const acc = await ejecutar(
    sustCtl.aceptar,
    { id: s.cliente.id, rol: 'CLIENTE' },
    { id: prop.body.sustitucion.id }
  );
  assert.ok([200, 201].includes(acc.code), acc.body && acc.body.error);
  return prop.body.sustitucion.id;
}

test('Fase A: aceptar no marca SUSTITUIDO ni sube armada ni mueve inventario', { skip }, async () => {
  await conEsquema(async (pool, sustCtl) => {
    const s = await semilla(pool);
    const { rows: [stockSustAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sustProd.id]
    );
    await proponerYAceptar(pool, sustCtl, s);
    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, cantidad_armada, producto_id FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(det.estado_picking, 'FALTANTE');
    assert.equal(Number(det.cantidad_armada), 0);
    assert.equal(det.producto_id, s.orig.id);
    const { rows: [stockSust] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sustProd.id]
    );
    assert.equal(Number(stockSust.stock), Number(stockSustAntes.stock));
    const { rows: [aj] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA')`,
      [s.pedido.id]
    );
    assert.equal(aj.n, 0);
  });
});

test('Fase A: escaneo original incorrecto; sustituto arma; completo → SUSTITUIDO + AJUSTE único', { skip }, async () => {
  await conEsquema(async (pool, sustCtl, picking) => {
    const s = await semilla(pool);
    const sid = await proponerYAceptar(pool, sustCtl, s);
    const { rows: [stockOrigAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.orig.id]
    );
    const { rows: [stockSustAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sustProd.id]
    );

    const mal = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codOrig, cantidad: 1 }
    );
    assert.equal(mal.code, 409);
    assert.equal(mal.body.codigo, 'CODIGO_INCORRECTO');
    const { rows: [detMal] } = await pool.query(
      `SELECT cantidad_armada, estado_picking FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(Number(detMal.cantidad_armada), 0);
    assert.equal(detMal.estado_picking, 'FALTANTE');

    const uuid1 = crypto.randomUUID();
    const ok1 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: uuid1, codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(ok1.code, 201, ok1.body && ok1.body.error);
    assert.equal(ok1.body.detalle.cantidadArmada, 1);
    assert.equal(ok1.body.detalle.estadoPicking, 'PENDIENTE');
    assert.equal(ok1.body.detalle.productoId, s.orig.id);
    assert.equal(ok1.body.detalle.sustitucionAceptadaId, sid);
    assert.equal(ok1.body.detalle.codigoBarras, s.codSust);

    // Incorrecto no mueve inventario
    const { rows: [ajParcial] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA')`,
      [s.pedido.id]
    );
    assert.equal(ajParcial.n, 0);

    const reintento = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: uuid1, codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(reintento.code, 200);
    assert.equal(reintento.body.idempotente, true);
    assert.equal(reintento.body.detalle.cantidadArmada, 1);

    const ok2 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(ok2.code, 201, ok2.body && ok2.body.error);
    assert.equal(ok2.body.detalle.cantidadArmada, 2);
    assert.equal(ok2.body.detalle.estadoPicking, 'SUSTITUIDO');

    const { rows: [stockOrig] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.orig.id]
    );
    const { rows: [stockSust] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sustProd.id]
    );
    assert.equal(Number(stockOrig.stock), Number(stockOrigAntes.stock) + 2);
    assert.equal(Number(stockSust.stock), Number(stockSustAntes.stock) - 2);

    const { rows: [comp] } = await pool.query(
      `SELECT
         SUM(CASE WHEN tipo='AJUSTE_ENTRADA' THEN cantidad ELSE 0 END)::int AS entrada,
         SUM(CASE WHEN tipo='AJUSTE_SALIDA' THEN cantidad ELSE 0 END)::int AS salida,
         COUNT(*) FILTER (WHERE tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA'))::int AS n
       FROM movimientos_inventario WHERE pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(comp.entrada, 2);
    assert.equal(comp.salida, 2);
    assert.equal(comp.n, 2);

    // Históricos consultables
    const hist = await ejecutar(
      sustCtl.obtenerPorId,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: sid }
    );
    assert.equal(hist.code, 200);
    assert.equal(hist.body.sustitucion.estado, 'ACEPTADA');
    assert.equal(hist.body.sustitucion.productoOriginalId, s.orig.id);
    assert.equal(hist.body.sustitucion.productoSustitutoId, s.sustProd.id);
  });
});

test('Fase A: concurrentes no exceden cantidad; rechazo no prepara', { skip }, async () => {
  await conEsquema(async (pool, sustCtl, picking) => {
    const s = await semilla(pool);
    await proponerYAceptar(pool, sustCtl, s);

    // Dejar 1 de 2 armadas y pelear la última unidad
    await pool.query(
      `UPDATE pedido_detalle SET cantidad_armada=1, estado_picking='PENDIENTE' WHERE id=$1`,
      [s.detalle.id]
    );
    const codigos = (
      await Promise.all([
        ejecutar(
          picking.escanear,
          { id: s.picker.id, rol: 'PICKER' },
          { id: s.pedido.id, detalleId: s.detalle.id },
          { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
        ),
        ejecutar(
          picking.escanear,
          { id: s.picker.id, rol: 'PICKER' },
          { id: s.pedido.id, detalleId: s.detalle.id },
          { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
        )
      ])
    ).map((r) => r.code);
    assert.deepEqual(codigos.sort(), [201, 409]);
    const { rows: [linea] } = await pool.query(
      `SELECT cantidad_armada, estado_picking FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(Number(linea.cantidad_armada), 2);
    assert.equal(linea.estado_picking, 'SUSTITUIDO');

    const { rows: [aj] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo='AJUSTE_SALIDA'`,
      [s.pedido.id]
    );
    assert.equal(aj.n, 1);

    // Rechazo
    const s2 = await semilla(pool);
    const prop = await ejecutar(
      sustCtl.proponer,
      { id: s2.picker.id, rol: 'PICKER' },
      { id: s2.pedido.id, detalleId: s2.detalle.id },
      { productoSustitutoId: s2.sustProd.id }
    );
    const rech = await ejecutar(
      sustCtl.rechazar,
      { id: s2.cliente.id, rol: 'CLIENTE' },
      { id: prop.body.sustitucion.id }
    );
    assert.equal(rech.code, 200);
    const { rows: [detR] } = await pool.query(
      `SELECT estado_picking, cantidad_armada FROM pedido_detalle WHERE id=$1`,
      [s2.detalle.id]
    );
    assert.equal(detR.estado_picking, 'FALTANTE');
    assert.equal(Number(detR.cantidad_armada), 0);
    const escRech = await ejecutar(
      picking.escanear,
      { id: s2.picker.id, rol: 'PICKER' },
      { id: s2.pedido.id, detalleId: s2.detalle.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s2.codSust, cantidad: 1 }
    );
    assert.equal(escRech.code, 409);
    assert.equal(escRech.body.codigo, 'ESTADO_PICKING_INVALIDO');
  });
});
