const pool = require('../database');
const {
  esUuid,
  puedePrepararSegunPago,
  assertPickerAsignado,
  mapearLineaPreparacion,
  mapearEscaneo,
  intentarMarcarListo,
  evaluarEliminacionLinea
} = require('../picking');
const {
  aplicarAjustesInventarioSustitucion,
  restituirInventarioLineaEliminada,
  uuidAjusteEliminacion
} = require('../sustituciones');

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

async function cargarLineasPedido(cliente, pedidoId) {
  const { rows } = await cliente.query(
    `SELECT d.id, d.pedido_id, d.producto_id, d.nombre_producto, d.cantidad,
            d.cantidad_armada, d.estado_picking, d.precio_unitario_centimos,
            d.subtotal_centimos, d.fecha_escaneo, d.escaneado_por,
            p.pasillo, p.codigo_barras,
            s.id AS sustitucion_aceptada_id,
            s.producto_sustituto_id,
            ps.pasillo AS pasillo_sustituto,
            ps.codigo_barras AS codigo_barras_sustituto,
            ps.nombre AS nombre_sustituto,
            (
              d.estado_picking = 'FALTANTE'
              AND EXISTS (
                SELECT 1 FROM sustituciones_pedido sr
                WHERE sr.pedido_detalle_id = d.id AND sr.estado = 'RECHAZADA'
              )
              AND NOT EXISTS (
                SELECT 1 FROM sustituciones_pedido sa
                WHERE sa.pedido_detalle_id = d.id
                  AND sa.estado IN ('PENDIENTE', 'ACEPTADA')
              )
            ) AS resoluble_sin_producto
     FROM pedido_detalle d
     JOIN productos p ON p.id = d.producto_id
     LEFT JOIN LATERAL (
       SELECT s2.id, s2.producto_sustituto_id
       FROM sustituciones_pedido s2
       WHERE s2.pedido_detalle_id = d.id
         AND s2.estado = 'ACEPTADA'
       ORDER BY s2.fecha_respuesta DESC NULLS LAST, s2.created_at DESC
       LIMIT 1
     ) s ON TRUE
     LEFT JOIN productos ps ON ps.id = s.producto_sustituto_id
     WHERE d.pedido_id = $1 AND d.deleted_at IS NULL
     ORDER BY COALESCE(ps.pasillo, p.pasillo) NULLS LAST,
              COALESCE(ps.nombre, p.nombre) ASC,
              d.id ASC`,
    [pedidoId]
  );
  return rows.map(mapearLineaPreparacion);
}

async function cargarSustitucionAceptada(cliente, detalleId) {
  const { rows } = await cliente.query(
    `SELECT s.*, ps.codigo_barras AS sustituto_codigo_barras, ps.nombre AS sustituto_nombre
     FROM sustituciones_pedido s
     JOIN productos ps ON ps.id = s.producto_sustituto_id
     WHERE s.pedido_detalle_id = $1
       AND s.estado = 'ACEPTADA'
     ORDER BY s.fecha_respuesta DESC NULLS LAST, s.created_at DESC
     LIMIT 1
     FOR UPDATE OF s`,
    [detalleId]
  );
  return rows[0] || null;
}

async function disponibleProductoPicking(cliente, productoId) {
  const { rows } = await cliente.query(
    `SELECT p.id, p.stock, p.activo, p.deleted_at,
            COALESCE((
              SELECT SUM(r.cantidad)::int FROM reservas_carrito r WHERE r.producto_id = p.id
            ), 0) AS reservado
     FROM productos p
     WHERE p.id = $1
     FOR UPDATE OF p`,
    [productoId]
  );
  const p = rows[0];
  if (!p || p.deleted_at || !p.activo) return null;
  return { ...p, disponible: Number(p.stock) - Number(p.reservado) };
}

