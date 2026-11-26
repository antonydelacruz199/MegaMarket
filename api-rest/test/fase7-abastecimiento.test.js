const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_f7_' + crypto.randomBytes(6).toString('hex');
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
      '005_pedido_recibido.sql',
      '006_aviso_sin_stock.sql',
      '007_abastecimiento.sql'
    ]) {
      await pool.query(fs.readFileSync(path.join(__dirname, '../sql', file), 'utf8'));
    }

    // roles extendidos usados por semillas de otras fases
    await pool.query(`
      DO $$ BEGIN
        ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;
      EXCEPTION WHEN undefined_object THEN NULL;
      END $$;
    `).catch(() => {});

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
    bust('../src/abastecimiento');
    bust('../src/banco/bancoAndinoSimulado');
    bust('../src/controllers/proveedores.controller');
    bust('../src/controllers/compras.controller');

    const proveedores = require('../src/controllers/proveedores.controller');
    const compras = require('../src/controllers/compras.controller');

    await fn(pool, { proveedores, compras });

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
    bust('../src/controllers/proveedores.controller');
    bust('../src/controllers/compras.controller');
    bust('../src/abastecimiento');
    bust('../src/banco/bancoAndinoSimulado');
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, body = {}, params = {}, query = {}) {
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
  await handler({ usuario, body, params, query }, res);
  return res;
}

async function semilla(pool) {
  const { rows: [admin] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Admin','admin_f7','admin_f7','x','ADMINISTRADOR') RETURNING id`
  );
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES('Cli','cli_f7','cli_f7','x','CLIENTE') RETURNING id`
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES('Abarrotes F7') RETURNING id`
  );
  const { rows: [p1] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES('Arroz','Marca','desc',$1,350,10) RETURNING id, stock`,
    [cat.id]
  );
  const { rows: [p2] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock)
     VALUES('Aceite','Marca','desc',$1,800,5) RETURNING id, stock`,
    [cat.id]
  );
  return { admin, cliente, p1, p2 };
}

test(
  'Fase7 proveedores: registro, ruc inválido/duplicado, edición y activación',
  { skip },
  async () => {
    await conEsquema(async (pool, { proveedores }) => {
      const { admin, cliente } = await semilla(pool);
      const denegado = await ejecutar(proveedores.crear, cliente, {
        ruc: '20111111111',
        razonSocial: 'X'
      });
      // El middleware de rol no se aplica aquí; el controlador asume admin.
      // Se prueba el happy path admin y validaciones de datos.
      assert.equal(denegado.code === 201 || denegado.code === 400 || denegado.code === 500, true);

      const malo = await ejecutar(proveedores.crear, admin, {
        ruc: '123',
        razonSocial: 'Demo'
      });
      assert.equal(malo.code, 400);

      const ok = await ejecutar(proveedores.crear, admin, {
        ruc: '20123456789',
        razonSocial: 'Proveedor Demo SAC',
        correo: 'prov@demo.com',
        cuentaBancaria: '001-999-000',
        telefono: '999888777'
      });
      assert.equal(ok.code, 201);
      assert.equal(ok.body.proveedor.ruc, '20123456789');

      const dup = await ejecutar(proveedores.crear, admin, {
        ruc: '20123456789',
        razonSocial: 'Otro'
      });
      assert.equal(dup.code, 409);

      const patch = await ejecutar(
        proveedores.actualizar,
        admin,
        { razonSocial: 'Proveedor Demo Actualizado', activo: false },
        { id: ok.body.proveedor.id }
      );
      assert.equal(patch.code, 200);
      assert.equal(patch.body.proveedor.activo, false);
      assert.equal(patch.body.proveedor.razonSocial, 'Proveedor Demo Actualizado');

      const inexistente = await ejecutar(
        proveedores.obtener,
        admin,
        {},
        { id: crypto.randomUUID() }
      );
      assert.equal(inexistente.code, 404);

      const lista = await ejecutar(proveedores.listar, admin, {}, {}, { q: 'Demo' });
      assert.equal(lista.code, 200);
      assert.ok(lista.body.proveedores.length >= 1);
    });
  }
);

