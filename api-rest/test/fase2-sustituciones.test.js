const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');
const {
  diferenciaPrecioDentroDeTolerancia,
  montoAjusteCentimos
} = require('../src/sustituciones');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_su_' + crypto.randomBytes(6).toString('hex');
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
      '003_operaciones_extendidas.sql'
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
      '../src/sustituciones',
      '../src/picking'
    ]) {
      delete require.cache[require.resolve(mod)];
    }
    const controller = require('../src/controllers/sustituciones.controller');
    await fn(pool, controller);

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, params = {}, body = {}, query = {}) {
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
  await handler({ usuario, params, body, query }, res);
  return res;
}

async function semilla(pool, { preferencia = 'RECHAZAR', precioSust = 100, catDistinta = false } = {}) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES($2,$2,$2,'x','CLIENTE',$1) RETURNING id`,
    [preferencia, `c_${tag}`]
  );
  const { rows: [picker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','PICKER') RETURNING id`,
    [`p_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Lacteos_${tag}`]
  );
  const { rows: [cat2] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Limpieza_${tag}`]
  );
  const { rows: [orig] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras)
     VALUES($2,'A','d',$1,100,10,$3) RETURNING id`,
    [cat.id, `Leche ${tag}`, `88${tag}01`]
  );
  const { rows: [sust] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras)
     VALUES($3,'B','d',$1,$2,10,$4) RETURNING id`,
    [catDistinta ? cat2.id : cat.id, precioSust, `Leche B ${tag}`, `88${tag}02`]
  );
  const { rows: [caro] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES($2,'C','d',$1,150,10) RETURNING id`,
    [cat.id, `Leche Premium ${tag}`]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id, preferencia_sustitucion)
     VALUES($1,$2,200,'EN_PICKING',$3,$4) RETURNING *`,
    [cliente.id, crypto.randomUUID(), picker.id, preferencia]
  );
  const { rows: [detalle] } = await pool.query(
    `INSERT INTO pedido_detalle(
       pedido_id, producto_id, nombre_producto, cantidad,
       precio_unitario_centimos, subtotal_centimos, estado_picking, cantidad_armada
     ) VALUES($1,$2,$3,2,100,200,'FALTANTE',0) RETURNING *`,
    [pedido.id, orig.id, `Leche ${tag}`]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',2)`,
    [crypto.randomUUID(), orig.id, pedido.id, cliente.id]
  );
  await pool.query(`UPDATE productos SET stock = stock - 2 WHERE id=$1`, [orig.id]);
  const { rows: [pago] } = await pool.query(
    `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
     VALUES($1,$2,200,'PEN','QR','CONTRAENTREGA','PENDIENTE') RETURNING id`,
    [crypto.randomUUID(), pedido.id]
  );
  return { cliente, picker, orig, sust, caro, pedido, detalle, pago, cat };
}

test('reglas: tolerancia ±20 % y monto de ajuste', () => {
  assert.equal(diferenciaPrecioDentroDeTolerancia(100, 120), true);
  assert.equal(diferenciaPrecioDentroDeTolerancia(100, 80), true);
  assert.equal(diferenciaPrecioDentroDeTolerancia(100, 121), false);
  assert.deepEqual(montoAjusteCentimos(100, 120, 2), {
    tipo: 'DESCUENTO',
    montoCentimos: 40
  });
  assert.deepEqual(montoAjusteCentimos(100, 80, 2), {
    tipo: 'DEVOLUCION',
    montoCentimos: 40
  });
  assert.equal(montoAjusteCentimos(100, 100, 2), null);
});

