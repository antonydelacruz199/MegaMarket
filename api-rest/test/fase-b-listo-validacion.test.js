/**
 * Fase B: validación estricta EN_PICKING → CONFIRMADO (→ LISTO CONTRAENTREGA) (PostgreSQL aislado).
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_lb_' + crypto.randomBytes(6).toString('hex');
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
      '../src/controllers/pedidos.controller',
      '../src/controllers/picking.controller',
      '../src/controllers/sustituciones.controller',
      '../src/picking',
      '../src/pedidosEstados',
      '../src/sustituciones'
    ]) {
      try {
        delete require.cache[require.resolve(mod)];
      } catch (_e) {
        // ignore missing
      }
    }
    const pedidos = require('../src/controllers/pedidos.controller');
    const picking = require('../src/controllers/picking.controller');
    const sust = require('../src/controllers/sustituciones.controller');
    await fn(pool, pedidos, picking, sust);

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
  const { rows: [otroPicker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','PICKER') RETURNING id`,
    [`op_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Cat_${tag}`]
  );
  const codOrig = `88${tag}01`;
  const codSust = `88${tag}02`;
  const { rows: [orig] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'A','d',$1,100,20,$3,'A') RETURNING id`,
    [cat.id, `Orig ${tag}`, codOrig]
  );
  const { rows: [sust] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,codigo_barras,pasillo)
     VALUES($2,'B','d',$1,120,20,$3,'B') RETURNING id`,
    [cat.id, `Sust ${tag}`, codSust]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id, preferencia_sustitucion)
     VALUES($1,$2,200,'EN_PICKING',$3,'RECHAZAR') RETURNING id`,
    [cliente.id, crypto.randomUUID(), picker.id]
  );
  const { rows: [det] } = await pool.query(
    `INSERT INTO pedido_detalle(
       pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos,
       estado_picking, cantidad_armada
     ) VALUES($1,$2,$3,2,100,200,'PENDIENTE',0) RETURNING id`,
    [pedido.id, orig.id, `Orig ${tag}`]
  );
  await pool.query(
    `INSERT INTO pagos(uuid_operacion,pedido_id,monto_centimos,moneda,medio_pago,modalidad,estado)
     VALUES($1,$2,200,'PEN','QR','CONTRAENTREGA','PENDIENTE')`,
    [crypto.randomUUID(), pedido.id]
  );
  return {
    tag,
    cliente,
    picker,
    otroPicker,
    orig,
    sust,
    pedido,
    det,
    codOrig,
    codSust,
    usuarioPicker: { id: picker.id, rol: 'PICKER' },
    usuarioOtro: { id: otroPicker.id, rol: 'PICKER' },
    usuarioCliente: { id: cliente.id, rol: 'CLIENTE' }
  };
}

test(
  'Fase B: pedido totalmente preparado pasa a CONFIRMADO/LISTO sin inventario',
  { skip },
  async () => {
    await conEsquema(async (pool, pedidos, picking) => {
      const s = await semilla(pool);
      const esc1 = await ejecutar(
        picking.escanear,
        s.usuarioPicker,
        { id: s.pedido.id, detalleId: s.det.id },
        { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codOrig, cantidad: 1 }
      );
      assert.equal(esc1.code, 201);
      const esc2 = await ejecutar(
        picking.escanear,
        s.usuarioPicker,
        { id: s.pedido.id, detalleId: s.det.id },
        { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codOrig, cantidad: resteCant(esc1) }
      );
      assert.equal(esc2.code, 201);

      const { rows: movAntes } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1`,
        [s.pedido.id]
      );

      // Si el último escaneo ya cerró preparación, la transición es idempotente.
      const cierre = await ejecutar(
        pedidos.transicionar,
        s.usuarioPicker,
        { id: s.pedido.id },
        { estado: 'CONFIRMADO' }
      );
      assert.equal(cierre.code, 200);
      assert.equal(cierre.body.pedido.estado, 'LISTO');

      const { rows: movDespues } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1`,
        [s.pedido.id]
      );
      assert.equal(movDespues[0].n, movAntes[0].n);

      const idem = await ejecutar(
        pedidos.transicionar,
        s.usuarioPicker,
        { id: s.pedido.id },
        { estado: 'CONFIRMADO' }
      );
      assert.equal(idem.code, 200);
      assert.equal(idem.body.idempotente, true);
    });
  }
);

function resteCant(esc) {
  const d = esc.body.detalle;
  return Math.max(1, Number(d.cantidad) - Number(d.cantidadArmada));
}

test('Fase B: productos pendientes / cantidades incompletas / faltantes', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const s = await semilla(pool);

    const pend = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(pend.code, 409);
    assert.equal(pend.body.codigo, 'PICKING_INCOMPLETO');
    assert.ok(Array.isArray(pend.body.impedimentos));

    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='ARMADO', cantidad_armada=1 WHERE id=$1`,
      [s.det.id]
    );
    const cant = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(cant.code, 409);
    assert.equal(cant.body.codigo, 'CANTIDAD_INCOMPLETA');

    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='FALTANTE', cantidad_armada=0 WHERE id=$1`,
      [s.det.id]
    );
    const falt = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(falt.code, 409);
    assert.equal(falt.body.codigo, 'PICKING_INCOMPLETO');
    assert.equal(falt.body.impedimentos[0].codigo, 'LINEA_FALTANTE');
  });
});

test('Fase B: sustituciones pendientes y aceptadas sin escanear', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const s = await semilla(pool);
    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='FALTANTE' WHERE id=$1`,
      [s.det.id]
    );
    const { rows: [prop] } = await pool.query(
      `INSERT INTO sustituciones_pedido(
         pedido_detalle_id, producto_original_id, producto_sustituto_id, picker_id,
         cantidad, precio_original_centimos, precio_sustituto_centimos, estado,
         fecha_propuesta, fecha_limite
       ) VALUES ($1,$2,$3,$4,2,100,120,'PENDIENTE', now(), now() + interval '5 minutes')
       RETURNING id`,
      [s.det.id, s.orig.id, s.sust.id, s.picker.id]
    );

    const pend = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(pend.code, 409);
    assert.equal(pend.body.codigo, 'SUSTITUCION_PENDIENTE');

    await pool.query(
      `UPDATE sustituciones_pedido SET estado='ACEPTADA', fecha_respuesta=now() WHERE id=$1`,
      [prop.id]
    );
    const sinScan = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(sinScan.code, 409);
    assert.equal(sinScan.body.codigo, 'SUSTITUTO_SIN_ESCANEAR');
  });
});

test('Fase B: sustituto escaneado completo permite CONFIRMADO/LISTO', { skip }, async () => {
  await conEsquema(async (pool, pedidos, picking, sustCtrl) => {
    const s = await semilla(pool);
    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='FALTANTE' WHERE id=$1`,
      [s.det.id]
    );
    const prop = await ejecutar(
      sustCtrl.proponer,
      s.usuarioPicker,
      { id: s.pedido.id, detalleId: s.det.id },
      { productoSustitutoId: s.sust.id }
    );
    assert.equal(prop.code, 201, JSON.stringify(prop.body));

    const acc = await ejecutar(
      sustCtrl.aceptar,
      s.usuarioCliente,
      { id: prop.body.sustitucion.id },
      {}
    );
    assert.equal(acc.code, 200, JSON.stringify(acc.body));

    // Aceptar NO debe pasar el pedido a CONFIRMADO/LISTO.
    const { rows: [pedTrasAceptar] } = await pool.query(
      `SELECT estado FROM pedidos WHERE id=$1`,
      [s.pedido.id]
    );
    assert.equal(pedTrasAceptar.estado, 'EN_PICKING');

    const cierrePrematuro = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(cierrePrematuro.code, 409);
    assert.equal(cierrePrematuro.body.codigo, 'SUSTITUTO_SIN_ESCANEAR');

    const e1 = await ejecutar(
      picking.escanear,
      s.usuarioPicker,
      { id: s.pedido.id, detalleId: s.det.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(e1.code, 201, JSON.stringify(e1.body));
    const e2 = await ejecutar(
      picking.escanear,
      s.usuarioPicker,
      { id: s.pedido.id, detalleId: s.det.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codSust, cantidad: 1 }
    );
    assert.equal(e2.code, 201, JSON.stringify(e2.body));
    assert.equal(e2.body.detalle.estadoPicking, 'SUSTITUIDO');

    const cierre = await ejecutar(
      pedidos.transicionar,
      s.usuarioPicker,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(cierre.code, 200);
    assert.equal(cierre.body.pedido.estado, 'LISTO');
  });
});

test('Fase B: autorización picker ajeno y concurrencia escaneo/cierre', { skip }, async () => {
  await conEsquema(async (pool, pedidos, picking) => {
    const s = await semilla(pool);
    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='ARMADO', cantidad_armada=2 WHERE id=$1`,
      [s.det.id]
    );

    const ajeno = await ejecutar(
      pedidos.transicionar,
      s.usuarioOtro,
      { id: s.pedido.id },
      { estado: 'CONFIRMADO' }
    );
    assert.equal(ajeno.code, 403);
    assert.equal(ajeno.body.codigo, 'PEDIDO_NO_ASIGNADO');

    // Concurrencia: un escaneo incorrecto no debe dejar pasar un cierre si se reabre incompleto.
    await pool.query(
      `UPDATE pedido_detalle SET estado_picking='PENDIENTE', cantidad_armada=1 WHERE id=$1`,
      [s.det.id]
    );
    const [scanRes, cierreRes] = await Promise.all([
      ejecutar(
        picking.escanear,
        s.usuarioPicker,
        { id: s.pedido.id, detalleId: s.det.id },
        { uuidOperacion: crypto.randomUUID(), codigoBarras: s.codOrig, cantidad: 1 }
      ),
      ejecutar(
        pedidos.transicionar,
        s.usuarioPicker,
        { id: s.pedido.id },
        { estado: 'CONFIRMADO' }
      )
    ]);
    // Uno completa a ARMADO/LISTO o el cierre ve incompleto; nunca inconsistencia.
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [
      s.pedido.id
    ]);
    const { rows: [det] } = await pool.query(
      `SELECT estado_picking, cantidad_armada FROM pedido_detalle WHERE id=$1`,
      [s.det.id]
    );
    if (ped.estado === 'LISTO') {
      assert.equal(det.estado_picking, 'ARMADO');
      assert.equal(Number(det.cantidad_armada), 2);
      assert.ok(scanRes.code === 201 || cierreRes.code === 200);
    } else {
      assert.equal(ped.estado, 'EN_PICKING');
      // Si el cierre ganó la carrera con línea incompleta → 409; si el escaneo completó después, puede quedar ARMADO.
      assert.ok(
        cierreRes.code === 409 ||
          (cierreRes.code === 200 && Number(det.cantidad_armada) === 2),
        JSON.stringify({ scan: scanRes.code, cierre: cierreRes.code, det, ped })
      );
    }
  });
});