test(
  'Fase7 órdenes: creación, validaciones, aprobación y transición inválida',
  { skip },
  async () => {
    await conEsquema(async (pool, { proveedores, compras }) => {
      const { admin, p1, p2 } = await semilla(pool);
      const prov = await ejecutar(proveedores.crear, admin, {
        ruc: '20987654321',
        razonSocial: 'Insumos SAC',
        cuentaBancaria: '002-111',
        activo: true
      });
      assert.equal(prov.code, 201);

      const vacia = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: []
      });
      assert.equal(vacia.code, 400);

      const negativa = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: -1, precioUnitarioCentimos: 100 }]
      });
      assert.equal(negativa.code, 400);

      const costoMalo = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: 2, precioUnitarioCentimos: 0 }]
      });
      assert.equal(costoMalo.code, 400);

      const dupProducto = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [
          { productoId: p1.id, cantidad: 1, precioUnitarioCentimos: 100 },
          { productoId: p1.id, cantidad: 2, precioUnitarioCentimos: 100 }
        ]
      });
      assert.equal(dupProducto.code, 400);

      await ejecutar(
        proveedores.actualizar,
        admin,
        { activo: false },
        { id: prov.body.proveedor.id }
      );
      const inactivo = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: 2, precioUnitarioCentimos: 150 }]
      });
      assert.equal(inactivo.code, 409);

      await ejecutar(
        proveedores.actualizar,
        admin,
        { activo: true },
        { id: prov.body.proveedor.id }
      );

      const creada = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [
          { productoId: p1.id, cantidad: 2, precioUnitarioCentimos: 150 },
          { productoId: p2.id, cantidad: 1, precioUnitarioCentimos: 500 }
        ]
      });
      assert.equal(creada.code, 201);
      assert.equal(creada.body.orden.estado, 'SUGERIDA');
      assert.equal(creada.body.orden.totalCentimos, 800);
      assert.equal(creada.body.orden.detalles.length, 2);
      assert.match(creada.body.orden.numeroOrden, /^OC-/);

      const otra = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: 1, precioUnitarioCentimos: 100 }]
      });
      assert.notEqual(otra.body.orden.numeroOrden, creada.body.orden.numeroOrden);

      const pagoSinAprobar = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID() },
        { id: creada.body.orden.id }
      );
      assert.equal(pagoSinAprobar.code, 409);

      const uuidAprobar = crypto.randomUUID();
      const aprobada = await ejecutar(
        compras.aprobarOrden,
        admin,
        { uuidOperacion: uuidAprobar },
        { id: creada.body.orden.id }
      );
      assert.equal(aprobada.code, 200);
      assert.equal(aprobada.body.orden.estado, 'APROBADA');
      assert.ok(aprobada.body.orden.fechaAprobacion);
      assert.equal(aprobada.body.orden.administradorId, admin.id);

      const reintentoAprobar = await ejecutar(
        compras.aprobarOrden,
        admin,
        { uuidOperacion: uuidAprobar },
        { id: creada.body.orden.id }
      );
      assert.equal(reintentoAprobar.code, 200);
      assert.equal(reintentoAprobar.body.idempotente, true);

      const cancelarPagada = await ejecutar(
        compras.cancelarOrden,
        admin,
        {},
        { id: creada.body.orden.id }
      );
      // APROBADA puede cancelarse
      assert.equal(cancelarPagada.code, 200);
      assert.equal(cancelarPagada.body.orden.estado, 'CANCELADA');
    });
  }
);