function mapearPedidoPicking(fila) {
  return {
    id: fila.id,
    clienteId: fila.cliente_id,
    estado: fila.estado,
    totalCentimos: Number(fila.total_centimos),
    franjaEntregaId: fila.franja_entrega_id || null,
    pickerId: fila.picker_id,
    preferenciaSustitucion: fila.preferencia_sustitucion || null,
    fecha: fila.fecha,
    version: Number(fila.version),
    updatedAt: fila.updated_at
  };
}

async function validarPedidoPreparable(cliente, pedidoId, pickerId) {
  const { rows } = await cliente.query(
    `SELECT *
     FROM pedidos
     WHERE id = $1 AND deleted_at IS NULL
     FOR UPDATE`,
    [pedidoId]
  );
  const pedido = rows[0];
  const asig = assertPickerAsignado(pedido, pickerId);
  if (!asig.ok) return { ...asig, pedido: null, pago: null };

  if (pedido.estado !== 'EN_PICKING') {
    return {
      ok: false,
      http: 409,
      codigo: 'PEDIDO_NO_EN_PICKING',
      error: 'Solo se preparan pedidos en estado EN_PICKING',
      pedido,
      pago: null
    };
  }

  const pago = await cargarPago(cliente, pedido.id);
  const pagoOk = puedePrepararSegunPago(pago);
  if (!pagoOk.ok) return { ...pagoOk, pedido, pago };

  return { ok: true, pedido, pago };
}

async function listar(req, res) {
  try {
    const { rows: pedidos } = await pool.query(
      `SELECT id, cliente_id, estado, total_centimos, franja_entrega_id, picker_id,
              preferencia_sustitucion, fecha, version, updated_at
       FROM pedidos
       WHERE deleted_at IS NULL
         AND picker_id = $1
         AND estado = 'EN_PICKING'
       ORDER BY fecha ASC`,
      [req.usuario.id]
    );

    const resultado = [];
    for (const pedido of pedidos) {
      const pago = await cargarPago(pool, pedido.id);
      if (!puedePrepararSegunPago(pago).ok) continue;
      const lineas = await cargarLineasPedido(pool, pedido.id);
      resultado.push({
        pedido: mapearPedidoPicking(pedido),
        detalles: lineas
      });
    }

    return res.status(200).json({ pedidos: resultado });
  } catch (error) {
    console.error('Error al listar picking:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar los pedidos de picking' });
  }
}

async function obtenerPorId(req, res) {
  const cliente = await pool.connect();
  try {
    const { rows } = await cliente.query(
      `SELECT id, cliente_id, estado, total_centimos, franja_entrega_id, picker_id,
              preferencia_sustitucion, fecha, version, updated_at, deleted_at
       FROM pedidos
       WHERE id = $1`,
      [req.params.id]
    );
    const pedido = rows[0];
    if (!pedido || pedido.deleted_at) {
      return res.status(404).json({ error: 'Pedido no encontrado', codigo: 'PEDIDO_NO_ENCONTRADO' });
    }
    const asig = assertPickerAsignado(pedido, req.usuario.id);
    if (!asig.ok) {
      return res.status(asig.http).json({ error: asig.error, codigo: asig.codigo });
    }

    const pago = await cargarPago(cliente, pedido.id);
    const lineas = await cargarLineasPedido(cliente, pedido.id);
    return res.status(200).json({
      pedido: mapearPedidoPicking(pedido),
      detalles: lineas,
      pago: pago
        ? {
            modalidad: pago.modalidad,
            estado: pago.estado
          }
        : null
    });
  } catch (error) {
    console.error('Error al obtener picking:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el detalle de preparación' });
  } finally {
    cliente.release();
  }
}

