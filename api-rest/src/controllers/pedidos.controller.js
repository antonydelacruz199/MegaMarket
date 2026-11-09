const crypto = require('crypto');
const pool = require('../database');
const {agruparItems, huella} = require('../reglas');

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
    if(req.body.contratoVersion !== 2) return res.status(426).json({error:'Actualiza la aplicación para comprar con reservas'});
    let items;
    try { items=agruparItems(req.body.items,true); } catch(e){return res.status(400).json({error:e.message});}
    const direccionCanonica=Object.fromEntries(['departamento','provincia','distrito','direccion','telefono'].map(k=>[k,String(direccion?.[k] || '').trim()]));
    const solicitudHash = huella({items,direccion:direccionCanonica});

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
    await cliente.query('SELECT id FROM usuarios WHERE id=$1 FOR UPDATE',[req.usuario.id]);

    const existente = await cliente.query(
      `SELECT id, cliente_id, solicitud_hash FROM pedidos WHERE client_uuid = $1 LIMIT 1`,
      [clientUuid]
    );
    if (existente.rows[0]) {
      if(existente.rows[0].cliente_id !== req.usuario.id){
        await cliente.query('ROLLBACK');return res.status(409).json({error:'Pedido no disponible'});
      }
      if(existente.rows[0].solicitud_hash === null){
        const historico=await cargarPedidoCompleto(cliente,existente.rows[0].id);
        const lineasHistoricas=historico.detalles.map(d=>({productoId:d.producto_id,cantidad:Number(d.cantidad)})).sort((a,b)=>a.productoId.localeCompare(b.productoId));
        const direccionHistorica=Object.fromEntries(Object.keys(direccionCanonica).map(k=>[k,String(historico.direccion?.[k] || '').trim()]));
        if(huella({items:lineasHistoricas,direccion:direccionHistorica})!==huella({items:items.map(i=>({productoId:i.productoId,cantidad:i.cantidad})),direccion:direccionCanonica})){
          await cliente.query('ROLLBACK');return res.status(409).json({error:'Contenido diferente del pedido histórico'});
        }
      } else if(existente.rows[0].solicitud_hash !== solicitudHash){
        await cliente.query('ROLLBACK');return res.status(409).json({error:'Identificador de pedido ya utilizado con otro propietario o contenido'});
      }
      const completo = await cargarPedidoCompleto(cliente, existente.rows[0].id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        ...completo
      });
    }

    await cliente.query('INSERT INTO carritos(id,usuario_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[clientUuid,req.usuario.id]);
    const {rows:[carrito]}=await cliente.query('SELECT * FROM carritos WHERE id=$1 FOR UPDATE',[clientUuid]);
    if(carrito.usuario_id!==req.usuario.id || carrito.cerrado){await cliente.query('ROLLBACK');return res.status(409).json({error:'Carrito no disponible'});}
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
      const {rows:[reservado]}=await cliente.query('SELECT COALESCE(SUM(cantidad),0) total FROM reservas_carrito WHERE producto_id=$1 AND carrito_id<>$2',[productoId,clientUuid]);
      if (Number(producto.stock)-Number(reservado.total) < cantidad) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({ error: `Stock insuficiente para ${producto.nombre}` });
      }

      const unitario = precioVigente(producto);
      if (!Number.isFinite(unitario) || unitario <= 0) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: `Precio inválido para ${producto.nombre}` });
      }
      if(unitario !== item.precioEsperadoCentimos){await cliente.query('ROLLBACK');return res.status(409).json({error:'El precio cambió. Revisa el catálogo y acepta el nuevo total.',codigo:'PRECIO_CAMBIADO'});}
      const subtotal = unitario * cantidad;
      totalCentimos += subtotal;
      if(!Number.isSafeInteger(totalCentimos)){await cliente.query('ROLLBACK');return res.status(400).json({error:'Importe fuera del rango permitido'});}
      lineas.push({
        producto,
        cantidad,
        precioUnitarioCentimos: unitario,
        subtotalCentimos: subtotal
      });
    }

    const pedidoInsert = await cliente.query(
      `INSERT INTO pedidos (
         cliente_id, client_uuid, total_centimos, estado, solicitud_hash
       ) VALUES (
         $1, $2, $3, 'CONFIRMADO', $4
       )
       RETURNING *`,
      [req.usuario.id, clientUuid, totalCentimos, solicitudHash]
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

    await cliente.query('DELETE FROM reservas_carrito WHERE carrito_id=$1',[clientUuid]);
    await cliente.query('UPDATE carritos SET cerrado=true WHERE id=$1',[clientUuid]);
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
    console.error('Error al crear pedido:', error.message);
    return res.status(500).json({ error: 'No se pudo crear el pedido' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  crear
};
