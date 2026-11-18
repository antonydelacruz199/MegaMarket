const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_pk_' + crypto.randomBytes(6).toString('hex');
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
    delete require.cache[require.resolve('../src/controllers/picking.controller')];
    delete require.cache[require.resolve('../src/picking')];
    const picking = require('../src/controllers/picking.controller');

    await fn(pool, picking);

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
    delete require.cache[require.resolve('../src/controllers/picking.controller')];
    delete require.cache[require.resolve('../src/picking')];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, params, body = {}) {
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
  await handler({ usuario, params, body }, res);
  return res;
}

async function semilla(pool, { modalidad = 'CONTRAENTREGA', pagoEstado = 'PENDIENTE' } = {}) {
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Cli','cli','cli','x','CLIENTE') RETURNING id`
  );
  const { rows: [picker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Pick','pick','pick','x','PICKER') RETURNING id`
  );
  const { rows: [otroPicker] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Pick2','pick2','pick2','x','PICKER') RETURNING id`
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES('A') RETURNING id`
  );
  const { rows: [pA] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,pasillo,codigo_barras)
     VALUES('Arroz','M','d',$1,100,50,'B','770001') RETURNING id`,
    [cat.id]
  );
  const { rows: [pB] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,pasillo,codigo_barras)
     VALUES('Aceite','M','d',$1,200,50,'A','770002') RETURNING id`,
    [cat.id]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id)
     VALUES($1,$2,500,'EN_PICKING',$3) RETURNING *`,
    [cliente.id, crypto.randomUUID(), picker.id]
  );
  const { rows: [d1] } = await pool.query(
    `INSERT INTO pedido_detalle(pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos)
     VALUES($1,$2,'Arroz',2,100,200) RETURNING *`,
    [pedido.id, pA.id]
  );
  const { rows: [d2] } = await pool.query(
    `INSERT INTO pedido_detalle(pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos)
     VALUES($1,$2,'Aceite',1,200,200) RETURNING *`,
    [pedido.id, pB.id]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',2)`,
    [crypto.randomUUID(), pA.id, pedido.id, cliente.id]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',1)`,
    [crypto.randomUUID(), pB.id, pedido.id, cliente.id]
  );
  if (modalidad) {
    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
                         numero_operacion_banco, fecha_confirmacion)
       VALUES($1,$2,500,'PEN','QR',$3,$4,$5,$6)`,
      [
        crypto.randomUUID(),
        pedido.id,
        modalidad,
        pagoEstado,
        pagoEstado === 'CONFIRMADO' ? 'OP-1' : null,
        pagoEstado === 'CONFIRMADO' ? new Date().toISOString() : null
      ]
    );
  }
  return { cliente, picker, otroPicker, pA, pB, pedido, d1, d2 };
}

async function contarSalidas(pool, pedidoId) {
  const { rows: [r] } = await pool.query(
    `SELECT COALESCE(SUM(cantidad),0)::int AS n
     FROM movimientos_inventario
     WHERE pedido_id=$1 AND tipo='SALIDA_VENTA' AND deleted_at IS NULL`,
    [pedidoId]
  );
  return r.n;
}

test('PostgreSQL picking: listado por pasillo y aislamiento de asignación', { skip }, async () => {
  await conEsquema(async (pool, picking) => {
    const s = await semilla(pool);
    const { rows: [pedidoAjeno] } = await pool.query(
      `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id)
       VALUES($1,$2,100,'EN_PICKING',$3) RETURNING id`,
      [s.cliente.id, crypto.randomUUID(), s.otroPicker.id]
    );
    await pool.query(
      `INSERT INTO pedido_detalle(pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos)
       VALUES($1,$2,'Arroz',1,100,100)`,
      [pedidoAjeno.id, s.pA.id]
    );

    const lista = await ejecutar(picking.listar, { id: s.picker.id, rol: 'PICKER' }, {});
    assert.equal(lista.code, 200);
    assert.equal(lista.body.pedidos.length, 1);
    assert.equal(lista.body.pedidos[0].pedido.id, s.pedido.id);
    const nombres = lista.body.pedidos[0].detalles.map((d) => d.nombreProducto);
    assert.deepEqual(nombres, ['Aceite', 'Arroz']); // pasillo A luego B
    assert.equal(lista.body.pedidos[0].detalles[0].pasillo, 'A');

    const ajeno = await ejecutar(
      picking.obtenerPorId,
      { id: s.picker.id, rol: 'PICKER' },
      { id: pedidoAjeno.id }
    );
    assert.equal(ajeno.code, 403);
    assert.equal(ajeno.body.codigo, 'PEDIDO_NO_ASIGNADO');
  });
});

test('PostgreSQL picking: escaneo correcto, incorrecto, exceso y sin redecontar stock', { skip }, async () => {
  await conEsquema(async (pool, picking) => {
    const s = await semilla(pool);
    const salidasAntes = await contarSalidas(pool, s.pedido.id);
    const { rows: [stockAntes] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.pA.id
    ]);

    const mal = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '999999', cantidad: 1 }
    );
    assert.equal(mal.code, 409);
    assert.equal(mal.body.codigo, 'CODIGO_INCORRECTO');
    assert.equal(mal.body.escaneo.resultado, 'INCORRECTO');
    assert.equal(mal.body.detalle.cantidadArmada, 0);

    const ok1 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    assert.equal(ok1.code, 201);
    assert.equal(ok1.body.detalle.cantidadArmada, 1);
    assert.equal(ok1.body.detalle.estadoPicking, 'PENDIENTE');

    const ok2 = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    assert.equal(ok2.code, 201);
    assert.equal(ok2.body.detalle.cantidadArmada, 2);
    assert.equal(ok2.body.detalle.estadoPicking, 'ARMADO');

    const exceso = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    assert.equal(exceso.code, 409);
    assert.equal(exceso.body.codigo, 'CANTIDAD_EXCEDIDA');

    assert.equal(await contarSalidas(pool, s.pedido.id), salidasAntes);
    const { rows: [stockDespues] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.pA.id
    ]);
    assert.equal(Number(stockDespues.stock), Number(stockAntes.stock));
  });
});

test('PostgreSQL picking: reintento UUID idempotente y concurrencia última unidad', { skip }, async () => {
  await conEsquema(async (pool, picking) => {
    const s = await semilla(pool);
    const uuid = crypto.randomUUID();
    const body = { uuidOperacion: uuid, codigoBarras: '770002', cantidad: 1 };
    const primero = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d2.id },
      body
    );
    assert.equal(primero.code, 201);
    const reintento = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d2.id },
      body
    );
    assert.equal(reintento.code, 200);
    assert.equal(reintento.body.idempotente, true);
    assert.equal(reintento.body.detalle.cantidadArmada, 1);
    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM picking_escaneos WHERE pedido_detalle_id=$1`,
      [s.d2.id]
    );
    assert.equal(n.n, 1);

    // Reset línea d1 a 1 de 2 armadas para pelear la última unidad
    await pool.query(
      `UPDATE pedido_detalle SET cantidad_armada=1, estado_picking='PENDIENTE' WHERE id=$1`,
      [s.d1.id]
    );
    const codigos = (
      await Promise.all([
        ejecutar(
          picking.escanear,
          { id: s.picker.id, rol: 'PICKER' },
          { id: s.pedido.id, detalleId: s.d1.id },
          { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
        ),
        ejecutar(
          picking.escanear,
          { id: s.picker.id, rol: 'PICKER' },
          { id: s.pedido.id, detalleId: s.d1.id },
          { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
        )
      ])
    ).map((r) => r.code);
    assert.deepEqual(codigos.sort(), [201, 409]);
    const { rows: [linea] } = await pool.query(
      `SELECT cantidad_armada, estado_picking FROM pedido_detalle WHERE id=$1`,
      [s.d1.id]
    );
    assert.equal(Number(linea.cantidad_armada), 2);
    assert.equal(linea.estado_picking, 'ARMADO');
  });
});

