const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_v3_' + crypto.randomBytes(6).toString('hex');
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
    delete require.cache[require.resolve('../src/controllers/pedidos.controller')];
    const pedidos = require('../src/controllers/pedidos.controller');

    await fn(pool, pedidos);

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
    delete require.cache[require.resolve('../src/controllers/pedidos.controller')];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, body) {
  const res = {
    code: 200,
    body: null,
    status(code) {
      this.code = code;
      return this;
    },
    json(body) {
      this.body = body;
      return this;
    }
  };
  await handler({ usuario, body }, res);
  return res;
}

async function semillaBasica(pool) {
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol,preferencia_sustitucion)
     VALUES('Ana','ana','ana','x','CLIENTE','ACEPTAR') RETURNING id`
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES('Abarrotes') RETURNING id`
  );
  const { rows: [producto] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES('Arroz','Marca','desc',$1,100,10) RETURNING id`,
    [cat.id]
  );
  const { rows: [franja] } = await pool.query(
    `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
     VALUES (CURRENT_DATE, '10:00', '12:00', 2, true) RETURNING id`
  );
  return { cliente, producto, franja };
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

test('PostgreSQL checkout v3 exitoso crea RECIBIDO, pago PENDIENTE y descuenta stock', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const { cliente, producto, franja } = await semillaBasica(pool);
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
      modalidadPago: 'ONLINE',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 2, precioEsperadoCentimos: 100 }]
    });
    assert.equal(res.code, 201);
    assert.equal(res.body.pedido.estado, 'RECIBIDO');
    assert.equal(res.body.pedido.franja_entrega_id, franja.id);
    assert.equal(res.body.pedido.preferencia_sustitucion, 'ACEPTAR');
    assert.equal(res.body.pago.estado, 'PENDIENTE');
    assert.equal(res.body.pago.modalidad, 'ONLINE');
    assert.equal(res.body.pago.numeroOperacionBanco, null);

    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      producto.id
    ]);
    assert.equal(Number(stock.stock), 8);
    const { rows: [mov] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM movimientos_inventario WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
      [res.body.pedido.id]
    );
    assert.equal(mov.n, 1);
  });
});

test('PostgreSQL checkout v2 sin regresión (sin franja ni pagos)', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const { cliente, producto } = await semillaBasica(pool);
    const cart = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart, cliente.id]);
    await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,1)`, [cart, producto.id]);
    const res = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 2,
      clientUuid: cart,
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    });
    assert.equal(res.code, 201);
    assert.equal(res.body.pedido.estado, 'RECIBIDO');
    assert.equal(res.body.pago, null);
    const { rows: pagos } = await pool.query(`SELECT id FROM pagos WHERE pedido_id=$1`, [
      res.body.pedido.id
    ]);
    assert.equal(pagos.length, 0);
  });
});

test('PostgreSQL v3 franja llena, modalidad inválida, precio y stock', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const { cliente, producto, franja } = await semillaBasica(pool);
    await pool.query(`UPDATE franjas_entrega SET capacidad=1 WHERE id=$1`, [franja.id]);
    const otro = crypto.randomUUID();
    await pool.query(
      `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, franja_entrega_id)
       VALUES($1,$2,100,'CONFIRMADO',$3)`,
      [cliente.id, otro, franja.id]
    );

    const cart = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart, cliente.id]);
    await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,1)`, [cart, producto.id]);

    const llena = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 3,
      clientUuid: cart,
      franjaEntregaId: franja.id,
      modalidadPago: 'CONTRAENTREGA',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    });
    assert.equal(llena.code, 409);
    assert.equal(llena.body.codigo, 'FRANJA_LLENA');

    const modalidad = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 3,
      clientUuid: crypto.randomUUID(),
      franjaEntregaId: franja.id,
      modalidadPago: 'EFECTIVO',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    });
    assert.equal(modalidad.code, 400);
    assert.equal(modalidad.body.codigo, 'MODALIDAD_INVALIDA');

    const { rows: [franja2] } = await pool.query(
      `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
       VALUES (CURRENT_DATE, '16:00', '18:00', 5, true) RETURNING id`
    );
    const cart2 = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart2, cliente.id]);
    await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,1)`, [cart2, producto.id]);
    const precio = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 3,
      clientUuid: cart2,
      franjaEntregaId: franja2.id,
      modalidadPago: 'ONLINE',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 999 }]
    });
    assert.equal(precio.code, 409);
    assert.equal(precio.body.codigo, 'PRECIO_CAMBIADO');

    await pool.query(`UPDATE productos SET stock=0 WHERE id=$1`, [producto.id]);
    const cart3 = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart3, cliente.id]);
    const stock = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 3,
      clientUuid: cart3,
      franjaEntregaId: franja2.id,
      modalidadPago: 'ONLINE',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    });
    assert.equal(stock.code, 409);
    assert.equal(stock.body.codigo, 'STOCK_INSUFICIENTE');
  });
});