async function escanear(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = String(
      req.body.uuidOperacion || req.body.uuid_operacion || ''
    ).trim();
    const codigoBarras = String(
      req.body.codigoBarras || req.body.codigo_barras || ''
    ).trim();
    const cantidad = Number(req.body.cantidad == null ? 1 : req.body.cantidad);

    if (!esUuid(uuidOperacion)) {
      return res.status(400).json({
        error: 'uuidOperacion es obligatorio y debe ser un UUID válido',
        codigo: 'UUID_INVALIDO'
      });
    }
    if (!codigoBarras) {
      return res.status(400).json({
        error: 'codigoBarras es obligatorio',
        codigo: 'CODIGO_REQUERIDO'
      });
    }
    if (!Number.isInteger(cantidad) || cantidad <= 0) {
      return res.status(400).json({
        error: 'cantidad debe ser un entero > 0',
        codigo: 'CANTIDAD_INVALIDA'
      });
    }

    await cliente.query('BEGIN');

    const prep = await validarPedidoPreparable(cliente, req.params.id, req.usuario.id);
    if (!prep.ok) {
      await cliente.query('ROLLBACK');
      return res.status(prep.http).json({ error: prep.error, codigo: prep.codigo });
    }

    const { rows: detalles } = await cliente.query(
      `SELECT d.*, p.codigo_barras AS producto_codigo_barras, p.pasillo, p.nombre AS producto_nombre
       FROM pedido_detalle d
       JOIN productos p ON p.id = d.producto_id
       WHERE d.id = $1 AND d.pedido_id = $2 AND d.deleted_at IS NULL
       FOR UPDATE OF d`,
      [req.params.detalleId, req.params.id]
    );
    const detalle = detalles[0];
    if (!detalle) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Línea de pedido no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }

    const previo = await cliente.query(
      `SELECT * FROM picking_escaneos WHERE uuid_operacion = $1 LIMIT 1`,
      [uuidOperacion]
    );
    if (previo.rows[0]) {
      const escaneoPrev = previo.rows[0];
      if (
        escaneoPrev.pedido_detalle_id !== detalle.id ||
        escaneoPrev.picker_id !== req.usuario.id
      ) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: 'uuidOperacion ya utilizado en otra operación',
          codigo: 'UUID_EN_USO'
        });
      }
      const lineas = await cargarLineasPedido(cliente, prep.pedido.id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        escaneo: mapearEscaneo(escaneoPrev),
        detalle: lineas.find((l) => l.id === detalle.id),
        detalles: lineas
      });
    }

    const sustitucionAceptada = await cargarSustitucionAceptada(cliente, detalle.id);
    const esSustituto = Boolean(sustitucionAceptada);

    // Con sustitución ACEPTADA: se escanea en FALTANTE/PENDIENTE (aún no SUSTITUIDO).
    // Sin sustitución: PENDIENTE/ARMADO como antes.
    const estadosPermitidos = esSustituto
      ? ['FALTANTE', 'PENDIENTE']
      : ['PENDIENTE', 'ARMADO'];
    if (!estadosPermitidos.includes(detalle.estado_picking)) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: esSustituto
          ? `No se puede escanear el sustituto en estado ${detalle.estado_picking}`
          : `No se puede escanear una línea en estado ${detalle.estado_picking}`,
        codigo: 'ESTADO_PICKING_INVALIDO'
      });
    }

    const codigoEsperado = esSustituto
      ? String(sustitucionAceptada.sustituto_codigo_barras || '').trim()
      : detalle.producto_codigo_barras
        ? String(detalle.producto_codigo_barras).trim()
        : '';
    const correcto = Boolean(codigoEsperado) && codigoEsperado === codigoBarras;

    if (!correcto) {
      const { rows: [escaneo] } = await cliente.query(
        `INSERT INTO picking_escaneos (
           uuid_operacion, pedido_detalle_id, picker_id, codigo_barras, cantidad, resultado
         ) VALUES ($1, $2, $3, $4, $5, 'INCORRECTO')
         RETURNING *`,
        [uuidOperacion, detalle.id, req.usuario.id, codigoBarras, cantidad]
      );
      const lineas = await cargarLineasPedido(cliente, prep.pedido.id);
      await cliente.query('COMMIT');
      return res.status(409).json({
        error: esSustituto
          ? 'Código de barras no corresponde al producto sustituto aceptado'
          : 'Código de barras no corresponde al producto solicitado',
        codigo: 'CODIGO_INCORRECTO',
        idempotente: false,
        escaneo: mapearEscaneo(escaneo),
        detalle: lineas.find((l) => l.id === detalle.id),
        detalles: lineas
      });
    }

    const armadaActual = Number(detalle.cantidad_armada);
    const solicitada = Number(detalle.cantidad);
    if (armadaActual + cantidad > solicitada) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'La cantidad armada no puede superar la solicitada',
        codigo: 'CANTIDAD_EXCEDIDA'
      });
    }

    const nuevaArmada = armadaActual + cantidad;
    let nuevoEstado;
    if (nuevaArmada === solicitada) {
      nuevoEstado = esSustituto ? 'SUSTITUIDO' : 'ARMADO';
    } else {
      nuevoEstado = 'PENDIENTE';
    }

    // Update optimista: evita que dos escaneos concurrentes pasen la cantidad.
    const { rows: [actualizado] } = await cliente.query(
      `UPDATE pedido_detalle
       SET cantidad_armada = $2,
           estado_picking = $3,
           fecha_escaneo = now(),
           escaneado_por = $4
       WHERE id = $1 AND cantidad_armada = $5
       RETURNING id`,
      [detalle.id, nuevaArmada, nuevoEstado, req.usuario.id, armadaActual]
    );
    if (!actualizado) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'La cantidad armada no puede superar la solicitada',
        codigo: 'CANTIDAD_EXCEDIDA'
      });
    }

    // Inventario compensatorio solo al completar el sustituto (una vez por sustitución).
    if (esSustituto && nuevoEstado === 'SUSTITUIDO') {
      const original = await disponibleProductoPicking(
        cliente,
        sustitucionAceptada.producto_original_id
      );
      const sustitutoDisp = await disponibleProductoPicking(
        cliente,
        sustitucionAceptada.producto_sustituto_id
      );
      const ajusteInv = await aplicarAjustesInventarioSustitucion(cliente, {
        sustitucionId: sustitucionAceptada.id,
        pedidoId: prep.pedido.id,
        productoOriginalId: sustitucionAceptada.producto_original_id,
        productoSustitutoId: sustitucionAceptada.producto_sustituto_id,
        cantidad: Number(sustitucionAceptada.cantidad),
        actorId: req.usuario.id,
        disponibleOriginal: original,
        disponibleSustituto: sustitutoDisp
      });
      if (ajusteInv.ok === false) {
        await cliente.query('ROLLBACK');
        return res.status(ajusteInv.http).json({
          error: ajusteInv.error,
          codigo: ajusteInv.codigo
        });
      }
    }

    const { rows: [escaneo] } = await cliente.query(
      `INSERT INTO picking_escaneos (
         uuid_operacion, pedido_detalle_id, picker_id, codigo_barras, cantidad, resultado
       ) VALUES ($1, $2, $3, $4, $5, 'CORRECTO')
       RETURNING *`,
      [uuidOperacion, detalle.id, req.usuario.id, codigoBarras, cantidad]
    );

    // Invariante: el escaneo normal no emite SALIDA_VENTA; el de sustituto solo AJUSTE_* al cerrar.
    const lineas = await cargarLineasPedido(cliente, prep.pedido.id);
    const pasoAListo = await intentarMarcarListo(cliente, prep.pedido.id);
    await cliente.query('COMMIT');
    return res.status(201).json({
      idempotente: false,
      escaneo: mapearEscaneo(escaneo),
      detalle: lineas.find((l) => l.id === detalle.id),
      detalles: lineas,
      pedidoListo: pasoAListo,
      estadoPedido: pasoAListo ? 'LISTO' : prep.pedido.estado
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'uuidOperacion ya utilizado',
        codigo: 'UUID_EN_USO'
      });
    }
    console.error('Error al registrar escaneo:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar el escaneo' });
  } finally {
    cliente.release();
  }
}