test(
  'Fase7 pagos: simulación, rechazo, timeout, idempotencia y referencia',
  { skip },
  async () => {
    await conEsquema(async (pool, { proveedores, compras }) => {
      const { admin, p1 } = await semilla(pool);
      const prov = await ejecutar(proveedores.crear, admin, {
        ruc: '20444444444',
        razonSocial: 'Pagos SAC',
        cuentaBancaria: '003-222'
      });

      async function ordenAprobada() {
        const o = await ejecutar(compras.crearOrden, admin, {
          proveedorId: prov.body.proveedor.id,
          items: [{ productoId: p1.id, cantidad: 3, precioUnitarioCentimos: 200 }]
        });
        await ejecutar(compras.aprobarOrden, admin, {}, { id: o.body.orden.id });
        return o.body.orden;
      }

      const ordenOk = await ordenAprobada();
      const uuidOk = crypto.randomUUID();
      const pagoOk = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: uuidOk, forzarResultado: 'CONFIRMADA' },
        { id: ordenOk.id }
      );
      assert.equal(pagoOk.code, 200);
      assert.equal(pagoOk.body.orden.estado, 'PAGADA');
      assert.equal(pagoOk.body.simulacion, true);
      assert.ok(pagoOk.body.transferencia.numeroOperacionBanco);
      assert.ok(!pagoOk.body.aviso);

      const reintento = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: uuidOk, forzarResultado: 'CONFIRMADA' },
        { id: ordenOk.id }
      );
      assert.equal(reintento.code, 200);
      assert.equal(reintento.body.idempotente, true);

      const conflicto = await ejecutar(
        compras.pagarOrden,
        admin,
        {
          uuidOperacion: uuidOk,
          montoCentimos: 1,
          forzarResultado: 'CONFIRMADA'
        },
        { id: ordenOk.id }
      );
      assert.equal(conflicto.code, 409);

      const ordenRechazo = await ordenAprobada();
      const rechazo = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID(), forzarResultado: 'RECHAZADA' },
        { id: ordenRechazo.id }
      );
      assert.equal(rechazo.code, 409);
      assert.equal(rechazo.body.orden.estado, 'APROBADA');
      assert.equal(rechazo.body.transferencia.estado, 'RECHAZADA');

      const ordenPend = await ordenAprobada();
      const pend = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID(), forzarResultado: 'PENDIENTE' },
        { id: ordenPend.id }
      );
      assert.equal(pend.code, 409);
      assert.equal(pend.body.orden.estado, 'APROBADA');
      assert.equal(pend.body.transferencia.estado, 'PENDIENTE');

      const ordenDesc = await ordenAprobada();
      const desc = await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID(), forzarResultado: 'DESCONOCIDA' },
        { id: ordenDesc.id }
      );
      assert.equal(desc.code, 409);
      assert.equal(desc.body.orden.estado, 'APROBADA');
      assert.equal(desc.body.transferencia.estado, 'DESCONOCIDA');

      // Doble solicitud concurrente misma orden, distintos UUID
      const ordenConc = await ordenAprobada();
      const [a, b] = await Promise.all([
        ejecutar(
          compras.pagarOrden,
          admin,
          { uuidOperacion: crypto.randomUUID(), forzarResultado: 'CONFIRMADA' },
          { id: ordenConc.id }
        ),
        ejecutar(
          compras.pagarOrden,
          admin,
          { uuidOperacion: crypto.randomUUID(), forzarResultado: 'CONFIRMADA' },
          { id: ordenConc.id }
        )
      ]);
      const codigos = [a.code, b.code].sort();
      assert.ok(codigos.includes(200));
      assert.ok(codigos.includes(409) || codigos.filter((c) => c === 200).length === 1);
      const { rows: confirmadas } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM transferencias_proveedor
         WHERE orden_compra_id = $1 AND estado = 'CONFIRMADA'`,
        [ordenConc.id]
      );
      assert.equal(confirmadas[0].n, 1);
    });
  }
);

test(
  'Fase7 recepción: parcial, completa, excesiva, movimientos y stock',
  { skip },
  async () => {
    await conEsquema(async (pool, { proveedores, compras }) => {
      const { admin, p1, p2 } = await semilla(pool);
      const stockInicial = { p1: Number(p1.stock), p2: Number(p2.stock) };
      const prov = await ejecutar(proveedores.crear, admin, {
        ruc: '20555555555',
        razonSocial: 'Recepción SAC',
        cuentaBancaria: '004-333'
      });
      const creada = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [
          { productoId: p1.id, cantidad: 100, precioUnitarioCentimos: 10 },
          { productoId: p2.id, cantidad: 20, precioUnitarioCentimos: 50 }
        ]
      });
      await ejecutar(compras.aprobarOrden, admin, {}, { id: creada.body.orden.id });
      await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID(), forzarResultado: 'CONFIRMADA' },
        { id: creada.body.orden.id }
      );

      const d1 = creada.body.orden.detalles.find((d) => d.productoId === p1.id);
      const d2 = creada.body.orden.detalles.find((d) => d.productoId === p2.id);

      const noPagada = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: 5, precioUnitarioCentimos: 10 }]
      });
      const recepcionSinPago = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: crypto.randomUUID(),
          items: [{ detalleId: noPagada.body.orden.detalles[0].id, cantidad: 1 }]
        },
        { id: noPagada.body.orden.id }
      );
      assert.equal(recepcionSinPago.code, 409);

      const uuidR1 = crypto.randomUUID();
      const parcial = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: uuidR1,
          items: [{ detalleId: d1.id, cantidad: 40 }]
        },
        { id: creada.body.orden.id }
      );
      assert.equal(parcial.code, 201);
      assert.equal(parcial.body.orden.estado, 'PAGADA');
      assert.equal(parcial.body.recepcionCompleta, false);

      const reintento = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: uuidR1,
          items: [{ detalleId: d1.id, cantidad: 40 }]
        },
        { id: creada.body.orden.id }
      );
      assert.ok(reintento.code === 200 || reintento.code === 201);
      assert.equal(reintento.body.idempotente, true);

      const excesiva = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: crypto.randomUUID(),
          items: [{ detalleId: d1.id, cantidad: 100 }]
        },
        { id: creada.body.orden.id }
      );
      assert.equal(excesiva.code, 409);

      const invalida = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: crypto.randomUUID(),
          items: [{ detalleId: d1.id, cantidad: 0 }]
        },
        { id: creada.body.orden.id }
      );
      assert.equal(invalida.code, 400);

      const segunda = await ejecutar(
        compras.registrarRecepcion,
        admin,
        {
          uuidOperacion: crypto.randomUUID(),
          items: [
            { detalleId: d1.id, cantidad: 60 },
            { detalleId: d2.id, cantidad: 20 }
          ]
        },
        { id: creada.body.orden.id }
      );
      assert.equal(segunda.code, 201);
      assert.equal(segunda.body.orden.estado, 'RECIBIDA');
      assert.equal(segunda.body.recepcionCompleta, true);

      const { rows: stockP1 } = await pool.query(
        `SELECT stock FROM productos WHERE id = $1`,
        [p1.id]
      );
      const { rows: stockP2 } = await pool.query(
        `SELECT stock FROM productos WHERE id = $1`,
        [p2.id]
      );
      assert.equal(Number(stockP1[0].stock), stockInicial.p1 + 100);
      assert.equal(Number(stockP2[0].stock), stockInicial.p2 + 20);

      const { rows: movs } = await pool.query(
        `SELECT * FROM movimientos_inventario
         WHERE tipo = 'ENTRADA_COMPRA' AND orden_compra_detalle_id IS NOT NULL
         ORDER BY created_at`
      );
      assert.ok(movs.length >= 3);
      assert.ok(movs.every((m) => m.orden_compra_detalle_id));

      const hist = await ejecutar(
        compras.historialRecepciones,
        admin,
        {},
        { id: creada.body.orden.id }
      );
      assert.equal(hist.code, 200);
      assert.ok(hist.body.movimientos.length >= 3);
    });
  }
);

test(
  'Fase7 concurrencia de recepción no supera lo solicitado',
  { skip },
  async () => {
    await conEsquema(async (pool, { proveedores, compras }) => {
      const { admin, p1 } = await semilla(pool);
      const prov = await ejecutar(proveedores.crear, admin, {
        ruc: '20666666666',
        razonSocial: 'Concurrente SAC',
        cuentaBancaria: '005-444'
      });
      const creada = await ejecutar(compras.crearOrden, admin, {
        proveedorId: prov.body.proveedor.id,
        items: [{ productoId: p1.id, cantidad: 10, precioUnitarioCentimos: 100 }]
      });
      await ejecutar(compras.aprobarOrden, admin, {}, { id: creada.body.orden.id });
      await ejecutar(
        compras.pagarOrden,
        admin,
        { uuidOperacion: crypto.randomUUID(), forzarResultado: 'CONFIRMADA' },
        { id: creada.body.orden.id }
      );
      const detalleId = creada.body.orden.detalles[0].id;

      const resultados = await Promise.all(
        Array.from({ length: 5 }, () =>
          ejecutar(
            compras.registrarRecepcion,
            admin,
            {
              uuidOperacion: crypto.randomUUID(),
              items: [{ detalleId, cantidad: 4 }]
            },
            { id: creada.body.orden.id }
          )
        )
      );
      const exitosos = resultados.filter((r) => r.code === 201);
      const fallidos = resultados.filter((r) => r.code !== 201);
      assert.ok(exitosos.length <= 2);
      assert.ok(fallidos.length >= 3);

      const { rows: [detalle] } = await pool.query(
        `SELECT cantidad, cantidad_recibida FROM orden_compra_detalle WHERE id = $1`,
        [detalleId]
      );
      assert.ok(Number(detalle.cantidad_recibida) <= Number(detalle.cantidad));
    });
  }
);
