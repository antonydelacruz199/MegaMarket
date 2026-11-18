const pool = require('../database');
const {
  esUuid,
  assertRepartidorAsignado,
  puedeMarcarEntregadoSegunPago,
  validarEvidencia,
  mapearEntrega,
  mapearPedidoEntrega
} = require('../entregas');

async function cargarPago(cliente, pedidoId) {
  const { rows } = await cliente.query(
    `SELECT id, modalidad, estado, numero_operacion_banco, fecha_confirmacion
     FROM pagos
     WHERE pedido_id = $1
     ORDER BY created_at DESC
     LIMIT 1`,
    [pedidoId]
  );
  return rows[0] || null;
}

async function cargarEntregaPorPedido(cliente, pedidoId) {
  const { rows } = await cliente.query(
    `SELECT * FROM entregas WHERE pedido_id = $1 LIMIT 1`,
    [pedidoId]
  );
  return rows[0] || null;
}

async function listar(req, res) {
  try {
    // LISTO sin asignar = pool para tomar (picker → delivery).
    // EN_CAMINO / ENTREGADO solo los del repartidor autenticado.
    const { rows: pedidos } = await pool.query(
      `SELECT id, cliente_id, estado, total_centimos, franja_entrega_id, repartidor_id,
              fecha, version, updated_at
       FROM pedidos
       WHERE deleted_at IS NULL
         AND (
           (estado = 'LISTO' AND (repartidor_id IS NULL OR repartidor_id = $1))
           OR (estado IN ('EN_CAMINO', 'ENTREGADO') AND repartidor_id = $1)
         )
       ORDER BY
         CASE
           WHEN estado = 'LISTO' THEN 0
           WHEN estado = 'EN_CAMINO' THEN 1
           ELSE 2
         END,
         fecha ASC`,
      [req.usuario.id]
    );

    const resultado = [];
    for (const pedido of pedidos) {
      const entrega = await cargarEntregaPorPedido(pool, pedido.id);
      resultado.push({
        pedido: mapearPedidoEntrega(pedido),
        entrega: mapearEntrega(entrega)
      });
    }
    return res.status(200).json({ pedidos: resultado });
  } catch (error) {
    console.error('Error al listar entregas:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar los pedidos de entrega' });
  }
}

async function obtenerPorId(req, res) {
  try {
    const { rows } = await pool.query(
      `SELECT id, cliente_id, estado, total_centimos, franja_entrega_id, repartidor_id,
              fecha, version, updated_at, deleted_at
       FROM pedidos
       WHERE id = $1`,
      [req.params.id]
    );
    const pedido = rows[0];
    if (!pedido || pedido.deleted_at) {
      return res.status(404).json({
        error: 'Pedido no encontrado',
        codigo: 'PEDIDO_NO_ENCONTRADO'
      });
    }
    const asig = assertRepartidorAsignado(pedido, req.usuario.id);
    if (!asig.ok) {
      return res.status(asig.http).json({ error: asig.error, codigo: asig.codigo });
    }

    const pago = await cargarPago(pool, pedido.id);
    const entrega = await cargarEntregaPorPedido(pool, pedido.id);
    const { rows: direccion } = await pool.query(
      `SELECT departamento, provincia, distrito, direccion, telefono
       FROM direcciones
       WHERE pedido_id = $1 AND deleted_at IS NULL
       LIMIT 1`,
      [pedido.id]
    );

    return res.status(200).json({
      pedido: mapearPedidoEntrega(pedido),
      direccion: direccion[0] || null,
      entrega: mapearEntrega(entrega),
      pago: pago
        ? { modalidad: pago.modalidad, estado: pago.estado }
        : null
    });
  } catch (error) {
    console.error('Error al obtener entrega:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el detalle de entrega' });
  }
}