async function marcarFaltante(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');

    const prep = await validarPedidoPreparable(cliente, req.params.id, req.usuario.id);
    if (!prep.ok) {
      await cliente.query('ROLLBACK');
      return res.status(prep.http).json({ error: prep.error, codigo: prep.codigo });
    }

    const { rows: detalles } = await cliente.query(
      `SELECT d.*, p.pasillo, p.codigo_barras
       FROM pedido_detalle d
       JOIN productos p ON p.id = d.producto_id
       WHERE d.id = $1 AND d.pedido_id = $2 AND d.deleted_at IS NULL
       FOR UPDATE OF d`,
      [req.params.detalleId, req.params.id]
    );
    const detalle = detalles[0];
    if (!detalle) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Línea de pedido no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }

    if (detalle.estado_picking === 'FALTANTE') {
      const lineas = await cargarLineasPedido(cliente, prep.pedido.id);
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        detalle: lineas.find((l) => l.id === detalle.id),
        detalles: lineas
      });
    }

    if (detalle.estado_picking !== 'PENDIENTE') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: `No se puede marcar FALTANTE desde ${detalle.estado_picking}`,
        codigo: 'ESTADO_PICKING_INVALIDO'
      });
    }

    await cliente.query(
      `UPDATE pedido_detalle
       SET estado_picking = 'FALTANTE',
           fecha_escaneo = now(),
           escaneado_por = $2
       WHERE id = $1`,
      [detalle.id, req.usuario.id]
    );

    const lineas = await cargarLineasPedido(cliente, prep.pedido.id);
    await cliente.query('COMMIT');
    return res.status(200).json({
      idempotente: false,
      detalle: lineas.find((l) => l.id === detalle.id),
      detalles: lineas
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al marcar faltante:', error.message);
    return res.status(500).json({ error: 'No se pudo marcar el producto como faltante' });
  } finally {
    cliente.release();
  }
}