test('PostgreSQL sustituciones: categoría, rango y propuesta', { skip }, async () => {
  await conEsquema(async (pool, ctl) => {
    const s = await semilla(pool, { precioSust: 100 });
    const ok = await ejecutar(
      ctl.listarCandidatos,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(ok.code, 200);
    assert.ok(ok.body.candidatos.some((c) => c.id === s.sust.id));
    assert.ok(!ok.body.candidatos.some((c) => c.id === s.caro.id)); // 50% fuera de rango

    const fuera = await ejecutar(
      ctl.proponer,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { productoSustitutoId: s.caro.id }
    );
    assert.equal(fuera.code, 409);
    assert.equal(fuera.body.codigo, 'PRECIO_FUERA_RANGO');

    const { rows: [otraCat] } = await pool.query(
      `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
       VALUES('Detergente','D','d',$1,100,5) RETURNING id`,
      [(await pool.query(`SELECT id FROM categorias WHERE nombre LIKE 'Limpieza_%' LIMIT 1`)).rows[0].id]
    );
    const cat = await ejecutar(
      ctl.proponer,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { productoSustitutoId: otraCat.id }
    );
    assert.equal(cat.code, 409);
    assert.equal(cat.body.codigo, 'CATEGORIA_DISTINTA');

    const prop = await ejecutar(
      ctl.proponer,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { productoSustitutoId: s.sust.id }
    );
    assert.equal(prop.code, 201);
    assert.equal(prop.body.sustitucion.estado, 'PENDIENTE');

    const dup = await ejecutar(
      ctl.proponer,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { productoSustitutoId: s.sust.id }
    );
    assert.equal(dup.code, 409);
    assert.equal(dup.body.codigo, 'PROPUESTA_PENDIENTE');
  });
});

test('PostgreSQL sustituciones: aceptación, ajustes, inventario e idempotencia', { skip }, async () => {
  await conEsquema(async (pool, ctl) => {
    const s = await semilla(pool, { precioSust: 80 });
    const prop = await ejecutar(
      ctl.proponer,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { productoSustitutoId: s.sust.id }
    );
    const sid = prop.body.sustitucion.id;

    const ajeno = await ejecutar(
      ctl.aceptar,
      { id: crypto.randomUUID(), rol: 'CLIENTE' },
      { id: sid }
    );
    assert.equal(ajeno.code, 403);

    const { rows: [stockOrigAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.orig.id]
    );
    const { rows: [stockSustAntes] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.sust.id]
    );
    const { rows: [salidasAntes] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [s.pedido.id]
    );

    const acc = await ejecutar(
      ctl.aceptar,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: sid }
    );
    assert.equal(acc.code, 200);
    assert.equal(acc.body.sustitucion.estado, 'ACEPTADA');
    assert.equal(acc.body.ajuste.tipo, 'DEVOLUCION');
    assert.equal(acc.body.ajuste.montoCentimos, 40);
    assert.equal(acc.body.ajuste.estado, 'PENDIENTE');

    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, precio_unitario_centimos, subtotal_centimos, cantidad_armada, producto_id
       FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    // Aceptar no prepara: sigue FALTANTE, armada 0, producto original intacto.
    assert.equal(det.estado_picking, 'FALTANTE');
    assert.equal(Number(det.precio_unitario_centimos), 100);
    assert.equal(Number(det.subtotal_centimos), 200);
    assert.equal(Number(det.cantidad_armada), 0);
    assert.equal(det.producto_id, s.orig.id);

    const { rows: [stockOrig] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.orig.id
    ]);
    const { rows: [stockSust] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.sust.id
    ]);
    // Inventario se mueve al completar escaneos del sustituto, no al aceptar.
    assert.equal(Number(stockOrig.stock), Number(stockOrigAntes.stock));
    assert.equal(Number(stockSust.stock), Number(stockSustAntes.stock));

    const { rows: [salidas] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [s.pedido.id]
    );
    assert.equal(salidas.n, salidasAntes.n);

    const { rows: [comp] } = await pool.query(
      `SELECT
         SUM(CASE WHEN tipo='AJUSTE_ENTRADA' THEN cantidad ELSE 0 END)::int AS entrada,
         SUM(CASE WHEN tipo='AJUSTE_SALIDA' THEN cantidad ELSE 0 END)::int AS salida
       FROM movimientos_inventario WHERE pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(Number(comp.entrada || 0), 0);
    assert.equal(Number(comp.salida || 0), 0);

    const reintento = await ejecutar(
      ctl.aceptar,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: sid }
    );
    assert.equal(reintento.code, 200);
    assert.equal(reintento.body.idempotente, true);
    const { rows: [ajustesN] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM ajustes_pago WHERE sustitucion_id=$1`,
      [sid]
    );
    assert.equal(ajustesN.n, 1);
  });
});

