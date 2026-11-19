const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');
const {
  puedeMarcarEntregadoSegunPago,
  validarEvidencia
} = require('../src/entregas');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_en_' + crypto.randomBytes(6).toString('hex');
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
    for (const mod of ['../src/controllers/entregas.controller', '../src/entregas']) {
      delete require.cache[require.resolve(mod)];
    }
    const entregas = require('../src/controllers/entregas.controller');
    await fn(pool, entregas);

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
  await handler({ usuario, params, body }, res);
  return res;
}

async function semilla(
  pool,
  {
    estado = 'EN_CAMINO',
    modalidad = 'CONTRAENTREGA',
    pagoEstado = 'CONFIRMADO',
    conPago = true,
    stock = 5,
    /** null = legado histórico; string = pedido nuevo con contrato */
    solicitudHash = 'hash-nuevo'
  } = {}
) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
    [`cl_${tag}`]
  );
  const { rows: [repartidor] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','REPARTIDOR') RETURNING id`,
    [`rp_${tag}`]
  );
  const { rows: [otro] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','REPARTIDOR') RETURNING id`,
    [`ro_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`Cat_${tag}`]
  );
  const { rows: [producto] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES($1,'M','d',$2,100,$3) RETURNING id`,
    [`Prod_${tag}`, cat.id, stock]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, repartidor_id, solicitud_hash)
     VALUES($1,$2,100,$3,$4,$5) RETURNING *`,
    [cliente.id, crypto.randomUUID(), estado, repartidor.id, solicitudHash]
  );
  await pool.query(
    `INSERT INTO pedido_detalle(pedido_id, producto_id, nombre_producto, cantidad, precio_unitario_centimos, subtotal_centimos)
     VALUES($1,$2,'X',1,100,100)`,
    [pedido.id, producto.id]
  );
  await pool.query(
    `INSERT INTO direcciones(pedido_id, departamento, provincia, distrito, direccion, telefono)
     VALUES($1,'Junín','Huancayo','El Tambo','Calle 1','999999999')`,
    [pedido.id]
  );
  await pool.query(
    `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
     VALUES($1,$2,$3,$4,'SALIDA_VENTA',1)`,
    [crypto.randomUUID(), producto.id, pedido.id, cliente.id]
  );
  let pago = null;
  if (conPago) {
    const { rows: [p] } = await pool.query(
      `INSERT INTO pagos(
         uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
         numero_operacion_banco, fecha_confirmacion
       ) VALUES ($1,$2,100,'PEN','QR',$3,$4,$5,$6) RETURNING *`,
      [
        crypto.randomUUID(),
        pedido.id,
        modalidad,
        pagoEstado,
        pagoEstado === 'CONFIRMADO' ? `OP-${tag}` : null,
        pagoEstado === 'CONFIRMADO' ? new Date().toISOString() : null
      ]
    );
    pago = p;
  }
  return { cliente, repartidor, otro, producto, pedido, pago };
}

function evidencia(extra = {}) {
  return {
    uuidOperacion: crypto.randomUUID(),
    // Referencia lógica no remota (compat); rutas entregas/{pedidoId}/… se validan en medios.
    fotoReferencia: 'evidencia-local-demo.jpg',
    latitud: -12.0464,
    longitud: -77.0428,
    fechaEntrega: new Date().toISOString(),
    ...extra
  };
}

test('reglas entrega: evidencia GPS y pago', () => {
  assert.equal(validarEvidencia({ fotoReferencia: '', latitud: 0, longitud: 0 }).ok, false);
  assert.equal(validarEvidencia({ fotoReferencia: 'a', latitud: 100, longitud: 0 }).codigo, 'GPS_INVALIDO');
  assert.equal(validarEvidencia({ fotoReferencia: 'a', latitud: 0, longitud: 0 }).ok, true);

  const bancario = {
    modalidad: 'ONLINE',
    estado: 'CONFIRMADO',
    numero_operacion_banco: 'OP-1',
    fecha_confirmacion: new Date().toISOString()
  };
  assert.equal(puedeMarcarEntregadoSegunPago(bancario).ok, true);
  assert.equal(
    puedeMarcarEntregadoSegunPago({ modalidad: 'ONLINE', estado: 'PENDIENTE' }).ok,
    false
  );
  assert.equal(
    puedeMarcarEntregadoSegunPago({ modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' }).ok,
    false
  );
  assert.equal(
    puedeMarcarEntregadoSegunPago(
      { modalidad: 'ONLINE', estado: 'CONFIRMADO' }, // sin datos banco
      { solicitud_hash: 'x' }
    ).ok,
    false
  );
  assert.equal(
    puedeMarcarEntregadoSegunPago(null, { solicitud_hash: null }).ok,
    true
  );
  assert.equal(
    puedeMarcarEntregadoSegunPago(null, { solicitud_hash: 'hash-v2' }).ok,
    false
  );
});

test('PostgreSQL entregas: permisos, evidencia, estado e inventario intacto', { skip }, async () => {
  await conEsquema(async (pool, entregas) => {
    const s = await semilla(pool);
    const { rows: [stockAntes] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.producto.id
    ]);
    const { rows: [salidasAntes] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [s.pedido.id]
    );

    const lista = await ejecutar(entregas.listar, { id: s.repartidor.id, rol: 'REPARTIDOR' });
    assert.equal(lista.code, 200);
    assert.equal(lista.body.pedidos.length, 1);

    const ajeno = await ejecutar(
      entregas.registrar,
      { id: s.otro.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia()
    );
    assert.equal(ajeno.code, 403);

    const sinFoto = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia({ fotoReferencia: '   ' })
    );
    assert.equal(sinFoto.code, 400);
    assert.equal(sinFoto.body.codigo, 'FOTO_REQUERIDA');

    const gps = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia({ latitud: 91 })
    );
    assert.equal(gps.code, 400);
    assert.equal(gps.body.codigo, 'GPS_INVALIDO');

    await pool.query(`UPDATE pedidos SET estado='LISTO' WHERE id=$1`, [s.pedido.id]);
    const malEstado = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia()
    );
    assert.equal(malEstado.code, 409);
    assert.equal(malEstado.body.codigo, 'PEDIDO_NO_EN_CAMINO');
    await pool.query(`UPDATE pedidos SET estado='EN_CAMINO' WHERE id=$1`, [s.pedido.id]);

    const ok = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia()
    );
    assert.equal(ok.code, 201);
    assert.equal(ok.body.pedido.estado, 'ENTREGADO');
    assert.equal(ok.body.entrega.fotoReferencia, 'evidencia-local-demo.jpg');
    assert.ok(!Buffer.isBuffer(ok.body.entrega.fotoReferencia));

    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      s.producto.id
    ]);
    assert.equal(Number(stock.stock), Number(stockAntes.stock));
    const { rows: [salidas] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [s.pedido.id]
    );
    assert.equal(salidas.n, salidasAntes.n);

    const cerrada = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      evidencia()
    );
    assert.equal(cerrada.code, 409);
    assert.equal(cerrada.body.codigo, 'ENTREGA_CERRADA');
  });
});

test('PostgreSQL entregas: pagos pendientes, legado y nuevo sin pagar', { skip }, async () => {
  await conEsquema(async (pool, entregas) => {
    const contra = await semilla(pool, {
      modalidad: 'CONTRAENTREGA',
      pagoEstado: 'PENDIENTE'
    });
    const bloqContra = await ejecutar(
      entregas.registrar,
      { id: contra.repartidor.id, rol: 'REPARTIDOR' },
      { id: contra.pedido.id },
      evidencia()
    );
    assert.equal(bloqContra.code, 409);
    assert.equal(bloqContra.body.codigo, 'ENTREGA_REQUIERE_PAGO');
    assert.equal(
      (await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [contra.pedido.id])).rows[0]
        .estado,
      'EN_CAMINO'
    );

    const online = await semilla(pool, {
      modalidad: 'ONLINE',
      pagoEstado: 'PENDIENTE'
    });
    const bloqOnline = await ejecutar(
      entregas.registrar,
      { id: online.repartidor.id, rol: 'REPARTIDOR' },
      { id: online.pedido.id },
      evidencia()
    );
    assert.equal(bloqOnline.code, 409);
    assert.equal(bloqOnline.body.codigo, 'ENTREGA_REQUIERE_PAGO');

    // Nuevo sin fila pagos pero con solicitud_hash → no legado
    const nuevoSinPago = await semilla(pool, {
      conPago: false,
      solicitudHash: 'contrato-v2'
    });
    const bloqNuevo = await ejecutar(
      entregas.registrar,
      { id: nuevoSinPago.repartidor.id, rol: 'REPARTIDOR' },
      { id: nuevoSinPago.pedido.id },
      evidencia()
    );
    assert.equal(bloqNuevo.code, 409);
    assert.equal(bloqNuevo.body.codigo, 'ENTREGA_REQUIERE_PAGO');

    // Histórico explícito: sin pagos y sin solicitud_hash
    const legado = await semilla(pool, { conPago: false, solicitudHash: null });
    const hist = await ejecutar(
      entregas.registrar,
      { id: legado.repartidor.id, rol: 'REPARTIDOR' },
      { id: legado.pedido.id },
      evidencia()
    );
    assert.equal(hist.code, 201);
    assert.equal(hist.body.compatibilidadHistorica, true);
  });
});

test('PostgreSQL entregas: idempotencia y concurrencia con pago confirmado', { skip }, async () => {
  await conEsquema(async (pool, entregas) => {
    const s = await semilla(pool, {
      modalidad: 'CONTRAENTREGA',
      pagoEstado: 'CONFIRMADO'
    });
    const uuid = crypto.randomUUID();
    const body = evidencia({ uuidOperacion: uuid });
    const primero = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      body
    );
    assert.equal(primero.code, 201);
    const reintento = await ejecutar(
      entregas.registrar,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { id: s.pedido.id },
      body
    );
    assert.equal(reintento.code, 200);
    assert.equal(reintento.body.idempotente, true);
    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM entregas WHERE pedido_id=$1`,
      [s.pedido.id]
    );
    assert.equal(n.n, 1);

    const conc = await semilla(pool, { pagoEstado: 'CONFIRMADO' });
    const codigos = (
      await Promise.all([
        ejecutar(
          entregas.registrar,
          { id: conc.repartidor.id, rol: 'REPARTIDOR' },
          { id: conc.pedido.id },
          evidencia()
        ),
        ejecutar(
          entregas.registrar,
          { id: conc.repartidor.id, rol: 'REPARTIDOR' },
          { id: conc.pedido.id },
          evidencia()
        )
      ])
    ).map((r) => r.code);
    assert.deepEqual(codigos.sort(), [201, 409]);
    const { rows: [nConc] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM entregas WHERE pedido_id=$1`,
      [conc.pedido.id]
    );
    assert.equal(nConc.n, 1);
    const { rows: [ped] } = await pool.query(`SELECT estado FROM pedidos WHERE id=$1`, [
      conc.pedido.id
    ]);
    assert.equal(ped.estado, 'ENTREGADO');
  });
});