const ESTADOS_PAGO_SIN_DEVOLUCION = new Set(['CANCELADO', 'RECHAZADO', 'EXPIRADO']);

function mapearAjuste(ajuste) {
  if (!ajuste) return null;
  return {
    id: ajuste.id,
    tipo: ajuste.tipo,
    motivo: ajuste.motivo,
    montoCentimos: Number(ajuste.monto_centimos),
    estado: ajuste.estado
  };
}

/**
 * Ajuste económico pendiente por la línea que ya no se entrega.
 * No actualiza pagos: un pago CONFIRMADO permanece intacto y la devolución no se confirma.
 */
async function registrarAjusteEliminacion(cliente, { pago, sustitucionId, detalleId, monto }) {
  if (!pago || ESTADOS_PAGO_SIN_DEVOLUCION.has(pago.estado)) return null;
  const montoCentimos = Number(monto);
  if (!Number.isSafeInteger(montoCentimos) || montoCentimos <= 0) return null;

  const uuid = uuidAjusteEliminacion(detalleId);
  const previo = await cliente.query(
    `SELECT * FROM ajustes_pago
     WHERE uuid_operacion = $1
        OR (pago_id = $2 AND sustitucion_id = $3 AND tipo = 'DEVOLUCION')
     ORDER BY created_at ASC
     LIMIT 1`,
    [uuid, pago.id, sustitucionId]
  );
  if (previo.rows[0]) return previo.rows[0];

  try {
    const { rows: [ins] } = await cliente.query(
      `INSERT INTO ajustes_pago (
         uuid_operacion, pago_id, sustitucion_id, tipo, motivo, monto_centimos, estado, observaciones
       ) VALUES ($1, $2, $3, 'DEVOLUCION', 'PRODUCTO_FALTANTE', $4, 'PENDIENTE', $5)
       ON CONFLICT (uuid_operacion) DO NOTHING
       RETURNING *`,
      [
        uuid,
        pago.id,
        sustitucionId,
        montoCentimos,
        'Línea eliminada tras rechazo de sustitución'
      ]
    );
    if (ins) return ins;
  } catch (error) {
    if (!error || error.code !== '23505') throw error;
  }

  const { rows: [otra] } = await cliente.query(
    `SELECT * FROM ajustes_pago
     WHERE uuid_operacion = $1
        OR (pago_id = $2 AND sustitucion_id = $3 AND tipo = 'DEVOLUCION')
     ORDER BY created_at ASC
     LIMIT 1`,
    [uuid, pago.id, sustitucionId]
  );
  return otra || null;
}