test('PostgreSQL v3 idempotencia y concurrencia del último cupo', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const { cliente, producto, franja } = await semillaBasica(pool);
    await pool.query(`UPDATE franjas_entrega SET capacidad=1 WHERE id=$1`, [franja.id]);

    const cart = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart, cliente.id]);
    await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,1)`, [cart, producto.id]);
    const body = {
      contratoVersion: 3,
      clientUuid: cart,
      franjaEntregaId: franja.id,
      modalidadPago: 'CONTRAENTREGA',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    };
    const primero = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, body);
    assert.equal(primero.code, 201);
    const reintento = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, body);
    assert.equal(reintento.code, 200);
    assert.equal(reintento.body.idempotente, true);
    const { rows: [ocupados] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM pedidos WHERE franja_entrega_id=$1 AND estado<>'CANCELADO'`,
      [franja.id]
    );
    assert.equal(ocupados.n, 1);
    const { rows: [stock] } = await pool.query(`SELECT stock FROM productos WHERE id=$1`, [
      producto.id
    ]);
    assert.equal(Number(stock.stock), 9);

    const { rows: [cliente2] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES('B','b','b','x','CLIENTE') RETURNING id`
    );
    await pool.query(`UPDATE franjas_entrega SET capacidad=1 WHERE id=$1`, [franja.id]);
    // Liberar cupo cancelando el primero para probar concurrencia limpia en franja nueva
    const { rows: [franja2] } = await pool.query(
      `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
       VALUES (CURRENT_DATE, '18:00', '20:00', 1, true) RETURNING id`
    );
    await pool.query(`UPDATE productos SET stock=5 WHERE id=$1`, [producto.id]);

    async function intento(usuarioId) {
      const c = crypto.randomUUID();
      await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [c, usuarioId]);
      await pool.query(`INSERT INTO reservas_carrito VALUES($1,$2,1)`, [c, producto.id]);
      return ejecutar(pedidos.crear, { id: usuarioId, rol: 'CLIENTE' }, {
        contratoVersion: 3,
        clientUuid: c,
        franjaEntregaId: franja2.id,
        modalidadPago: 'ONLINE',
        direccion: direccion(),
        items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
      });
    }
    const codigos = (await Promise.all([intento(cliente.id), intento(cliente2.id)])).map(
      (r) => r.code
    );
    assert.deepEqual(codigos.sort(), [201, 409]);
    const { rows: [n] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM pedidos WHERE franja_entrega_id=$1 AND estado<>'CANCELADO'`,
      [franja2.id]
    );
    assert.equal(n.n, 1);
  });
});

test('PostgreSQL v3 rollback ante error no deja pago ni cupo', { skip }, async () => {
  await conEsquema(async (pool, pedidos) => {
    const { cliente, producto, franja } = await semillaBasica(pool);
    const cart = crypto.randomUUID();
    await pool.query(`INSERT INTO carritos(id,usuario_id) VALUES($1,$2)`, [cart, cliente.id]);
    // Sin reserva suficiente y stock 0 → falla tras bloquear franja
    await pool.query(`UPDATE productos SET stock=0 WHERE id=$1`, [producto.id]);
    const res = await ejecutar(pedidos.crear, { id: cliente.id, rol: 'CLIENTE' }, {
      contratoVersion: 3,
      clientUuid: cart,
      franjaEntregaId: franja.id,
      modalidadPago: 'ONLINE',
      direccion: direccion(),
      items: [{ productoId: producto.id, cantidad: 1, precioEsperadoCentimos: 100 }]
    });
    assert.equal(res.code, 409);
    const { rows: [pedidosN] } = await pool.query(
      `SELECT COUNT(*)::int AS n FROM pedidos WHERE franja_entrega_id=$1`,
      [franja.id]
    );
    assert.equal(pedidosN.n, 0);
    const { rows: [pagosN] } = await pool.query(`SELECT COUNT(*)::int AS n FROM pagos`);
    assert.equal(pagosN.n, 0);
  });
});
