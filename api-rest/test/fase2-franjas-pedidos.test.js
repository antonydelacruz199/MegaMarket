const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('path');
const crypto = require('node:crypto');
const { Pool } = require('pg');
const { asegurarCupoFranja } = require('../src/franjas');
const { evaluarTransicion } = require('../src/pedidosEstados');
const { restituirStockCancelacion } = require('../src/controllers/pedidos.controller');

const skip = !process.env.TEST_DATABASE_URL;

async function conEsquema(fn) {
  const schema = 'megamarket_f2_' + crypto.randomBytes(6).toString('hex');
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
    await fn(pool);
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

test(
  'PostgreSQL: cupo de franja serializa el último pedido concurrente',
  { skip },
  async () => {
    await conEsquema(async (pool) => {
      const { rows: [franja] } = await pool.query(
        `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
         VALUES (CURRENT_DATE, '10:00', '12:00', 1, true)
         RETURNING id`
      );
      const clientes = [];
      for (const nombre of ['A', 'B']) {
        const { rows: [u] } = await pool.query(
          `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
           VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
          [nombre]
        );
        clientes.push(u.id);
      }

      async function ocupar(clienteId) {
        const db = await pool.connect();
        try {
          await db.query('BEGIN');
          await asegurarCupoFranja(db, franja.id, { exigirHoyManana: true });
          await db.query(
            `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, franja_entrega_id)
             VALUES($1,$2,100,'CONFIRMADO',$3)`,
            [clienteId, crypto.randomUUID(), franja.id]
          );
          await db.query('COMMIT');
          return 201;
        } catch (error) {
          try {
            await db.query('ROLLBACK');
          } catch (_e) {
            // ignore
          }
          if (error.codigo === 'FRANJA_LLENA') return 409;
          throw error;
        } finally {
          db.release();
        }
      }

      const codigos = (await Promise.all(clientes.map(ocupar))).sort();
      assert.deepEqual(codigos, [201, 409]);
      const { rows: [c] } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM pedidos
         WHERE franja_entrega_id=$1 AND estado<>'CANCELADO'`,
        [franja.id]
      );
      assert.equal(c.n, 1);
    });
  }
);

test(
  'PostgreSQL: cancelar libera cupo; transición de estado no crea SALIDA_VENTA',
  { skip },
  async () => {
    await conEsquema(async (pool) => {
      const { rows: [franja] } = await pool.query(
        `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
         VALUES (CURRENT_DATE, '14:00', '16:00', 1, true) RETURNING id`
      );
      const { rows: [cliente] } = await pool.query(
        `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
         VALUES('C','c1','c1','x','CLIENTE') RETURNING id`
      );
      const { rows: [admin] } = await pool.query(
        `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
         VALUES('Ad','adm','adm','x','ADMINISTRADOR') RETURNING id`
      );
      const { rows: [pedido] } = await pool.query(
        `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, franja_entrega_id)
         VALUES($1,$2,500,'CONFIRMADO',$3) RETURNING id`,
        [cliente.id, crypto.randomUUID(), franja.id]
      );

      const db = await pool.connect();
      try {
        await db.query('BEGIN');
        await assert.rejects(
          () => asegurarCupoFranja(db, franja.id),
          (e) => e.codigo === 'FRANJA_LLENA'
        );
        await db.query('ROLLBACK');
      } finally {
        db.release();
      }

      // Contraentrega permite picking; no debe haber SALIDA_VENTA nueva
      await pool.query(
        `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado)
         VALUES($1,$2,500,'PEN','QR','CONTRAENTREGA','PENDIENTE')`,
        [crypto.randomUUID(), pedido.id]
      );

      const evalPick = evaluarTransicion({
        desde: 'CONFIRMADO',
        hacia: 'EN_PICKING',
        rol: 'ADMINISTRADOR',
        usuarioId: admin.id,
        pedido: {
          id: pedido.id,
          cliente_id: cliente.id,
          picker_id: null,
          repartidor_id: null
        },
        pago: { modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' },
        origenInternoBancario: false
      });
      assert.equal(evalPick.ok, true);

      await pool.query(`UPDATE pedidos SET estado='EN_PICKING' WHERE id=$1`, [pedido.id]);
      const { rows: [salidas] } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM movimientos_inventario
         WHERE pedido_id=$1 AND tipo='SALIDA_VENTA'`,
        [pedido.id]
      );
      assert.equal(salidas.n, 0);

      await pool.query(`UPDATE pedidos SET estado='CANCELADO' WHERE id=$1`, [pedido.id]);
      const db2 = await pool.connect();
      try {
        await db2.query('BEGIN');
        const cupo = await asegurarCupoFranja(db2, franja.id);
        assert.equal(cupo.cupoRestante, 1);
        await db2.query('ROLLBACK');
      } finally {
        db2.release();
      }
    });
  }
);

test(
  'PostgreSQL: pedidos históricos CONFIRMADO sin pago siguen siendo válidos',
  { skip },
  async () => {
    await conEsquema(async (pool) => {
      const { rows: [cliente] } = await pool.query(
        `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
         VALUES('H','h1','h1','x','CLIENTE') RETURNING id`
      );
      const { rows: [pedido] } = await pool.query(
        `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado)
         VALUES($1,$2,100,'CONFIRMADO') RETURNING id, estado`,
        [cliente.id, crypto.randomUUID()]
      );
      assert.equal(pedido.estado, 'CONFIRMADO');
      const { rows: pagos } = await pool.query(
        `SELECT id FROM pagos WHERE pedido_id=$1`,
        [pedido.id]
      );
      assert.equal(pagos.length, 0);
      const legado = evaluarTransicion({
        desde: 'CONFIRMADO',
        hacia: 'EN_PICKING',
        rol: 'ADMINISTRADOR',
        usuarioId: cliente.id,
        pedido: {
          id: pedido.id,
          cliente_id: cliente.id,
          picker_id: null,
          repartidor_id: null
        },
        pago: null,
        origenInternoBancario: false
      });
      assert.equal(legado.ok, true);
    });
  }
);

test(
  'PostgreSQL: restitución de cancelación es idempotente y no duplica stock',
  { skip },
  async () => {
    await conEsquema(async (pool) => {
      const { rows: [cliente] } = await pool.query(
        `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
         VALUES('R','r1','r1','x','CLIENTE') RETURNING id`
      );
      const { rows: [cat] } = await pool.query(
        `INSERT INTO categorias(nombre) VALUES('C') RETURNING id`
      );
      const { rows: [producto] } = await pool.query(
        `INSERT INTO productos(nombre,marca,categoria_id,precio_centimos,stock)
         VALUES('Leche','M',$1,200,10) RETURNING id, stock`,
        [cat.id]
      );
      const { rows: [pedido] } = await pool.query(
        `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado)
         VALUES($1,$2,400,'CONFIRMADO') RETURNING id`,
        [cliente.id, crypto.randomUUID()]
      );
      await pool.query(
        `INSERT INTO movimientos_inventario(uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad)
         VALUES($1,$2,$3,$4,'SALIDA_VENTA',2)`,
        [crypto.randomUUID(), producto.id, pedido.id, cliente.id]
      );
      await pool.query(`UPDATE productos SET stock = 8 WHERE id=$1`, [producto.id]);

      const db = await pool.connect();
      try {
        await db.query('BEGIN');
        await db.query(`SELECT id FROM pedidos WHERE id=$1 FOR UPDATE`, [pedido.id]);
        await restituirStockCancelacion(db, pedido.id, cliente.id);
        await restituirStockCancelacion(db, pedido.id, cliente.id);
        await db.query('COMMIT');
      } finally {
        db.release();
      }

      const { rows: [stock] } = await pool.query(
        `SELECT stock FROM productos WHERE id=$1`,
        [producto.id]
      );
      assert.equal(Number(stock.stock), 10);
      const { rows: [ajustes] } = await pool.query(
        `SELECT COUNT(*)::int AS n FROM movimientos_inventario
         WHERE pedido_id=$1 AND tipo='AJUSTE_ENTRADA'`,
        [pedido.id]
      );
      assert.equal(ajustes.n, 1);
    });
  }
);

test(
  'PostgreSQL: cancelar con pago CONFIRMADO se rechaza; cupo+insert franja son atómicos',
  { skip },
  async () => {
    await conEsquema(async (pool) => {
      const { rows: [cliente] } = await pool.query(
        `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
         VALUES('P','p1','p1','x','CLIENTE') RETURNING id`
      );
      const { rows: [pedido] } = await pool.query(
        `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado)
         VALUES($1,$2,100,'CONFIRMADO') RETURNING id`,
        [cliente.id, crypto.randomUUID()]
      );
      await pool.query(
        `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
                           numero_operacion_banco, fecha_confirmacion)
         VALUES($1,$2,100,'PEN','QR','ONLINE','CONFIRMADO','BANCO-1',now())`,
        [crypto.randomUUID(), pedido.id]
      );
      const bloqueo = evaluarTransicion({
        desde: 'CONFIRMADO',
        hacia: 'CANCELADO',
        rol: 'ADMINISTRADOR',
        usuarioId: cliente.id,
        pedido: { id: pedido.id, cliente_id: cliente.id, picker_id: null, repartidor_id: null },
        pago: {
          estado: 'CONFIRMADO',
          modalidad: 'ONLINE',
          numero_operacion_banco: 'BANCO-1',
          fecha_confirmacion: new Date().toISOString()
        },
        origenInternoBancario: false
      });
      assert.equal(bloqueo.codigo, 'CANCELACION_REQUIERE_DEVOLUCION');

      // Misma transacción: FOR UPDATE franja + INSERT pedido (patrón checkout v3).
      const { rows: [franja] } = await pool.query(
        `INSERT INTO franjas_entrega(fecha, hora_inicio, hora_fin, capacidad, activo)
         VALUES (CURRENT_DATE, '09:00', '10:00', 1, true) RETURNING id`
      );
      const db = await pool.connect();
      try {
        await db.query('BEGIN');
        await asegurarCupoFranja(db, franja.id);
        await db.query(
          `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, franja_entrega_id)
           VALUES($1,$2,50,'CONFIRMADO',$3)`,
          [cliente.id, crypto.randomUUID(), franja.id]
        );
        await db.query('COMMIT');
      } finally {
        db.release();
      }
      const db2 = await pool.connect();
      try {
        await db2.query('BEGIN');
        await assert.rejects(
          () => asegurarCupoFranja(db2, franja.id),
          (e) => e.codigo === 'FRANJA_LLENA'
        );
        await db2.query('ROLLBACK');
      } finally {
        db2.release();
      }
    });
  }
);