async function eliminarLinea(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');

    const { rows: pedidos } = await cliente.query(
      `SELECT * FROM pedidos WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`,
      [req.params.id]
    );
    const pedido = pedidos[0];
    const asig = assertPickerAsignado(pedido, req.usuario.id);
    if (!asig.ok) {
      await cliente.query('ROLLBACK');
      return res.status(asig.http).json({ error: asig.error, codigo: asig.codigo });
    }
    if (!['EN_PICKING', 'LISTO'].includes(pedido.estado)) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se resuelven líneas de un pedido en preparación',
        codigo: 'PEDIDO_NO_EN_PICKING'
      });
    }

    const { rows: detalles } = await cliente.query(
      `SELECT d.*
       FROM pedido_detalle d
       WHERE d.id = $1 AND d.pedido_id = $2 AND d.deleted_at IS NULL
       FOR UPDATE OF d`,
      [req.params.detalleId, req.params.id]
    );
    const detalle = detalles[0];
    if (!detalle) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Línea de pedido no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }

    const { rows: sustituciones } = await cliente.query(
      `SELECT id, pedido_detalle_id, estado, fecha_respuesta, created_at
       FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1
       FOR UPDATE`,
      [detalle.id]
    );
    const decision = evaluarEliminacionLinea(detalle, sustituciones);
    if (!decision.ok) {
      await cliente.query('ROLLBACK');
      return res.status(decision.http).json({
        error: decision.error,
        codigo: decision.codigo,
        sustitucionId: decision.sustitucionId || null
      });
    }

    if (pedido.estado === 'LISTO' && !decision.yaResuelta) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se resuelven líneas de un pedido en preparación',
        codigo: 'PEDIDO_NO_EN_PICKING'
      });
    }

    const pago = await cargarPago(cliente, pedido.id);
    if (!decision.yaResuelta) {
      const pagoOk = puedePrepararSegunPago(pago);
      if (!pagoOk.ok) {
        await cliente.query('ROLLBACK');
        return res.status(pagoOk.http).json({ error: pagoOk.error, codigo: pagoOk.codigo });
      }
    }

    const inventario = await restituirInventarioLineaEliminada(cliente, {
      detalleId: detalle.id,
      pedidoId: pedido.id,
      productoId: detalle.producto_id,
      cantidadLinea: Number(detalle.cantidad),
      actorId: req.usuario.id
    });
    const ajuste = await registrarAjusteEliminacion(cliente, {
      pago,
      sustitucionId: decision.sustitucionId,
      detalleId: detalle.id,
      monto: detalle.subtotal_centimos
    });

    if (!decision.yaResuelta) {
      await cliente.query(
        `UPDATE pedido_detalle
         SET estado_picking = 'ELIMINADO',
             cantidad_armada = 0,
             fecha_escaneo = now(),
             escaneado_por = $2
         WHERE id = $1`,
        [detalle.id, req.usuario.id]
      );
    }

    const pasoAListo = await intentarMarcarListo(cliente, pedido.id);
    const lineas = await cargarLineasPedido(cliente, pedido.id);
    await cliente.query('COMMIT');
    return res.status(200).json({
      idempotente: Boolean(decision.yaResuelta),
      detalle: lineas.find((l) => l.id === detalle.id),
      detalles: lineas,
      ajuste: mapearAjuste(ajuste),
      inventarioRestituido: inventario.cantidad || 0,
      pedidoListo: pasoAListo,
      estadoPedido: pasoAListo ? 'LISTO' : pedido.estado
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al eliminar línea de picking:', error.message);
    return res.status(500).json({ error: 'No se pudo resolver la línea sin producto' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  listar,
  obtenerPorId,
  escanear,
  marcarFaltante,
  eliminarLinea
};