test('PostgreSQL sustituciones: expiración por preferencia y concurrencia', { skip }, async () => {
  await conEsquema(async (pool, ctl) => {
    const autoAceptar = await semilla(pool, { preferencia: 'ACEPTAR', precioSust: 110 });
    const propA = await ejecutar(
      ctl.proponer,
      { id: autoAceptar.picker.id, rol: 'PICKER' },
      { id: autoAceptar.pedido.id, detalleId: autoAceptar.detalle.id },
      { productoSustitutoId: autoAceptar.sust.id }
    );
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() - interval '1 minute' WHERE id=$1`,
      [propA.body.sustitucion.id]
    );
    const resuelta = await ejecutar(
      ctl.aceptar,
      { id: autoAceptar.cliente.id, rol: 'CLIENTE' },
      { id: propA.body.sustitucion.id }
    );
    assert.equal(resuelta.code, 200);
    assert.equal(resuelta.body.automatica, true);
    assert.equal(resuelta.body.sustitucion.estado, 'ACEPTADA');
    assert.equal(resuelta.body.ajuste.tipo, 'DESCUENTO');
    assert.equal(resuelta.body.ajuste.montoCentimos, 20); // (110-100)*2

    const autoRechazar = await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 100 });
    const propR = await ejecutar(
      ctl.proponer,
      { id: autoRechazar.picker.id, rol: 'PICKER' },
      { id: autoRechazar.pedido.id, detalleId: autoRechazar.detalle.id },
      { productoSustitutoId: autoRechazar.sust.id }
    );
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() - interval '1 minute' WHERE id=$1`,
      [propR.body.sustitucion.id]
    );
    const rech = await ejecutar(
      ctl.resolverExpiradasHttp,
      { id: autoRechazar.picker.id, rol: 'PICKER' },
      {}
    );
    assert.equal(rech.code, 200);
    assert.ok(rech.body.resueltas >= 1);
    const { rows: [filaR] } = await pool.query(
      `SELECT estado, respuesta_automatica FROM sustituciones_pedido WHERE id=$1`,
      [propR.body.sustitucion.id]
    );
    assert.equal(filaR.estado, 'RECHAZADA');
    assert.equal(filaR.respuesta_automatica, true);
    const { rows: [detR] } = await pool.query(
      `SELECT estado_picking FROM pedido_detalle WHERE id=$1`,
      [autoRechazar.detalle.id]
    );
    assert.equal(detR.estado_picking, 'FALTANTE');

    // Concurrencia: dos aceptaciones simultáneas → una aplica, la otra idempotente
    const conc = await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 100 });
    const propC = await ejecutar(
      ctl.proponer,
      { id: conc.picker.id, rol: 'PICKER' },
      { id: conc.pedido.id, detalleId: conc.detalle.id },
      { productoSustitutoId: conc.sust.id }
    );
    const codigos = (
      await Promise.all([
        ejecutar(ctl.aceptar, { id: conc.cliente.id, rol: 'CLIENTE' }, { id: propC.body.sustitucion.id }),
        ejecutar(ctl.aceptar, { id: conc.cliente.id, rol: 'CLIENTE' }, { id: propC.body.sustitucion.id })
      ])
    ).map((r) => r.code);
    assert.deepEqual(codigos.sort(), [200, 200]);
    const { rows: [nAcept] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM sustituciones_pedido WHERE id=$1 AND estado='ACEPTADA'`,
      [propC.body.sustitucion.id]
    );
    assert.equal(nAcept.n, 1);
    const { rows: [movs] } = await pool.query(
      `SELECT
         SUM(CASE WHEN tipo='AJUSTE_SALIDA' THEN 1 ELSE 0 END)::int AS salidas
       FROM movimientos_inventario WHERE pedido_id=$1 AND tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA')`,
      [conc.pedido.id]
    );
    // Aceptación concurrente no genera AJUSTE; el inventario espera al escaneo.
    assert.equal(Number(movs.salidas || 0), 0);
  });
});
