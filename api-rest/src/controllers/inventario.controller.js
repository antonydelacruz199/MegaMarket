const pool = require('../database');

const TIPOS_PERMITIDOS = new Set(['SALIDA_VENTA', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA']);
const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function deltaStock(tipo, cantidad) {
  if (tipo === 'AJUSTE_ENTRADA') return cantidad;
  return -cantidad;
}

async function registrarMovimiento(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = String(req.body.uuidOperacion || req.body.uuid_operacion || '').trim();
    const productoId = req.body.productoId || req.body.producto_id;
    const pedidoId = req.body.pedidoId || req.body.pedido_id || null;
    const tipo = String(req.body.tipo || '').trim();
    const cantidad = Number(req.body.cantidad);

    if (!uuidOperacion) {
      return res.status(400).json({ error: 'uuidOperacion es obligatorio' });
    }
    if (!UUID_RE.test(uuidOperacion)) {
      return res.status(400).json({ error: 'uuidOperacion debe ser un UUID válido' });
    }
    if (!productoId) {
      return res.status(400).json({ error: 'productoId es obligatorio' });
    }
    if (pedidoId !== null && pedidoId !== undefined && pedidoId !== '' && !UUID_RE.test(String(pedidoId))) {
      return res.status(400).json({ error: 'pedidoId debe ser un UUID válido' });
    }
    if (!TIPOS_PERMITIDOS.has(tipo)) {
      return res.status(400).json({
        error: 'tipo inválido. Use SALIDA_VENTA, AJUSTE_ENTRADA o AJUSTE_SALIDA'
      });
    }
    if (!Number.isInteger(cantidad) || cantidad <= 0) {
      return res.status(400).json({ error: 'cantidad debe ser un entero > 0' });
    }
    if (req.body.stock !== undefined || req.body.stockAbsoluto !== undefined) {
      return res.status(400).json({
        error: 'No se acepta stock absoluto. Envíe únicamente un delta (tipo + cantidad).'
      });
    }

    await cliente.query('BEGIN');

    const movimientoPrevio = await cliente.query(
      `SELECT id, uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad,
              version, created_at, updated_at
       FROM movimientos_inventario
       WHERE uuid_operacion = $1 AND deleted_at IS NULL
       LIMIT 1`,
      [uuidOperacion]
    );
    if (movimientoPrevio.rows[0]) {
      const producto = await cliente.query(
        `SELECT id, stock, version FROM productos WHERE id = $1 LIMIT 1`,
        [productoId]
      );
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        movimiento: movimientoPrevio.rows[0],
        producto: producto.rows[0] || null
      });
    }

    const opPrevia = await cliente.query(
      `SELECT codigo_http, respuesta
       FROM operaciones_idempotentes
       WHERE uuid_operacion = $1
       LIMIT 1`,
      [uuidOperacion]
    );
    if (opPrevia.rows[0]) {
      await cliente.query('COMMIT');
      return res
        .status(opPrevia.rows[0].codigo_http || 200)
        .json(opPrevia.rows[0].respuesta);
    }

    const productoResultado = await cliente.query(
      `SELECT id, stock, version
       FROM productos
       WHERE id = $1 AND deleted_at IS NULL
       FOR UPDATE`,
      [productoId]
    );
    const producto = productoResultado.rows[0];
    if (!producto) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Producto no encontrado' });
    }

    const stockActual = Number(producto.stock);
    const delta = deltaStock(tipo, cantidad);
    const stockFinal = stockActual + delta;
    if (stockFinal < 0) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'La salida dejaría stock negativo' });
    }

    // version/updated_at los incrementa el trigger
    const actualizado = await cliente.query(
      `UPDATE productos
       SET stock = $1
       WHERE id = $2
       RETURNING id, stock, version`,
      [stockFinal, productoId]
    );

    const movimiento = await cliente.query(
      `INSERT INTO movimientos_inventario (
         uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
       ) VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING id, uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad,
                 version, created_at, updated_at`,
      [
        uuidOperacion,
        productoId,
        pedidoId || null,
        req.usuario.id,
        tipo,
        cantidad
      ]
    );

    const respuesta = {
      idempotente: false,
      movimiento: movimiento.rows[0],
      producto: actualizado.rows[0],
      stockAnterior: stockActual,
      stockFinal
    };

    await cliente.query(
      `INSERT INTO operaciones_idempotentes (
         uuid_operacion,
         usuario_id,
         tipo_operacion,
         recurso,
         entidad_id,
         estado,
         codigo_http,
         respuesta
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb)
       ON CONFLICT (uuid_operacion) DO NOTHING`,
      [
        uuidOperacion,
        req.usuario.id,
        'CREAR_MOVIMIENTO_INVENTARIO',
        'MOVIMIENTO_INVENTARIO',
        movimiento.rows[0].id,
        'PROCESADA',
        201,
        JSON.stringify(respuesta)
      ]
    );

    await cliente.query('COMMIT');
    return res.status(201).json(respuesta);
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      try {
        const uuidOperacion = String(req.body.uuidOperacion || req.body.uuid_operacion || '').trim();
        const previo = await pool.query(
          `SELECT id, uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad,
                  version, created_at, updated_at
           FROM movimientos_inventario
           WHERE uuid_operacion = $1 AND deleted_at IS NULL
           LIMIT 1`,
          [uuidOperacion]
        );
        if (previo.rows[0]) {
          return res.status(200).json({
            idempotente: true,
            movimiento: previo.rows[0]
          });
        }
      } catch (_retryError) {
        // fall through
      }
    }
    console.error('Error en movimiento de inventario:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar el movimiento' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  registrarMovimiento
};