async function registrar(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = String(
      req.body.uuidOperacion || req.body.uuid_operacion || ''
    ).trim();
    const evidencia = validarEvidencia({
      fotoReferencia: req.body.fotoReferencia || req.body.foto_referencia,
      latitud: req.body.latitud,
      longitud: req.body.longitud,
      fechaEntrega: req.body.fechaEntrega || req.body.fecha_entrega,
      pedidoId: req.params.id
    });

    if (!esUuid(uuidOperacion)) {
      return res.status(400).json({
        error: 'uuidOperacion es obligatorio y debe ser un UUID válido',
        codigo: 'UUID_INVALIDO'
      });
    }
    if (!evidencia.ok) {
      return res.status(evidencia.http).json({
        error: evidencia.error,
        codigo: evidencia.codigo
      });
    }

    await cliente.query('BEGIN');

    const previoUuid = await cliente.query(
      `SELECT * FROM entregas WHERE uuid_operacion = $1 LIMIT 1`,
      [uuidOperacion]
    );
    if (previoUuid.rows[0]) {
      const prev = previoUuid.rows[0];
      if (prev.pedido_id !== req.params.id || prev.repartidor_id !== req.usuario.id) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: 'uuidOperacion ya utilizado en otra entrega',
          codigo: 'UUID_EN_USO'
        });
      }
      const { rows: [pedidoPrev] } = await cliente.query(
        `SELECT * FROM pedidos WHERE id = $1`,
        [prev.pedido_id]
      );
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        entrega: mapearEntrega(prev),
        pedido: mapearPedidoEntrega(pedidoPrev)
      });
    }

    const { rows: pedidos } = await cliente.query(
      `SELECT *
       FROM pedidos
       WHERE id = $1 AND deleted_at IS NULL
       FOR UPDATE`,
      [req.params.id]
    );
    const pedido = pedidos[0];
    const asig = assertRepartidorAsignado(pedido, req.usuario.id);
    if (!asig.ok) {
      await cliente.query('ROLLBACK');
      return res.status(asig.http).json({ error: asig.error, codigo: asig.codigo });
    }

    const entregaExistente = await cargarEntregaPorPedido(cliente, pedido.id);
    if (entregaExistente || pedido.estado === 'ENTREGADO') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'El pedido ya tiene una entrega registrada y no admite cambios',
        codigo: 'ENTREGA_CERRADA',
        entrega: mapearEntrega(entregaExistente)
      });
    }

    if (pedido.estado !== 'EN_CAMINO') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se registra entrega cuando el pedido está EN_CAMINO',
        codigo: 'PEDIDO_NO_EN_CAMINO'
      });
    }

    const pago = await cargarPago(cliente, pedido.id);
    const pagoOk = puedeMarcarEntregadoSegunPago(pago, pedido);
    if (!pagoOk.ok) {
      await cliente.query('ROLLBACK');
      return res.status(pagoOk.http).json({
        error: pagoOk.error,
        codigo: pagoOk.codigo
      });
    }

    const fechaSql = evidencia.fechaEntrega || new Date().toISOString();
    const { rows: [entrega] } = await cliente.query(
      `INSERT INTO entregas (
         uuid_operacion, pedido_id, repartidor_id,
         foto_referencia, latitud, longitud, fecha_entrega
       ) VALUES ($1, $2, $3, $4, $5, $6, $7::timestamptz)
       RETURNING *`,
      [
        uuidOperacion,
        pedido.id,
        req.usuario.id,
        evidencia.fotoReferencia,
        evidencia.latitud,
        evidencia.longitud,
        fechaSql
      ]
    );

    // Misma TX: evidencia + estado. Sin tocar stock ni confirmar pagos.
    const { rows: [pedidoUpd] } = await cliente.query(
      `UPDATE pedidos
       SET estado = 'ENTREGADO'
       WHERE id = $1
       RETURNING *`,
      [pedido.id]
    );

    await cliente.query('COMMIT');
    return res.status(201).json({
      idempotente: false,
      entrega: mapearEntrega(entrega),
      pedido: mapearPedidoEntrega(pedidoUpd),
      compatibilidadHistorica: Boolean(pagoOk.legado)
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'Entrega duplicada o uuidOperacion en uso',
        codigo: 'ENTREGA_DUPLICADA'
      });
    }
    console.error('Error al registrar entrega:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar la entrega' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  listar,
  obtenerPorId,
  registrar
};
