/**
 * Consistencia de aceptación manual/automática y resolución de líneas rechazadas.
 * PostgreSQL aislado: no usa DATABASE_URL ni Neon de producción.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_rs_' + crypto.randomBytes(6).toString('hex');
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
      '../src/controllers/picking.controller',
      '../src/controllers/pedidos.controller',
      '../src/sustituciones',
      '../src/picking',
      '../src/jobs/sustitucionesExpiradas'
    ]) {
      delete require.cache[require.resolve(mod)];
    }
    const sust = require('../src/controllers/sustituciones.controller');
    const picking = require('../src/controllers/picking.controller');
    const pedidos = require('../src/controllers/pedidos.controller');
    await fn(pool, sust, picking, pedidos);

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

async function semilla(pool, opciones = {}) {
  const tag = crypto.randomBytes(3).toString('hex');
  const preferencia = opciones.preferencia || 'RECHAZAR';
  const precioSust = opciones.precioSust == null ? 90 : opciones.precioSust;
  const confirmado = Boolean(opciones.pagoConfirmado);
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES($1,$1,$1,'x','CLIENTE',$2) RETURNING id`,
    [`c_${tag}`, preferencia]
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
  const codOrig = `88${tag}01`;
  const codSust = `88${tag}02`;
  const codOtro = `88${tag}03`;
  const { rows: [orig] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'A','d',$1,100,20,$3,'A') RETURNING id`,
    [cat.id, `Orig ${tag}`, codOrig]
  );
  const { rows: [sustProd] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'B','d',$1,$4,20,$3,'B') RETURNING id`,
    [cat.id, `Sust ${tag}`, codSust, precioSust]
  );
  const { rows: [otro] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'C','d',$1,100,20,$3,'C') RETURNING id`,
    [cat.id, `Otro ${tag}`, codOtro]
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
    [pedido.id, orig.id, `Orig ${tag}`]
  );
  if (opciones.lineaArmada) {
    await pool.query(
      `INSERT INTO pedido_detalle(
         pedido_id, producto_id, nombre_producto, cantidad,
         precio_unitario_centimos, subtotal_centimos, estado_picking, cantidad_armada
       ) VALUES($1,$2,'Extra',1,100,100,'ARMADO',1)`,
      [pedido.id, otro.id]
    );
  }
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',2)`,
    [crypto.randomUUID(), orig.id, pedido.id, cliente.id]
  );
  await pool.query(`UPDATE productos SET stock = stock - 2 WHERE id=$1`, [orig.id]);
  if (confirmado) {
    await pool.query(
      `INSERT INTO pagos(
         uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
         numero_operacion_banco, fecha_confirmacion
       ) VALUES($1,$2,200,'PEN','QR','ONLINE','CONFIRMADO','OP-LAB',now())`,
      [crypto.randomUUID(), pedido.id]
    );
  } else {
    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
       VALUES($1,$2,200,'PEN','QR','CONTRAENTREGA','PENDIENTE')`,
      [crypto.randomUUID(), pedido.id]
    );
  }
  return { cliente, picker, orig, sustProd, otro, pedido, detalle, codOrig, codSust, codOtro };
}

async function proponer(sust, s, productoId) {
  return ejecutar(
    sust.proponer,
    { id: s.picker.id, rol: 'PICKER' },
    { id: s.pedido.id, detalleId: s.detalle.id },
    { productoSustitutoId: productoId }
  );
}

async function foto(pool, s, sustitucionId) {
  const { rows: [det] } = await pool.query(
    `SELECT estado_picking, cantidad_armada, producto_id, precio_unitario_centimos, subtotal_centimos
     FROM pedido_detalle WHERE id=$1`,
    [s.detalle.id]
  );
  const { rows: [sus] } = await pool.query(
    `SELECT estado, respuesta_automatica, producto_original_id, producto_sustituto_id
     FROM sustituciones_pedido WHERE id=$1`,
    [sustitucionId]
  );
  const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
  const { rows: [aj] } = await pool.query(
    `SELECT tipo, motivo, monto_centimos, estado FROM ajustes_pago WHERE sustitucion_id=$1`,
    [sustitucionId]
  );
  const { rows: [mov] } = await pool.query(
    `SELECT COUNT(*)::int AS n FROM movimientos_inventario
     WHERE pedido_id=$1 AND tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA')`,
    [s.pedido.id]
  );
  const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
  const { rows: [stockS] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.sustProd.id]);
  return {
    estadoPicking: det.estado_picking,
    armada: Number(det.cantidad_armada),
    productoId: det.producto_id,
    precio: Number(det.precio_unitario_centimos),
    subtotal: Number(det.subtotal_centimos),
    estadoSustitucion: sus.estado,
    automatica: sus.respuesta_automatica,
    originalId: sus.producto_original_id,
    sustitutoId: sus.producto_sustituto_id,
    pedido: ped.estado,
    ajuste: aj
      ? { tipo: aj.tipo, motivo: aj.motivo, monto: Number(aj.monto_centimos), estado: aj.estado }
      : null,
    ajustesInventario: mov.n,
    stockOriginal: Number(stock.stock),
    stockSustituto: Number(stockS.stock)
  };
}

test('aceptación manual y automática dejan la misma línea escaneable y no marcan LISTO', { skip }, async () => {
  await conEsquema(async (pool, sust, picking) => {
    const manual = await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 90, lineaArmada: true });
    const propM = await proponer(sust, manual, manual.sustProd.id);
    assert.equal(propM.code, 201, propM.body && propM.body.error);
    const accM = await ejecutar(
      sust.aceptar,
      { id: manual.cliente.id, rol: 'CLIENTE' },
      { id: propM.body.sustitucion.id }
    );
    assert.equal(accM.code, 200, accM.body && accM.body.error);
    assert.equal(accM.body.automatica, undefined);
    const fotoM = await foto(pool, manual, propM.body.sustitucion.id);

    const auto = await semilla(pool, { preferencia: 'ACEPTAR', precioSust: 90, lineaArmada: true });
    const propA = await proponer(sust, auto, auto.sustProd.id);
    assert.equal(propA.code, 201, propA.body && propA.body.error);
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() - interval '1 minute' WHERE id=$1`,
      [propA.body.sustitucion.id]
    );
    const db = await pool.connect();
    try {
      await db.query('BEGIN');
      const aplicados = await sust.resolverExpiradas(db, { pedidoId: auto.pedido.id });
      await db.query('COMMIT');
      assert.equal(aplicados.length, 1);
      assert.equal(aplicados[0].estado, 'ACEPTADA');
      assert.equal(aplicados[0].respuestaAutomatica, true);
    } finally {
      db.release();
    }
    const fotoA = await foto(pool, auto, propA.body.sustitucion.id);

    assert.equal(fotoM.estadoSustitucion, 'ACEPTADA');
    assert.equal(fotoA.estadoSustitucion, 'ACEPTADA');
    assert.equal(fotoM.automatica, false);
    assert.equal(fotoA.automatica, true);
    for (const clave of [
      'estadoPicking', 'armada', 'precio', 'subtotal', 'pedido',
      'ajustesInventario', 'stockOriginal', 'stockSustituto'
    ]) {
      assert.equal(fotoA[clave], fotoM[clave], clave);
    }
    assert.equal(fotoM.estadoPicking, 'FALTANTE');
    assert.equal(fotoM.armada, 0);
    assert.equal(fotoM.productoId, manual.orig.id);
    assert.equal(fotoA.productoId, auto.orig.id);
    assert.equal(fotoM.pedido, 'EN_PICKING');
    assert.equal(fotoM.ajuste.tipo, 'DEVOLUCION');
    assert.equal(fotoM.ajuste.motivo, 'SUSTITUCION');
    assert.equal(fotoM.ajuste.monto, 20);
    assert.equal(fotoM.ajuste.estado, 'PENDIENTE');
    assert.deepEqual(fotoA.ajuste, fotoM.ajuste);
    assert.equal(fotoM.ajustesInventario, 0);
    assert.equal(fotoM.stockOriginal, 18);
    assert.equal(fotoM.stockSustituto, 20);

    for (const caso of [
      [manual, propM.body.sustitucion.id],
      [auto, propA.body.sustitucion.id]
    ]) {
      const [s, sid] = caso;
      const dup = await proponer(sust, s, s.otro.id);
      assert.equal(dup.code, 409);
      assert.equal(dup.body.codigo, 'SUSTITUCION_ACEPTADA');
      const mal = await ejecutar(
        picking.escanear,
        { id: s.picker.id, rol: 'PICKER' },
        { id: s.pedido.id, detalleId: s.detalle.id },
        { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codOrig, cantidad: 1 }
      );
      assert.equal(mal.code, 409);
      assert.equal(mal.body.codigo, 'CODIGO_INCORRECTO');
      const ok = await ejecutar(
        picking.escanear,
        { id: s.picker.id, rol: 'PICKER' },
        { id: s.pedido.id, detalleId: s.detalle.id },
        { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
      );
      assert.equal(ok.code, 201, ok.body && ok.body.error);
      assert.equal(ok.body.detalle.estadoPicking, 'PENDIENTE');
      assert.equal(ok.body.detalle.cantidadArmada, 1);
      assert.equal(ok.body.detalle.sustitucionAceptadaId, sid);
      assert.equal(ok.body.detalle.codigoBarras, s.codSust);
      assert.equal(ok.body.detalle.productoId, s.orig.id);
      assert.equal(ok.body.pedidoListo, false);
      const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
      assert.equal(ped.estado, 'EN_PICKING');
    }
  });
});

test('rechazo permite otro sustituto y la eliminación exige ese rechazo', { skip }, async () => {
  await conEsquema(async (pool, sust, picking) => {
    const s = await semilla(pool, { pagoConfirmado: true });
    const { rows: [pagoAntes] } = await pool.query(
      `SELECT estado, monto_centimos, numero_operacion_banco, modalidad FROM pagos WHERE pedido_id=$1`,
      [s.pedido.id]
    );
    const { rows: [stockAntes] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);

    const sin = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(sin.code, 409);
    assert.equal(sin.body.codigo, 'ELIMINADO_SIN_RESOLUCION');

    const prop = await proponer(sust, s, s.sustProd.id);
    assert.equal(prop.code, 201);
    const pendiente = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(pendiente.code, 409);
    assert.equal(pendiente.body.codigo, 'SUSTITUCION_PENDIENTE');

    const rech = await ejecutar(
      sust.rechazar,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: prop.body.sustitucion.id }
    );
    assert.equal(rech.code, 200);
    assert.equal(rech.body.sustitucion.estado, 'RECHAZADA');
    const { rows: [detR] } = await pool.query(
      `SELECT estado_picking, cantidad_armada, producto_id FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(detR.estado_picking, 'FALTANTE');
    assert.equal(Number(detR.cantidad_armada), 0);
    assert.equal(detR.producto_id, s.orig.id);

    const otra = await proponer(sust, s, s.otro.id);
    assert.equal(otra.code, 201, otra.body && otra.body.error);
    const { rows: hist } = await pool.query(
      `SELECT estado, producto_original_id, producto_sustituto_id
       FROM sustituciones_pedido WHERE pedido_detalle_id=$1 ORDER BY created_at`,
      [s.detalle.id]
    );
    assert.equal(hist.length, 2);
    assert.equal(hist[0].estado, 'RECHAZADA');
    assert.equal(hist[1].estado, 'PENDIENTE');
    assert.equal(hist[0].producto_original_id, s.orig.id);
    assert.equal(hist[1].producto_sustituto_id, s.otro.id);

    const bloqueada = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(bloqueada.code, 409);
    assert.equal(bloqueada.body.codigo, 'SUSTITUCION_PENDIENTE');

    const rech2 = await ejecutar(
      sust.rechazar,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: otra.body.sustitucion.id }
    );
    assert.equal(rech2.code, 200);

    const { rows: [stockTrasRechazo] } = await pool.query(
      `SELECT stock FROM productos WHERE id=$1`,
      [s.orig.id]
    );
    assert.equal(Number(stockTrasRechazo.stock), Number(stockAntes.stock));
    const { rows: [ajustesRechazo] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM ajustes_pago a
       JOIN pagos p ON p.id = a.pago_id WHERE p.pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(ajustesRechazo.n, 0);

    const eli = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(eli.code, 200, eli.body && eli.body.error);
    assert.equal(eli.body.idempotente, false);
    assert.equal(eli.body.detalle.estadoPicking, 'ELIMINADO');
    assert.equal(eli.body.detalle.cantidadArmada, 0);
    assert.equal(eli.body.detalle.productoId, s.orig.id);
    assert.equal(eli.body.ajuste.tipo, 'DEVOLUCION');
    assert.equal(eli.body.ajuste.motivo, 'PRODUCTO_FALTANTE');
    assert.equal(eli.body.ajuste.montoCentimos, 200);
    assert.equal(eli.body.ajuste.estado, 'PENDIENTE');
    assert.equal(eli.body.pedidoListo, true);
    assert.equal(eli.body.estadoPedido, 'LISTO');

    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, cantidad_armada, producto_id, precio_unitario_centimos, subtotal_centimos
       FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(det.estado_picking, 'ELIMINADO');
    assert.equal(Number(det.cantidad_armada), 0);
    assert.equal(det.producto_id, s.orig.id);
    assert.equal(Number(det.precio_unitario_centimos), 100);
    assert.equal(Number(det.subtotal_centimos), 200);

    const { rows: propuestas } = await pool.query(
      `SELECT estado FROM sustituciones_pedido WHERE pedido_detalle_id=$1 ORDER BY created_at`,
      [s.detalle.id]
    );
    assert.deepEqual(propuestas.map((p) => p.estado), ['RECHAZADA', 'RECHAZADA']);

    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
    assert.equal(Number(stock.stock), Number(stockAntes.stock) + 2);
    const { rows: [mov] } = await pool.query(
      `SELECT COUNT(*)::int AS n, COALESCE(SUM(cantidad),0)::int AS cant
       FROM movimientos_inventario
       WHERE pedido_id=$1 AND producto_id=$2 AND tipo='AJUSTE_ENTRADA'`,
      [s.pedido.id, s.orig.id]
    );
    assert.equal(mov.n, 1);
    assert.equal(mov.cant, 2);

    const { rows: [pago] } = await pool.query(
      `SELECT estado, monto_centimos, numero_operacion_banco, modalidad FROM pagos WHERE pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(pago.estado, pagoAntes.estado);
    assert.equal(Number(pago.monto_centimos), Number(pagoAntes.monto_centimos));
    assert.equal(pago.numero_operacion_banco, pagoAntes.numero_operacion_banco);
    assert.equal(pago.modalidad, pagoAntes.modalidad);
    assert.equal(pago.estado, 'CONFIRMADO');

    const repetir = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(repetir.code, 200, repetir.body && repetir.body.error);
    assert.equal(repetir.body.idempotente, true);
    const { rows: [stock2] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
    assert.equal(Number(stock2.stock), Number(stock.stock));
    const { rows: [nAj] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM ajustes_pago a
       JOIN pagos p ON p.id = a.pago_id WHERE p.pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(nAj.n, 1);
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
    assert.equal(ped.estado, 'LISTO');
  });
});

test('rechazo automático se elimina una sola vez y el cierre concurrente no duplica stock', { skip }, async () => {
  await conEsquema(async (pool, sust, picking) => {
    const s = await semilla(pool, { preferencia: 'RECHAZAR', precioSust: 100 });
    const prop = await proponer(sust, s, s.sustProd.id);
    await pool.query(
      `UPDATE sustituciones_pedido SET fecha_limite = now() - interval '1 minute' WHERE id=$1`,
      [prop.body.sustitucion.id]
    );
    const db = await pool.connect();
    try {
      await db.query('BEGIN');
      const aplicados = await sust.resolverExpiradas(db, { pedidoId: s.pedido.id });
      await db.query('COMMIT');
      assert.equal(aplicados[0].estado, 'RECHAZADA');
      assert.equal(aplicados[0].respuestaAutomatica, true);
    } finally {
      db.release();
    }
    const { rows: [antes] } = await pool.query(
      `SELECT estado_picking FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(antes.estado_picking, 'FALTANTE');
    const { rows: [pedAntes] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
    assert.equal(pedAntes.estado, 'EN_PICKING');

    const { rows: [stockAntes] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
    const respuestas = await Promise.all([
      ejecutar(
        picking.eliminarLinea,
        { id: s.picker.id, rol: 'PICKER' },
        { id: s.pedido.id, detalleId: s.detalle.id }
      ),
      ejecutar(
        picking.eliminarLinea,
        { id: s.picker.id, rol: 'PICKER' },
        { id: s.pedido.id, detalleId: s.detalle.id }
      )
    ]);
    assert.ok(respuestas.every((r) => r.code === 200), JSON.stringify(respuestas.map((r) => r.body)));
    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, cantidad_armada, producto_id FROM pedido_detalle WHERE id=$1`,
      [s.detalle.id]
    );
    assert.equal(det.estado_picking, 'ELIMINADO');
    assert.equal(Number(det.cantidad_armada), 0);
    assert.equal(det.producto_id, s.orig.id);
    const { rows: [sus] } = await pool.query(
      `SELECT estado, respuesta_automatica, producto_original_id FROM sustituciones_pedido WHERE id=$1`,
      [prop.body.sustitucion.id]
    );
    assert.equal(sus.estado, 'RECHAZADA');
    assert.equal(sus.respuesta_automatica, true);
    assert.equal(sus.producto_original_id, s.orig.id);
    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
    assert.equal(Number(stock.stock), Number(stockAntes.stock) + 2);
    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS movs FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo='AJUSTE_ENTRADA'`,
      [s.pedido.id]
    );
    assert.equal(n.movs, 1);
    const { rows: [aj] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM ajustes_pago a
       JOIN pagos p ON p.id = a.pago_id WHERE p.pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(aj.n, 1);
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
    assert.equal(ped.estado, 'LISTO');
  });
});

test('sustituto aceptado no se elimina; escanearlo completo sí puede cerrar', { skip }, async () => {
  await conEsquema(async (pool, sust, picking) => {
    const s = await semilla(pool);
    const prop = await proponer(sust, s, s.sustProd.id);
    const acc = await ejecutar(
      sust.aceptar,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { id: prop.body.sustitucion.id }
    );
    assert.equal(acc.code, 200);
    const no = await ejecutar(
      picking.eliminarLinea,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id }
    );
    assert.equal(no.code, 409);
    assert.equal(no.body.codigo, 'SUSTITUCION_ACEPTADA');
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [s.pedido.id]);
    assert.equal(ped.estado, 'EN_PICKING');

    const u1 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(u1.code, 201);
    assert.equal(u1.body.pedidoListo, false);
    const u2 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.detalle.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(u2.code, 201, u2.body && u2.body.error);
    assert.equal(u2.body.detalle.estadoPicking, 'SUSTITUIDO');
    assert.equal(u2.body.detalle.cantidadArmada, 2);
    assert.equal(u2.body.pedidoListo, true);
    const { rows: [stockO] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.orig.id]);
    const { rows: [stockS] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [s.sustProd.id]);
    assert.equal(Number(stockO.stock), 20);
    assert.equal(Number(stockS.stock), 18);
    const { rows: [mov] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario
       WHERE pedido_id=$1 AND tipo IN ('AJUSTE_ENTRADA','AJUSTE_SALIDA')`,
      [s.pedido.id]
    );
    assert.equal(mov.n, 2);
  });
});
