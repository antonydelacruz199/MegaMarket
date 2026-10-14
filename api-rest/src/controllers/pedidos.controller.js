const crypto = require('crypto');
const pool = require('../database');

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function precioVigente(producto) {
  const precio = Number(producto.precio_centimos);
  const oferta = producto.precio_oferta_centimos;
  if (
    producto.es_oferta &&
    oferta !== null &&
    oferta !== undefined &&
    Number(oferta) > 0 &&
    Number(oferta) < precio
  ) {
    return Number(oferta);
  }
  return precio;
}

function validarDireccion(direccion) {
  if (!direccion || typeof direccion !== 'object') {
    return 'dirección es obligatoria';
  }
  if (!String(direccion.departamento || '').trim()) return 'departamento es obligatorio';
  if (!String(direccion.provincia || '').trim()) return 'provincia es obligatorio';
  if (!String(direccion.distrito || '').trim()) return 'distrito es obligatorio';
  if (!String(direccion.direccion || '').trim()) return 'direccion es obligatoria';
  const telefono = String(direccion.telefono || '');
  if ((telefono.match(/\d/g) || []).length < 6) return 'teléfono inválido';
  return null;
}

async function cargarPedidoCompleto(cliente, pedidoId) {
  const pedido = await cliente.query(
    `SELECT id, cliente_id, client_uuid, fecha, total_centimos, estado,
            version, created_at, updated_at
     FROM pedidos
     WHERE id = $1
     LIMIT 1`,
    [pedidoId]
  );
  const detalles = await cliente.query(
    `SELECT id, pedido_id, producto_id, nombre_producto,
            precio_unitario_centimos, cantidad, subtotal_centimos,
            version, created_at, updated_at
     FROM pedido_detalle
     WHERE pedido_id = $1 AND deleted_at IS NULL
     ORDER BY id`,
    [pedidoId]
  );
  const direccion = await cliente.query(
    `SELECT id, pedido_id, departamento, provincia, distrito, direccion, telefono,
            version, created_at, updated_at
     FROM direcciones
     WHERE pedido_id = $1 AND deleted_at IS NULL
     LIMIT 1`,
    [pedidoId]
  );
  return {
    pedido: pedido.rows[0],
    detalles: detalles.rows,
    direccion: direccion.rows[0] || null
  };
}

async function crear(req, res) {
  const cliente = await pool.connect();
  try {
    const clientUuid = String(req.body.clientUuid || req.body.client_uuid || '').trim();
    const direccion = req.body.direccion || req.body.dirección;
    const items = req.body.items || req.body.productos || [];

    if (!clientUuid) {
      return res.status(400).json({ error: 'clientUuid es obligatorio' });
    }
    if (!UUID_RE.test(clientUuid)) {
      return res.status(400).json({ error: 'clientUuid debe ser un UUID válido' });
    }
    const errorDireccion = validarDireccion(direccion);
    if (errorDireccion) {
      return res.status(400).json({ error: errorDireccion });
    }
    if (!Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: 'Debe incluir al menos un producto' });
    }

    await cliente.query('BEGIN');

    const existente = await cliente.query(
      `SELECT id FROM pedidos WHERE client_uuid = $1 LIMIT 1`,
      [clientUuid]
    );
    if (existente.rows[0]) {
      const completo = await cargarPedidoCompleto(cliente, existente.rows[0].id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        ...completo
      });
    }

    let totalCentimos = 0;
    const lineas = [];

    for (const item of items) {
      const productoId = item.productoId || item.producto_id;
      const cantidad = Number(item.cantidad);
      if (!productoId) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: 'productoId es obligatorio en cada ítem' });
      }
      if (!Number.isInteger(cantidad) || cantidad <= 0) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: 'cantidad debe ser un entero > 0' });
      }

      const productoResultado = await cliente.query(
        `SELECT id, nombre, precio_centimos, precio_oferta_centimos, es_oferta, stock
         FROM productos
         WHERE id = $1 AND deleted_at IS NULL AND activo = true
         FOR UPDATE`,
        [productoId]
      );
      const producto = productoResultado.rows[0];
      if (!producto) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: `Producto no disponible: ${productoId}` });
      }
      if (Number(producto.stock) < cantidad) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({ error: `Stock insuficiente para ${producto.nombre}` });
      }

      const unitario = precioVigente(producto);
      if (!Number.isFinite(unitario) || unitario <= 0) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: `Precio inválido para ${producto.nombre}` });
      }
      const subtotal = unitario * cantidad;
      totalCentimos += subtotal;
      lineas.push({
        producto,
        cantidad,
        precioUnitarioCentimos: unitario,
        subtotalCentimos: subtotal
      });
    }

    const pedidoInsert = await cliente.query(
      `INSERT INTO pedidos (
         cliente_id, client_uuid, total_centimos, estado
       ) VALUES (
         $1, $2, $3, 'CONFIRMADO'
       )
       RETURNING *`,
      [req.usuario.id, clientUuid, totalCentimos]
    );
    const pedido = pedidoInsert.rows[0];

    await cliente.query(
      `INSERT INTO direcciones (
         pedido_id, departamento, provincia, distrito, direccion, telefono
       ) VALUES ($1, $2, $3, $4, $5, $6)`,
      [
        pedido.id,
        String(direccion.departamento).trim(),
        String(direccion.provincia).trim(),
        String(direccion.distrito).trim(),
        String(direccion.direccion).trim(),
        String(direccion.telefono).trim()
      ]
    );

    for (const linea of lineas) {
      await cliente.query(
        `INSERT INTO pedido_detalle (
           pedido_id, producto_id, nombre_producto, cantidad,
           precio_unitario_centimos, subtotal_centimos
         ) VALUES ($1, $2, $3, $4, $5, $6)`,
        [
          pedido.id,
          linea.producto.id,
          linea.producto.nombre,
          linea.cantidad,
          linea.precioUnitarioCentimos,
          linea.subtotalCentimos
        ]
      );

      const nuevoStock = Number(linea.producto.stock) - linea.cantidad;
      // version/updated_at los incrementa el trigger
      await cliente.query(
        `UPDATE productos
         SET stock = $1
         WHERE id = $2`,
        [nuevoStock, linea.producto.id]
      );

      await cliente.query(
        `INSERT INTO movimientos_inventario (
           uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
         ) VALUES ($1, $2, $3, $4, 'SALIDA_VENTA', $5)`,
        [
          crypto.randomUUID(),
          linea.producto.id,
          pedido.id,
          req.usuario.id,
          linea.cantidad
        ]
      );
    }

    const completo = await cargarPedidoCompleto(cliente, pedido.id);
    await cliente.query('COMMIT');
    return res.status(201).json({
      idempotente: false,
      ...completo
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      try {
        const clientUuid = String(req.body.clientUuid || req.body.client_uuid || '').trim();
        const existente = await pool.query(
          `SELECT id FROM pedidos WHERE client_uuid = $1 LIMIT 1`,
          [clientUuid]
        );
        if (existente.rows[0]) {
          const cliente2 = await pool.connect();
          try {
            const completo = await cargarPedidoCompleto(cliente2, existente.rows[0].id);
            return res.status(200).json({ idempotente: true, ...completo });
          } finally {
            cliente2.release();
          }
        }
      } catch (_retryError) {
        // fall through
      }
    }
    console.error('Error al crear pedido:', error.message);
    return res.status(500).json({ error: 'No se pudo crear el pedido' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  crear
};