test('PostgreSQL picking: faltante, ONLINE pendiente bloqueado, aislamiento entre pedidos', { skip }, async () => {
  await conEsquema(async (pool, picking) => {
    const s = await semilla(pool);
    const faltante = await ejecutar(
      picking.marcarFaltante,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d2.id },
      {}
    );
    assert.equal(faltante.code, 200);
    assert.equal(faltante.body.detalle.estadoPicking, 'FALTANTE');
    const faltante2 = await ejecutar(
      picking.marcarFaltante,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d2.id },
      {}
    );
    assert.equal(faltante2.code, 200);
    assert.equal(faltante2.body.idempotente, true);

    const { rows: [pedidoOnline] } = await pool.query(
      `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, picker_id)
       VALUES($1,$2,100,'EN_PICKING',$3) RETURNING id`,
      [s.cliente.id, crypto.randomUUID(), s.picker.id]
    );
    const { rows: [detalleOnline] } = await pool.query(
      `INSERT INTO pedido_detalle(pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos)
       VALUES($1,$2,'Arroz',1,100,100) RETURNING id`,
      [pedidoOnline.id, s.pA.id]
    );
    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
       VALUES($1,$2,100,'PEN','QR','ONLINE','PENDIENTE')`,
      [crypto.randomUUID(), pedidoOnline.id]
    );
    const bloqueado = await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: pedidoOnline.id, detalleId: detalleOnline.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    assert.equal(bloqueado.code, 409);
    assert.equal(bloqueado.body.codigo, 'PICKING_REQUIERE_PAGO');

    // Escaneo en pedido A no altera cantidad_armada del pedido ONLINE
    const armadaBAntes = Number(
      (
        await pool.query(`SELECT cantidad_armada FROM pedido_detalle WHERE id=$1`, [
          detalleOnline.id
        ])
      ).rows[0].cantidad_armada
    );
    await ejecutar(
      picking.escanear,
      { id: s.picker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    const armadaBDespues = Number(
      (
        await pool.query(`SELECT cantidad_armada FROM pedido_detalle WHERE id=$1`, [
          detalleOnline.id
        ])
      ).rows[0].cantidad_armada
    );
    assert.equal(armadaBDespues, armadaBAntes);

    const noAsignado = await ejecutar(
      picking.escanear,
      { id: s.otroPicker.id, rol: 'PICKER' },
      { id: s.pedido.id, detalleId: s.d1.id },
      { uuidOperacion: crypto.randomUUID(), codigoBarras: '770001', cantidad: 1 }
    );
    assert.equal(noAsignado.code, 403);
    assert.equal(noAsignado.body.codigo, 'PEDIDO_NO_ASIGNADO');
  });
});

test('reglas picking: ONLINE pendiente vs CONTRAENTREGA', () => {
  const { puedePrepararSegunPago } = require('../src/picking');
  assert.equal(puedePrepararSegunPago(null).ok, true);
  assert.equal(puedePrepararSegunPago({ modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' }).ok, true);
  assert.equal(puedePrepararSegunPago({ modalidad: 'ONLINE', estado: 'PENDIENTE' }).ok, false);
  assert.equal(puedePrepararSegunPago({ modalidad: 'ONLINE', estado: 'CONFIRMADO' }).ok, true);
});
