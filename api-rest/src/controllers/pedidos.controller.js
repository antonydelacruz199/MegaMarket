const crypto = require('crypto');
const pool = require('../database');
const { agruparItems, huella } = require('../reglas');
const { asegurarCupoFranja } = require('../franjas');
const { evaluarTransicion, puedeVerPedido } = require('../pedidosEstados');
const { validarPreparacionCompleta } = require('../picking');

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

function mapearPedido(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    clienteId: fila.cliente_id,
    clientUuid: fila.client_uuid,
    fecha: fila.fecha,
    totalCentimos: Number(fila.total_centimos),
    estado: fila.estado,
    franjaEntregaId: fila.franja_entrega_id || null,
    pickerId: fila.picker_id || null,
    repartidorId: fila.repartidor_id || null,
    preferenciaSustitucion: fila.preferencia_sustitucion || null,
    solicitudHash: fila.solicitud_hash || null,
    version: Number(fila.version),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearDetalle(fila) {
  return {
    id: fila.id,
    pedidoId: fila.pedido_id,
    productoId: fila.producto_id,
    nombreProducto: fila.nombre_producto,
    precioUnitarioCentimos: Number(fila.precio_unitario_centimos),
    cantidad: Number(fila.cantidad),
    subtotalCentimos: Number(fila.subtotal_centimos),
    estadoPicking: fila.estado_picking || null,
    cantidadArmada: fila.cantidad_armada == null ? null : Number(fila.cantidad_armada),
    version: Number(fila.version),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearDireccion(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    pedidoId: fila.pedido_id,
    departamento: fila.departamento,
    provincia: fila.provincia,
    distrito: fila.distrito,
    direccion: fila.direccion,
    telefono: fila.telefono,
    version: Number(fila.version),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearPago(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    pedidoId: fila.pedido_id,
    montoCentimos: Number(fila.monto_centimos),
    moneda: fila.moneda,
    medioPago: fila.medio_pago,
    modalidad: fila.modalidad,
    estado: fila.estado,
    referenciaBanco: fila.referencia_banco || null,
    numeroOperacionBanco: fila.numero_operacion_banco || null,
    qrContenido: fila.qr_contenido || null,
    fechaExpiracionQr: fila.fecha_expiracion_qr || null,
    fechaConfirmacion: fila.fecha_confirmacion || null,
    simulacion: true,
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

async function cargarPago(cliente, pedidoId) {
  try {
    const pago = await cliente.query(
      `SELECT id, uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad,
              estado, referencia_banco, numero_operacion_banco, qr_contenido,
              fecha_expiracion_qr, fecha_confirmacion, created_at, updated_at
       FROM pagos
       WHERE pedido_id = $1
       ORDER BY created_at DESC
       LIMIT 1`,
      [pedidoId]
    );
    return pago.rows[0] || null;
  } catch (_error) {
    return null;
  }
}

async function cargarPedidoCompleto(cliente, pedidoId) {
  const pedido = await cliente.query(
    `SELECT * FROM pedidos WHERE id = $1 LIMIT 1`,
    [pedidoId]
  );
  const detalles = await cliente.query(
    `SELECT * FROM pedido_detalle
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
  const pagoFila = await cargarPago(cliente, pedidoId);
  return {
    pedido: mapearPedido(pedido.rows[0]),
    // Compatibilidad con clientes/tests que leen filas crudas del insert antiguo
    pedidoRaw: pedido.rows[0] || null,
    detalles: detalles.rows.map(mapearDetalle),
    detallesRaw: detalles.rows,
    direccion: mapearDireccion(direccion.rows[0] || null),
    direccionRaw: direccion.rows[0] || null,
    pago: mapearPago(pagoFila),
    pagoRaw: pagoFila
  };
}

/** Respuesta estable para POST (mantiene forma previa + campos nuevos). */
function respuestaPedidoLegacyCompat(completo) {
  return {
    pedido: completo.pedidoRaw,
    detalles: completo.detallesRaw,
    direccion: completo.direccionRaw,
    pago: completo.pago
  };
}

async function crear(req, res) {
  const cliente = await pool.connect();
  try {
    const clientUuid = String(req.body.clientUuid || req.body.client_uuid || '').trim();
    const direccion = req.body.direccion || req.body.dirección;
    const contrato = Number(req.body.contratoVersion);
    const esV3 = contrato === 3;

    if (contrato !== 2 && contrato !== 3) {
      return res.status(426).json({
        error: 'Actualiza la aplicación para comprar con reservas'
      });
    }

    let items;
    try {
      items = agruparItems(req.body.items, true);
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
    const direccionCanonica = Object.fromEntries(
      ['departamento', 'provincia', 'distrito', 'direccion', 'telefono'].map((k) => [
        k,
        String(direccion?.[k] || '').trim()
      ])
    );

    let franjaEntregaId = null;
    let modalidadPago = null;
    if (esV3) {
      franjaEntregaId = String(
        req.body.franjaEntregaId || req.body.franja_entrega_id || ''
      ).trim();
      modalidadPago = String(
        req.body.modalidadPago || req.body.modalidad_pago || ''
      )
        .trim()
        .toUpperCase();
      if (!franjaEntregaId || !UUID_RE.test(franjaEntregaId)) {
        return res.status(400).json({
          error: 'franjaEntregaId es obligatorio y debe ser un UUID válido',
          codigo: 'FRANJA_REQUERIDA'
        });
      }
      if (!['ONLINE', 'CONTRAENTREGA'].includes(modalidadPago)) {
        return res.status(400).json({
          error: 'modalidadPago debe ser ONLINE o CONTRAENTREGA',
          codigo: 'MODALIDAD_INVALIDA'
        });
      }
    }

    const solicitudHash = esV3
      ? huella({
          items,
          direccion: direccionCanonica,
          franjaEntregaId,
          modalidadPago
        })
      : huella({ items, direccion: direccionCanonica });

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

    let preferenciaSustitucion = 'RECHAZAR';
    if (esV3) {
      const { rows: [usuarioFila] } = await cliente.query(
        `SELECT id, preferencia_sustitucion
         FROM usuarios
         WHERE id = $1 AND deleted_at IS NULL
         FOR UPDATE`,
        [req.usuario.id]
      );
      if (!usuarioFila) {
        await cliente.query('ROLLBACK');
        return res.status(401).json({ error: 'Usuario no disponible' });
      }
      preferenciaSustitucion = usuarioFila.preferencia_sustitucion || 'RECHAZAR';
      const prefBody = req.body.preferenciaSustitucion || req.body.preferencia_sustitucion;
      if (prefBody !== undefined && prefBody !== null && String(prefBody).trim() !== '') {
        preferenciaSustitucion = String(prefBody).trim().toUpperCase();
        if (!['ACEPTAR', 'RECHAZAR'].includes(preferenciaSustitucion)) {
          await cliente.query('ROLLBACK');
          return res.status(400).json({
            error: 'preferenciaSustitucion debe ser ACEPTAR o RECHAZAR',
            codigo: 'PREFERENCIA_INVALIDA'
          });
        }
      }
    } else {
      await cliente.query('SELECT id FROM usuarios WHERE id=$1 FOR UPDATE', [req.usuario.id]);
    }

    const existente = await cliente.query(
      `SELECT id, cliente_id, solicitud_hash FROM pedidos WHERE client_uuid = $1 LIMIT 1`,
      [clientUuid]
    );
    if (existente.rows[0]) {
      if (existente.rows[0].cliente_id !== req.usuario.id) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({ error: 'Pedido no disponible' });
      }
      if (existente.rows[0].solicitud_hash === null) {
        const historico = await cargarPedidoCompleto(cliente, existente.rows[0].id);
        const lineasHistoricas = historico.detallesRaw
          .map((d) => ({ productoId: d.producto_id, cantidad: Number(d.cantidad) }))
          .sort((a, b) => a.productoId.localeCompare(b.productoId));
        const direccionHistorica = Object.fromEntries(
          Object.keys(direccionCanonica).map((k) => [
            k,
            String(historico.direccionRaw?.[k] || '').trim()
          ])
        );
        if (
          huella({ items: lineasHistoricas, direccion: direccionHistorica }) !==
          huella({
            items: items.map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
            direccion: direccionCanonica
          })
        ) {
          await cliente.query('ROLLBACK');
          return res.status(409).json({ error: 'Contenido diferente del pedido histórico' });
        }
      } else if (existente.rows[0].solicitud_hash !== solicitudHash) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: 'Identificador de pedido ya utilizado con otro propietario o contenido'
        });
      }
      const completo = await cargarPedidoCompleto(cliente, existente.rows[0].id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        ...respuestaPedidoLegacyCompat(completo)
      });
    }

    // v3: cupo de franja en la misma transacción (antes de consumir stock).
    if (esV3) {
      try {
        await asegurarCupoFranja(cliente, franjaEntregaId, { exigirHoyManana: true });
      } catch (errorFranja) {
        await cliente.query('ROLLBACK');
        return res.status(errorFranja.http || 409).json({
          error: errorFranja.message,
          codigo: errorFranja.codigo || 'FRANJA_ERROR'
        });
      }
    }

    await cliente.query(
      'INSERT INTO carritos(id,usuario_id) VALUES($1,$2) ON CONFLICT DO NOTHING',
      [clientUuid, req.usuario.id]
    );
    const { rows: [carrito] } = await cliente.query(
      'SELECT * FROM carritos WHERE id=$1 FOR UPDATE',
      [clientUuid]
    );
    if (carrito.usuario_id !== req.usuario.id || carrito.cerrado) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'Carrito no disponible' });
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
      const { rows: [reservado] } = await cliente.query(
        'SELECT COALESCE(SUM(cantidad),0) total FROM reservas_carrito WHERE producto_id=$1 AND carrito_id<>$2',
        [productoId, clientUuid]
      );
      if (Number(producto.stock) - Number(reservado.total) < cantidad) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: `Stock insuficiente para ${producto.nombre}`,
          codigo: 'STOCK_INSUFICIENTE'
        });
      }

      const unitario = precioVigente(producto);
      if (!Number.isFinite(unitario) || unitario <= 0) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: `Precio inválido para ${producto.nombre}` });
      }
      if (unitario !== item.precioEsperadoCentimos) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: 'El precio cambió. Revisa el catálogo y acepta el nuevo total.',
          codigo: 'PRECIO_CAMBIADO'
        });
      }
      const subtotal = unitario * cantidad;
      totalCentimos += subtotal;
      if (!Number.isSafeInteger(totalCentimos)) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: 'Importe fuera del rango permitido' });
      }
      lineas.push({
        producto,
        cantidad,
        precioUnitarioCentimos: unitario,
        subtotalCentimos: subtotal
      });
    }

    let pedido;
    if (esV3) {
      const pedidoInsert = await cliente.query(
        `INSERT INTO pedidos (
           cliente_id, client_uuid, total_centimos, estado, solicitud_hash,
           franja_entrega_id, preferencia_sustitucion
         ) VALUES (
           $1, $2, $3, 'CONFIRMADO', $4, $5, $6
         )
         RETURNING *`,
        [
          req.usuario.id,
          clientUuid,
          totalCentimos,
          solicitudHash,
          franjaEntregaId,
          preferenciaSustitucion
        ]
      );
      pedido = pedidoInsert.rows[0];
    } else {
      const pedidoInsert = await cliente.query(
        `INSERT INTO pedidos (
           cliente_id, client_uuid, total_centimos, estado, solicitud_hash
         ) VALUES (
           $1, $2, $3, 'CONFIRMADO', $4
         )
         RETURNING *`,
        [req.usuario.id, clientUuid, totalCentimos, solicitudHash]
      );
      pedido = pedidoInsert.rows[0];
    }

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

    if (esV3) {
      // Pago pendiente: nunca CONFIRMADO aquí (sin Banco Andino).
      await cliente.query(
        `INSERT INTO pagos (
           uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado
         ) VALUES ($1, $2, $3, 'PEN', 'QR', $4, 'PENDIENTE')`,
        [crypto.randomUUID(), pedido.id, totalCentimos, modalidadPago]
      );
    }

    await cliente.query('DELETE FROM reservas_carrito WHERE carrito_id=$1', [clientUuid]);
    await cliente.query('UPDATE carritos SET cerrado=true WHERE id=$1', [clientUuid]);
    const completo = await cargarPedidoCompleto(cliente, pedido.id);
    await cliente.query('COMMIT');
    return res.status(201).json({
      idempotente: false,
      ...respuestaPedidoLegacyCompat(completo)
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    // Preferencia/columnas de 003 ausentes en bases solo 001+002 (tests SQLite v2).
    if (error && /preferencia_sustitucion|franja_entrega|pagos/i.test(error.message)) {
      console.error('Error al crear pedido (esquema):', error.message);
    } else {
      console.error('Error al crear pedido:', error.message);
    }
    return res.status(500).json({ error: 'No se pudo crear el pedido' });
  } finally {
    cliente.release();
  }
}

function filtroVisibilidadSql(usuario, alias = 'p') {
  if (usuario.rol === 'ADMINISTRADOR') {
    return { sql: 'TRUE', params: [] };
  }
  if (usuario.rol === 'CLIENTE') {
    return { sql: `${alias}.cliente_id = $1`, params: [usuario.id] };
  }
  if (usuario.rol === 'PICKER') {
    return {
      sql: `(${alias}.picker_id IS NULL OR ${alias}.picker_id = $1)`,
      params: [usuario.id]
    };
  }
  if (usuario.rol === 'REPARTIDOR') {
    return {
      sql: `(${alias}.repartidor_id IS NULL OR ${alias}.repartidor_id = $1)`,
      params: [usuario.id]
    };
  }
  return { sql: 'FALSE', params: [] };
}

async function listar(req, res) {
  try {
    const filtro = filtroVisibilidadSql(req.usuario, 'p');
    const params = [...filtro.params];
    let sql = `
      SELECT p.*
      FROM pedidos p
      WHERE p.deleted_at IS NULL AND (${filtro.sql})
    `;
    if (req.query.estado) {
      params.push(String(req.query.estado));
      sql += ` AND p.estado = $${params.length}`;
    }
    sql += ' ORDER BY p.fecha DESC LIMIT 100';
    const resultado = await pool.query(sql, params);
    return res.status(200).json({
      pedidos: resultado.rows.map(mapearPedido)
    });
  } catch (error) {
    console.error('Error al listar pedidos:', error.message);
    return res.status(500).json({ error: 'No se pudieron obtener los pedidos' });
  }
}

async function obtenerPorId(req, res) {
  const cliente = await pool.connect();
  try {
    const completo = await cargarPedidoCompleto(cliente, req.params.id);
    if (!completo.pedidoRaw || completo.pedidoRaw.deleted_at) {
      return res.status(404).json({ error: 'Pedido no encontrado' });
    }
    if (!puedeVerPedido(req.usuario, completo.pedidoRaw)) {
      return res.status(403).json({ error: 'No tienes permiso para ver este pedido' });
    }
    return res.status(200).json({
      pedido: completo.pedido,
      detalles: completo.detalles,
      direccion: completo.direccion,
      pago: completo.pago
    });
  } catch (error) {
    console.error('Error al obtener pedido:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el pedido' });
  } finally {
    cliente.release();
  }
}

async function restituirStockCancelacion(cliente, pedidoId, usuarioId) {
  const salidas = await cliente.query(
    `SELECT producto_id, SUM(cantidad)::int AS cantidad
     FROM movimientos_inventario
     WHERE pedido_id = $1 AND tipo = 'SALIDA_VENTA' AND deleted_at IS NULL
     GROUP BY producto_id
     ORDER BY producto_id`,
    [pedidoId]
  );

  for (const fila of salidas.rows) {
    // Bloquear producto antes de decidir; revalidar para cancelaciones concurrentes/repetidas.
    await cliente.query(
      `SELECT id FROM productos WHERE id = $1 FOR UPDATE`,
      [fila.producto_id]
    );
    const { rows: [neto] } = await cliente.query(
      `SELECT
         COALESCE(SUM(cantidad) FILTER (WHERE tipo = 'SALIDA_VENTA'), 0)::int AS salidas,
         COALESCE(SUM(cantidad) FILTER (WHERE tipo = 'AJUSTE_ENTRADA'), 0)::int AS entradas
       FROM movimientos_inventario
       WHERE pedido_id = $1 AND producto_id = $2 AND deleted_at IS NULL`,
      [pedidoId, fila.producto_id]
    );
    const pendiente = Number(neto.salidas) - Number(neto.entradas);
    if (pendiente <= 0) continue;

    await cliente.query(
      `UPDATE productos SET stock = stock + $1 WHERE id = $2`,
      [pendiente, fila.producto_id]
    );
    await cliente.query(
      `INSERT INTO movimientos_inventario (
         uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad
       ) VALUES ($1, $2, $3, $4, 'AJUSTE_ENTRADA', $5)`,
      [crypto.randomUUID(), fila.producto_id, pedidoId, usuarioId, pendiente]
    );
  }
}

async function transicionar(req, res) {
  const cliente = await pool.connect();
  try {
    const hacia = String(req.body.estado || req.body.nuevoEstado || '').trim();
    if (!hacia) {
      return res.status(400).json({ error: 'estado es obligatorio' });
    }
    // ENTREGADO requiere evidencia en /api/entregas (foto + GPS + uuid).
    if (hacia === 'ENTREGADO') {
      return res.status(409).json({
        error: 'Registra la entrega con evidencia en POST /api/entregas/pedidos/:id',
        codigo: 'ENTREGA_REQUIERE_EVIDENCIA'
      });
    }

    const pickerId = req.body.pickerId || req.body.picker_id || null;
    const repartidorId = req.body.repartidorId || req.body.repartidor_id || null;

    await cliente.query('BEGIN');
    const { rows } = await cliente.query(
      `SELECT *
       FROM pedidos
       WHERE id = $1 AND deleted_at IS NULL
       FOR UPDATE`,
      [req.params.id]
    );
    const pedido = rows[0];
    if (!pedido) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Pedido no encontrado' });
    }

    if (!puedeVerPedido(req.usuario, pedido) && req.usuario.rol !== 'ADMINISTRADOR') {
      await cliente.query('ROLLBACK');
      const codigo =
        req.usuario.rol === 'PICKER' || req.usuario.rol === 'REPARTIDOR'
          ? 'PEDIDO_NO_ASIGNADO'
          : 'PERMISO_DENEGADO';
      return res.status(403).json({
        error: 'No tienes permiso sobre este pedido',
        codigo
      });
    }

    // Idempotencia del cierre: ya LISTO → 200 sin mutar ni tocar inventario.
    if (hacia === 'LISTO' && pedido.estado === 'LISTO') {
      if (
        req.usuario.rol === 'PICKER' &&
        (!pedido.picker_id || pedido.picker_id !== req.usuario.id)
      ) {
        await cliente.query('ROLLBACK');
        return res.status(403).json({
          error: 'El pedido está asignado a otro picker',
          codigo: 'PEDIDO_NO_ASIGNADO'
        });
      }
      const completoIdem = await cargarPedidoCompleto(cliente, pedido.id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        pedido: completoIdem.pedido,
        detalles: completoIdem.detalles,
        direccion: completoIdem.direccion,
        pago: completoIdem.pago
      });
    }

    const pago = await cargarPago(cliente, pedido.id);
    const evaluacion = evaluarTransicion({
      desde: pedido.estado,
      hacia,
      rol: req.usuario.rol,
      usuarioId: req.usuario.id,
      pedido,
      pago,
      origenInternoBancario: false
    });

    if (!evaluacion.ok) {
      await cliente.query('ROLLBACK');
      return res.status(evaluacion.http).json({
        error: evaluacion.error,
        codigo: evaluacion.codigo
      });
    }

    if (hacia === 'LISTO') {
      // Bloqueo de líneas + validación estricta (cantidades, sustituciones, ELIMINADO).
      // Esta transición NO inserta movimientos de inventario.
      const validacion = await validarPreparacionCompleta(cliente, pedido.id);
      if (!validacion.ok) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: validacion.error,
          codigo: validacion.codigo,
          impedimentos: validacion.impedimentos
        });
      }
    }

    // Asignaciones opcionales (admin) sin tocar stock
    let nextPicker = pedido.picker_id;
    let nextRepartidor = pedido.repartidor_id;
    if (req.usuario.rol === 'ADMINISTRADOR') {
      if (pickerId) {
        if (!UUID_RE.test(String(pickerId))) {
          await cliente.query('ROLLBACK');
          return res.status(400).json({ error: 'pickerId inválido' });
        }
        nextPicker = pickerId;
      } else if (hacia === 'EN_PICKING' && !nextPicker) {
        // permitir que el admin deje picker_id null (pool) o asigne después
      }
      if (repartidorId) {
        if (!UUID_RE.test(String(repartidorId))) {
          await cliente.query('ROLLBACK');
          return res.status(400).json({ error: 'repartidorId inválido' });
        }
        nextRepartidor = repartidorId;
      }
    }

    if (req.usuario.rol === 'PICKER' && hacia === 'EN_PICKING' && !pedido.picker_id) {
      nextPicker = req.usuario.id;
    }
    if (req.usuario.rol === 'REPARTIDOR' && hacia === 'EN_CAMINO' && !pedido.repartidor_id) {
      nextRepartidor = req.usuario.id;
    }

    // IMPORTANTE: esta transición NO inserta SALIDA_VENTA ni descuenta stock de venta.
    await cliente.query(
      `UPDATE pedidos
       SET estado = $2,
           picker_id = $3,
           repartidor_id = $4
       WHERE id = $1`,
      [pedido.id, hacia, nextPicker, nextRepartidor]
    );

    if (hacia === 'CANCELADO') {
      await restituirStockCancelacion(cliente, pedido.id, req.usuario.id);
      if (pago && !['CONFIRMADO', 'CANCELADO', 'EXPIRADO'].includes(pago.estado)) {
        await cliente.query(
          `UPDATE pagos SET estado = 'CANCELADO', updated_at = now() WHERE id = $1`,
          [pago.id]
        ).catch(() => {});
      }
    }

    const completo = await cargarPedidoCompleto(cliente, pedido.id);
    await cliente.query('COMMIT');
    return res.status(200).json({
      pedido: completo.pedido,
      detalles: completo.detalles,
      direccion: completo.direccion,
      pago: completo.pago
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al transicionar pedido:', error.message);
    return res.status(500).json({ error: 'No se pudo actualizar el estado del pedido' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  crear,
  listar,
  obtenerPorId,
  transicionar,
  // exportados para pruebas / fases posteriores
  cargarPedidoCompleto,
  restituirStockCancelacion,
  evaluarTransicionBancaria: evaluarTransicion
};
