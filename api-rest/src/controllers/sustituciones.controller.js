const crypto = require('crypto');
const pool = require('../database');
const {
  precioVigente,
  diferenciaPrecioDentroDeTolerancia,
  cantidadFaltanteDetalle,
  montoAjusteCentimos,
  mapearSustitucion,
  mapearCandidato
} = require('../sustituciones');
const { assertPickerAsignado, esUuid } = require('../picking');

async function cargarPago(cliente, pedidoId) {
  const { rows } = await cliente.query(
    `SELECT id, modalidad, estado FROM pagos
     WHERE pedido_id = $1
     ORDER BY created_at DESC
     LIMIT 1`,
    [pedidoId]
  );
  return rows[0] || null;
}

async function disponibleProducto(cliente, productoId) {
  const { rows } = await cliente.query(
    `SELECT p.id, p.nombre, p.marca, p.categoria_id, p.precio_centimos, p.precio_oferta_centimos,
            p.es_oferta, p.stock, p.pasillo, p.codigo_barras, p.activo, p.deleted_at,
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
  return {
    ...p,
    disponible: Number(p.stock) - Number(p.reservado)
  };
}

/** Pedidos donde aún tiene sentido aplicar la preferencia automática. */
const ESTADOS_PEDIDO_COMPATIBLES = new Set(['EN_PICKING', 'LISTO']);

async function cancelarSustitucionIncompatible(cliente, sustitucionId, pedidoId) {
  const { rows: [upd] } = await cliente.query(
    `UPDATE sustituciones_pedido
     SET estado = 'CANCELADA',
         fecha_respuesta = now(),
         respuesta_automatica = true,
         updated_at = now()
     WHERE id = $1 AND estado IN ('PENDIENTE', 'SIN_STOCK')
     RETURNING *`,
    [sustitucionId]
  );
  if (!upd) return null;
  return mapearSustitucion({ ...upd, pedido_id: pedidoId });
}

/**
 * Cierra propuestas abiertas de un pedido (p. ej. al pasar a CANCELADO).
 * Afecta PENDIENTE y avisos SIN_STOCK; no toca ACEPTADA/RECHAZADA.
 */
async function cancelarSustitucionesAbiertasDePedido(cliente, pedidoId) {
  const { rows } = await cliente.query(
    `UPDATE sustituciones_pedido s
     SET estado = 'CANCELADA',
         fecha_respuesta = now(),
         respuesta_automatica = true,
         updated_at = now()
     FROM pedido_detalle d
     WHERE s.pedido_detalle_id = d.id
       AND d.pedido_id = $1
       AND s.estado IN ('PENDIENTE', 'SIN_STOCK')
     RETURNING s.*`,
    [pedidoId]
  );
  return rows.map((r) => mapearSustitucion({ ...r, pedido_id: pedidoId }));
}

/** Cierra abiertas de pedidos ya incompatibles (CANCELADO, etc.) del cliente. */
async function cancelarAbiertasPedidosIncompatibles(cliente, clienteId) {
  const { rows } = await cliente.query(
    `UPDATE sustituciones_pedido s
     SET estado = 'CANCELADA',
         fecha_respuesta = now(),
         respuesta_automatica = true,
         updated_at = now()
     FROM pedido_detalle d
     JOIN pedidos p ON p.id = d.pedido_id
     WHERE s.pedido_detalle_id = d.id
       AND p.cliente_id = $1
       AND s.estado IN ('PENDIENTE', 'SIN_STOCK')
       AND (
         p.deleted_at IS NOT NULL
         OR p.estado NOT IN ('EN_PICKING', 'LISTO')
       )
     RETURNING s.id`,
    [clienteId]
  );
  return rows.length;
}

/**
 * Aplica preferencia del pedido a propuestas vencidas (misma TX / FOR UPDATE).
 * No inventa confirmaciones bancarias: ajustes quedan PENDIENTE.
 * Pedidos CANCELADO/ENTREGADO u otros incompatibles → CANCELADA (sin inventario).
 */
async function resolverExpiradas(cliente, { sustitucionId = null, pedidoId = null } = {}) {
  const params = [];
  let filtro = `s.estado = 'PENDIENTE' AND s.fecha_limite <= now()`;
  if (sustitucionId) {
    params.push(sustitucionId);
    filtro = `s.id = $${params.length} AND s.estado = 'PENDIENTE' AND s.fecha_limite <= now()`;
  } else if (pedidoId) {
    params.push(pedidoId);
    filtro = `s.estado = 'PENDIENTE' AND s.fecha_limite <= now()
              AND d.pedido_id = $${params.length}`;
  }

  const { rows } = await cliente.query(
    `SELECT s.id, d.pedido_id, p.estado AS pedido_estado, p.deleted_at AS pedido_deleted_at
     FROM sustituciones_pedido s
     JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
     JOIN pedidos p ON p.id = d.pedido_id
     WHERE ${filtro}
     ORDER BY s.fecha_limite ASC
     FOR UPDATE OF s`,
    params
  );

  const aplicados = [];
  for (const fila of rows) {
    if (
      fila.pedido_deleted_at ||
      !ESTADOS_PEDIDO_COMPATIBLES.has(fila.pedido_estado)
    ) {
      const cancelada = await cancelarSustitucionIncompatible(
        cliente,
        fila.id,
        fila.pedido_id
      );
      if (cancelada) aplicados.push(cancelada);
      continue;
    }

    let r = await decidirInterno(cliente, {
      sustitucionId: fila.id,
      automatica: true,
      usuarioId: null,
      exigirPropietario: false
    });
    // Si la preferencia era ACEPTAR pero ya no hay stock, no dejar PENDIENTE en bucle.
    if (!r.ok && ['STOCK_INSUFICIENTE', 'SUSTITUTO_NO_DISPONIBLE'].includes(r.codigo)) {
      r = await decidirInterno(cliente, {
        sustitucionId: fila.id,
        decision: 'RECHAZAR',
        automatica: true,
        usuarioId: null,
        exigirPropietario: false
      });
    }
    if (r.ok) aplicados.push(r.sustitucion);
  }
  return aplicados;
}

async function decidirInterno(
  cliente,
  { sustitucionId, decision = null, automatica = false, usuarioId, exigirPropietario }
) {
  const { rows } = await cliente.query(
    `SELECT s.*, d.pedido_id, d.estado_picking, d.cantidad AS detalle_cantidad,
            d.producto_id AS detalle_producto_id, d.precio_unitario_centimos,
            p.cliente_id, p.preferencia_sustitucion, p.picker_id,
            p.estado AS pedido_estado, p.deleted_at AS pedido_deleted_at
     FROM sustituciones_pedido s
     JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
     JOIN pedidos p ON p.id = d.pedido_id
     WHERE s.id = $1
     FOR UPDATE OF s, d, p`,
    [sustitucionId]
  );
  const fila = rows[0];
  if (!fila) {
    return {
      ok: false,
      http: 404,
      codigo: 'SUSTITUCION_NO_ENCONTRADA',
      error: 'Sustitución no encontrada'
    };
  }

  if (exigirPropietario && fila.cliente_id !== usuarioId) {
    return {
      ok: false,
      http: 403,
      codigo: 'PERMISO_DENEGADO',
      error: 'Solo el cliente propietario puede decidir esta sustitución'
    };
  }

  if (fila.estado === 'ACEPTADA' || fila.estado === 'RECHAZADA' || fila.estado === 'CANCELADA') {
    if (fila.estado === 'CANCELADA') {
      return {
        ok: false,
        http: 409,
        codigo: 'SUSTITUCION_CANCELADA',
        error: 'La sustitución quedó cancelada porque el pedido ya no admite decisión',
        sustitucion: mapearSustitucion(fila)
      };
    }
    const decisionEfectiva = fila.estado === 'ACEPTADA' ? 'ACEPTAR' : 'RECHAZAR';
    if (!automatica && decision && decision !== decisionEfectiva) {
      return {
        ok: false,
        http: 409,
        codigo: 'DECISION_CONFLICTO',
        error: 'La sustitución ya fue resuelta con otra decisión'
      };
    }
    return {
      ok: true,
      idempotente: true,
      sustitucion: mapearSustitucion(fila),
      http: 200
    };
  }

  if (
    fila.pedido_deleted_at ||
    !ESTADOS_PEDIDO_COMPATIBLES.has(fila.pedido_estado)
  ) {
    const cancelada = await cancelarSustitucionIncompatible(
      cliente,
      sustitucionId,
      fila.pedido_id
    );
    return {
      ok: false,
      http: 409,
      codigo: 'PEDIDO_INCOMPATIBLE',
      error: 'El pedido fue cancelado o ya no admite sustituciones',
      sustitucion: cancelada || mapearSustitucion(fila)
    };
  }

  if (fila.estado !== 'PENDIENTE') {
    return {
      ok: false,
      http: 409,
      codigo: 'ESTADO_INVALIDO',
      error: `No se puede decidir una sustitución en estado ${fila.estado}`
    };
  }

  let decisionFinal = decision;
  // automatica sin decision → preferencia del pedido; con decision → forzar (p. ej. fallback stock).
  if (automatica && !decisionFinal) {
    decisionFinal =
      fila.preferencia_sustitucion === 'ACEPTAR' ? 'ACEPTAR' : 'RECHAZAR';
  }
  if (!['ACEPTAR', 'RECHAZAR'].includes(decisionFinal)) {
    return {
      ok: false,
      http: 400,
      codigo: 'DECISION_INVALIDA',
      error: 'decision debe ser ACEPTAR o RECHAZAR'
    };
  }
  const marcaAutomatica = Boolean(automatica);

  // Si el cliente responde tras el límite, aún se acepta su decisión explícita
  // (la automática solo corre cuando automatica=true y está vencida).

  if (decisionFinal === 'RECHAZAR') {
    const { rows: [upd] } = await cliente.query(
      `UPDATE sustituciones_pedido
       SET estado = 'RECHAZADA',
           fecha_respuesta = now(),
           respuesta_automatica = $2,
           updated_at = now()
       WHERE id = $1
       RETURNING *`,
      [sustitucionId, marcaAutomatica]
    );
    return {
      ok: true,
      idempotente: false,
      sustitucion: mapearSustitucion({ ...upd, pedido_id: fila.pedido_id }),
      http: 200
    };
  }

  // ACEPTAR: autoriza el sustituto. NO arma la línea ni mueve inventario.
  // El picker debe escanear el código del sustituto; los AJUSTE_* ocurren al completar.
  const sustituto = await disponibleProducto(cliente, fila.producto_sustituto_id);
  if (!sustituto) {
    return {
      ok: false,
      http: 409,
      codigo: 'SUSTITUTO_NO_DISPONIBLE',
      error: 'El producto sustituto ya no está disponible'
    };
  }
  if (sustituto.disponible < Number(fila.cantidad)) {
    return {
      ok: false,
      http: 409,
      codigo: 'STOCK_INSUFICIENTE',
      error: 'Stock insuficiente del producto sustituto'
    };
  }

  // Conserva producto_id / importes históricos y las unidades ya armadas del original.
  // Solo quedan por escanear las unidades faltantes (cantidad de la sustitución).
  await cliente.query(
    `UPDATE pedido_detalle
     SET estado_picking = 'FALTANTE',
         fecha_escaneo = NULL,
         escaneado_por = NULL
     WHERE id = $1`,
    [fila.pedido_detalle_id]
  );

  const { rows: [upd] } = await cliente.query(
    `UPDATE sustituciones_pedido
     SET estado = 'ACEPTADA',
         fecha_respuesta = now(),
         respuesta_automatica = $2,
         updated_at = now()
     WHERE id = $1
     RETURNING *`,
    [sustitucionId, marcaAutomatica]
  );

  const pago = await cargarPago(cliente, fila.pedido_id);
  let ajuste = null;
  const plan = montoAjusteCentimos(
    fila.precio_original_centimos,
    fila.precio_sustituto_centimos,
    fila.cantidad
  );
  if (pago && plan) {
    const existente = await cliente.query(
      `SELECT * FROM ajustes_pago
       WHERE pago_id = $1 AND sustitucion_id = $2 AND tipo = $3
       LIMIT 1`,
      [pago.id, sustitucionId, plan.tipo]
    );
    if (existente.rows[0]) {
      ajuste = existente.rows[0];
    } else {
      const { rows: [ins] } = await cliente.query(
        `INSERT INTO ajustes_pago (
           uuid_operacion, pago_id, sustitucion_id, tipo, motivo, monto_centimos, estado
         ) VALUES ($1, $2, $3, $4, 'SUSTITUCION', $5, 'PENDIENTE')
         RETURNING *`,
        [crypto.randomUUID(), pago.id, sustitucionId, plan.tipo, plan.montoCentimos]
      );
      ajuste = ins;
    }
  }

  return {
    ok: true,
    idempotente: false,
    sustitucion: mapearSustitucion({ ...upd, pedido_id: fila.pedido_id }),
    ajuste,
    http: 200
  };
}

async function listarCandidatos(req, res) {
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

    const { rows: detalles } = await cliente.query(
      `SELECT d.*, pr.categoria_id, pr.precio_centimos AS producto_precio
       FROM pedido_detalle d
       JOIN productos pr ON pr.id = d.producto_id
       WHERE d.id = $1 AND d.pedido_id = $2 AND d.deleted_at IS NULL
       FOR UPDATE OF d`,
      [req.params.detalleId, req.params.id]
    );
    const detalle = detalles[0];
    if (!detalle) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Línea no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }
    if (detalle.estado_picking !== 'FALTANTE') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se proponen sustitutos para líneas FALTANTE',
        codigo: 'ESTADO_PICKING_INVALIDO'
      });
    }

    const aceptadaLista = await cliente.query(
      `SELECT id FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1 AND estado = 'ACEPTADA'
       LIMIT 1`,
      [detalle.id]
    );
    if (aceptadaLista.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Ya hay un sustituto aceptado pendiente de escaneo',
        codigo: 'SUSTITUCION_ACEPTADA',
        sustitucionId: aceptadaLista.rows[0].id
      });
    }

    const precioOriginal = Number(detalle.precio_unitario_centimos);
    const cantidadFaltante = cantidadFaltanteDetalle(detalle);
    if (cantidadFaltante <= 0) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'No hay unidades faltantes para sustituir',
        codigo: 'SIN_FALTANTE'
      });
    }

    const { rows } = await cliente.query(
      `SELECT p.id, p.nombre, p.marca, p.categoria_id, p.precio_centimos, p.precio_oferta_centimos,
              p.es_oferta, p.pasillo, p.codigo_barras, p.stock,
              (p.stock - COALESCE((
                SELECT SUM(r.cantidad)::int FROM reservas_carrito r WHERE r.producto_id = p.id
              ), 0)) AS disponible
       FROM productos p
       WHERE p.deleted_at IS NULL
         AND p.activo = true
         AND p.categoria_id = $1
         AND p.id <> $2
         AND (p.stock - COALESCE((
           SELECT SUM(r.cantidad)::int FROM reservas_carrito r WHERE r.producto_id = p.id
         ), 0)) >= $3`,
      [detalle.categoria_id, detalle.producto_id, cantidadFaltante]
    );

    const candidatos = rows
      .filter((p) => diferenciaPrecioDentroDeTolerancia(precioOriginal, precioVigente(p)))
      .map(mapearCandidato);

    await cliente.query('COMMIT');
    return res.status(200).json({
      detalleId: detalle.id,
      precioOriginalCentimos: precioOriginal,
      cantidadSolicitada: Number(detalle.cantidad),
      cantidadArmada: Number(detalle.cantidad_armada || 0),
      cantidadFaltante,
      tolerancia: 0.2,
      candidatos
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al listar sustitutos:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar sustitutos' });
  } finally {
    cliente.release();
  }
}

async function proponer(req, res) {
  const cliente = await pool.connect();
  try {
    const productoSustitutoId = String(
      req.body.productoSustitutoId || req.body.producto_sustituto_id || ''
    ).trim();
    const cantidadBody = req.body.cantidad;

    if (!esUuid(productoSustitutoId)) {
      return res.status(400).json({
        error: 'productoSustitutoId debe ser un UUID válido',
        codigo: 'PRODUCTO_INVALIDO'
      });
    }

    await cliente.query('BEGIN');
    await resolverExpiradas(cliente, { pedidoId: req.params.id });

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
    if (pedido.estado !== 'EN_PICKING') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se proponen sustituciones en EN_PICKING',
        codigo: 'PEDIDO_NO_EN_PICKING'
      });
    }

    const { rows: detalles } = await cliente.query(
      `SELECT d.*, pr.categoria_id
       FROM pedido_detalle d
       JOIN productos pr ON pr.id = d.producto_id
       WHERE d.id = $1 AND d.pedido_id = $2 AND d.deleted_at IS NULL
       FOR UPDATE OF d`,
      [req.params.detalleId, req.params.id]
    );
    const detalle = detalles[0];
    if (!detalle) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Línea no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }
    if (detalle.estado_picking !== 'FALTANTE') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'La línea debe estar FALTANTE',
        codigo: 'ESTADO_PICKING_INVALIDO'
      });
    }

    const pendiente = await cliente.query(
      `SELECT id FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1 AND estado = 'PENDIENTE'
       LIMIT 1`,
      [detalle.id]
    );
    if (pendiente.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Ya existe una propuesta pendiente para esta línea',
        codigo: 'PROPUESTA_PENDIENTE',
        sustitucionId: pendiente.rows[0].id
      });
    }

    const aceptada = await cliente.query(
      `SELECT id FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1 AND estado = 'ACEPTADA'
       LIMIT 1`,
      [detalle.id]
    );
    if (aceptada.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Ya hay un sustituto aceptado pendiente de escaneo',
        codigo: 'SUSTITUCION_ACEPTADA',
        sustitucionId: aceptada.rows[0].id
      });
    }

    const faltante = cantidadFaltanteDetalle(detalle);
    if (faltante <= 0) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'No hay unidades faltantes para sustituir',
        codigo: 'SIN_FALTANTE'
      });
    }
    const cantidad =
      cantidadBody == null ? faltante : Number(cantidadBody);
    if (!Number.isInteger(cantidad) || cantidad <= 0 || cantidad > faltante) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({
        error: 'cantidad inválida: solo se sustituyen las unidades faltantes',
        codigo: 'CANTIDAD_INVALIDA'
      });
    }

    const sustituto = await disponibleProducto(cliente, productoSustitutoId);
    if (!sustituto) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Producto sustituto no disponible',
        codigo: 'SUSTITUTO_NO_DISPONIBLE'
      });
    }
    if (sustituto.categoria_id !== detalle.categoria_id) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'El sustituto debe ser de la misma categoría',
        codigo: 'CATEGORIA_DISTINTA'
      });
    }
    if (sustituto.id === detalle.producto_id) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({
        error: 'El sustituto debe ser distinto del original',
        codigo: 'PRODUCTO_IGUAL'
      });
    }

    const precioOriginal = Number(detalle.precio_unitario_centimos);
    const precioSust = precioVigente(sustituto);
    if (!diferenciaPrecioDentroDeTolerancia(precioOriginal, precioSust)) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'La diferencia de precio supera ±20 %',
        codigo: 'PRECIO_FUERA_RANGO'
      });
    }
    if (sustituto.disponible < cantidad) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Stock insuficiente del sustituto',
        codigo: 'STOCK_INSUFICIENTE'
      });
    }

    const { rows: [creada] } = await cliente.query(
      `INSERT INTO sustituciones_pedido (
         pedido_detalle_id, producto_original_id, producto_sustituto_id, picker_id,
         cantidad, precio_original_centimos, precio_sustituto_centimos, estado,
         fecha_propuesta, fecha_limite
       ) VALUES (
         $1, $2, $3, $4, $5, $6, $7, 'PENDIENTE', now(), now() + interval '5 minutes'
       )
       RETURNING *`,
      [
        detalle.id,
        detalle.producto_id,
        sustituto.id,
        req.usuario.id,
        cantidad,
        precioOriginal,
        precioSust
      ]
    );

    await cliente.query('COMMIT');
    return res.status(201).json({
      sustitucion: mapearSustitucion({ ...creada, pedido_id: pedido.id })
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'Ya existe una propuesta pendiente para esta línea',
        codigo: 'PROPUESTA_PENDIENTE'
      });
    }
    console.error('Error al proponer sustitución:', error.message);
    return res.status(500).json({ error: 'No se pudo proponer la sustitución' });
  } finally {
    cliente.release();
  }
}

async function listarCliente(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    // Pedidos cancelados / incompatibles: cerrar propuestas abiertas de inmediato.
    await cancelarAbiertasPedidosIncompatibles(cliente, req.usuario.id);
    // Resolver vencidas de este cliente antes de listar
    const { rows: pedidos } = await cliente.query(
      `SELECT id FROM pedidos WHERE cliente_id = $1 AND deleted_at IS NULL`,
      [req.usuario.id]
    );
    for (const p of pedidos) {
      await resolverExpiradas(cliente, { pedidoId: p.id });
    }

    const { paginar, metaPaginacion } = require('../paginacion');
    const { page, limit, offset } = paginar(req);
    const estado = req.query.estado ? String(req.query.estado).toUpperCase() : null;
    const params = [req.usuario.id];
    let where = `p.cliente_id = $1 AND p.deleted_at IS NULL`;
    if (estado) {
      params.push(estado);
      where += ` AND s.estado = $${params.length}`;
    }
    const fromSql = `
      FROM sustituciones_pedido s
      JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
      JOIN pedidos p ON p.id = d.pedido_id
      WHERE ${where}
    `;
    const { rows: countRows } = await cliente.query(
      `SELECT COUNT(*)::int AS total ${fromSql}`,
      params
    );
    const total = countRows[0]?.total ?? 0;
    const listParams = [...params, limit, offset];
    const { rows } = await cliente.query(
      `SELECT s.*, d.pedido_id
       ${fromSql}
       ORDER BY s.fecha_propuesta DESC
       LIMIT $${listParams.length - 1} OFFSET $${listParams.length}`,
      listParams
    );
    await cliente.query('COMMIT');
    return res.status(200).json({
      sustituciones: rows.map((r) => mapearSustitucion(r)),
      ...metaPaginacion(total, { page, limit })
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al listar sustituciones:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar las sustituciones' });
  } finally {
    cliente.release();
  }
}

async function obtenerPorId(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    await resolverExpiradas(cliente, { sustitucionId: req.params.id });

    const { rows } = await cliente.query(
      `SELECT s.*, d.pedido_id, p.cliente_id, p.picker_id,
              p.estado AS pedido_estado, p.deleted_at AS pedido_deleted_at
       FROM sustituciones_pedido s
       JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
       JOIN pedidos p ON p.id = d.pedido_id
       WHERE s.id = $1`,
      [req.params.id]
    );
    const fila = rows[0];
    if (!fila) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Sustitución no encontrada',
        codigo: 'SUSTITUCION_NO_ENCONTRADA'
      });
    }
    const esCliente = req.usuario.rol === 'CLIENTE' && fila.cliente_id === req.usuario.id;
    const esPicker = req.usuario.rol === 'PICKER' && fila.picker_id === req.usuario.id;
    const esAdmin = req.usuario.rol === 'ADMINISTRADOR';
    if (!esCliente && !esPicker && !esAdmin) {
      await cliente.query('ROLLBACK');
      return res.status(403).json({ error: 'No autorizado', codigo: 'PERMISO_DENEGADO' });
    }
    let mapeada = mapearSustitucion(fila);
    if (
      (fila.estado === 'PENDIENTE' || fila.estado === 'SIN_STOCK') &&
      (fila.pedido_deleted_at || !ESTADOS_PEDIDO_COMPATIBLES.has(fila.pedido_estado))
    ) {
      const cancelada = await cancelarSustitucionIncompatible(
        cliente,
        fila.id,
        fila.pedido_id
      );
      if (cancelada) mapeada = cancelada;
    }
    await cliente.query('COMMIT');
    return res.status(200).json({ sustitucion: mapeada });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al obtener sustitución:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener la sustitución' });
  } finally {
    cliente.release();
  }
}

function mapearAjusteRespuesta(ajuste) {
  if (!ajuste) return null;
  return {
    id: ajuste.id,
    tipo: ajuste.tipo,
    motivo: ajuste.motivo,
    montoCentimos: Number(ajuste.monto_centimos),
    estado: ajuste.estado
  };
}

async function decidir(req, res, decisionFija = null) {
  const cliente = await pool.connect();
  try {
    const decision =
      decisionFija ||
      String(req.body.decision || req.body.respuesta || '')
        .trim()
        .toUpperCase();

    await cliente.query('BEGIN');
    // Auto-preferencia al vencer; no debe deshacerse si luego hay conflicto de decisión.
    const auto = await resolverExpiradas(cliente, { sustitucionId: req.params.id });
    if (auto.length > 0) {
      const resuelta = auto[0];
      const decisionAuto = resuelta.estado === 'ACEPTADA' ? 'ACEPTAR' : 'RECHAZAR';
      // Verificar propietario sobre la fila ya resuelta
      const { rows: [meta] } = await cliente.query(
        `SELECT p.cliente_id
         FROM sustituciones_pedido s
         JOIN pedido_detalle d ON d.id = s.pedido_detalle_id
         JOIN pedidos p ON p.id = d.pedido_id
         WHERE s.id = $1`,
        [req.params.id]
      );
      if (!meta || meta.cliente_id !== req.usuario.id) {
        await cliente.query('ROLLBACK');
        return res.status(403).json({
          error: 'Solo el cliente propietario puede decidir esta sustitución',
          codigo: 'PERMISO_DENEGADO'
        });
      }
      const { rows: [ajusteFila] } = await cliente.query(
        `SELECT * FROM ajustes_pago WHERE sustitucion_id = $1 ORDER BY created_at DESC LIMIT 1`,
        [req.params.id]
      );
      await cliente.query('COMMIT');
      if (decision && decision !== decisionAuto) {
        return res.status(409).json({
          error: 'La sustitución ya fue resuelta por vencimiento según la preferencia del pedido',
          codigo: 'DECISION_CONFLICTO',
          sustitucion: resuelta,
          ajuste: mapearAjusteRespuesta(ajusteFila)
        });
      }
      return res.status(200).json({
        idempotente: true,
        automatica: true,
        sustitucion: resuelta,
        ajuste: mapearAjusteRespuesta(ajusteFila)
      });
    }

    const resultado = await decidirInterno(cliente, {
      sustitucionId: req.params.id,
      decision,
      automatica: false,
      usuarioId: req.usuario.id,
      exigirPropietario: true
    });

    if (!resultado.ok) {
      if (resultado.sustitucion && ['PEDIDO_INCOMPATIBLE', 'SUSTITUCION_CANCELADA'].includes(resultado.codigo)) {
        await cliente.query('COMMIT');
      } else {
        await cliente.query('ROLLBACK');
      }
      return res.status(resultado.http).json({
        error: resultado.error,
        codigo: resultado.codigo,
        sustitucion: resultado.sustitucion || undefined
      });
    }

    await cliente.query('COMMIT');
    return res.status(resultado.http).json({
      idempotente: Boolean(resultado.idempotente),
      sustitucion: resultado.sustitucion,
      ajuste: mapearAjusteRespuesta(resultado.ajuste)
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al decidir sustitución:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar la decisión' });
  } finally {
    cliente.release();
  }
}

async function aceptar(req, res) {
  return decidir(req, res, 'ACEPTAR');
}

async function rechazar(req, res) {
  return decidir(req, res, 'RECHAZAR');
}

async function resolverExpiradasHttp(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const aplicados = await resolverExpiradas(cliente, {});
    await cliente.query('COMMIT');
    return res.status(200).json({
      resueltas: aplicados.length,
      sustituciones: aplicados
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al resolver expiradas:', error.message);
    return res.status(500).json({ error: 'No se pudieron resolver sustituciones expiradas' });
  } finally {
    cliente.release();
  }
}

/**
 * Avisa al cliente que no hay producto/sustituto en almacén (SIN_STOCK).
 * No elimina la línea: el cliente puede cancelar el pedido desde Sustituciones.
 */
async function avisarSinStock(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    await resolverExpiradas(cliente, { pedidoId: req.params.id });

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
    if (pedido.estado !== 'EN_PICKING') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se informa sin stock en EN_PICKING',
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
        error: 'Línea no encontrada',
        codigo: 'DETALLE_NO_ENCONTRADO'
      });
    }
    if (detalle.estado_picking !== 'FALTANTE') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'La línea debe estar FALTANTE',
        codigo: 'ESTADO_PICKING_INVALIDO'
      });
    }

    const pendiente = await cliente.query(
      `SELECT id FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1 AND estado = 'PENDIENTE'
       LIMIT 1`,
      [detalle.id]
    );
    if (pendiente.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Hay una propuesta pendiente; no se puede avisar sin stock',
        codigo: 'PROPUESTA_PENDIENTE',
        sustitucionId: pendiente.rows[0].id
      });
    }

    const existente = await cliente.query(
      `SELECT * FROM sustituciones_pedido
       WHERE pedido_detalle_id = $1 AND estado = 'SIN_STOCK'
       LIMIT 1`,
      [detalle.id]
    );
    if (existente.rows[0]) {
      await cliente.query('COMMIT');
      return res.status(200).json({
        idempotente: true,
        sustitucion: mapearSustitucion({ ...existente.rows[0], pedido_id: pedido.id })
      });
    }

    const precioOriginal = Number(detalle.precio_unitario_centimos);
    const cantidad = cantidadFaltanteDetalle(detalle);
    if (cantidad <= 0) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'No hay unidades faltantes para informar',
        codigo: 'SIN_FALTANTE'
      });
    }
    const { rows: [creada] } = await cliente.query(
      `INSERT INTO sustituciones_pedido (
         pedido_detalle_id, producto_original_id, producto_sustituto_id, picker_id,
         cantidad, precio_original_centimos, precio_sustituto_centimos, estado,
         fecha_propuesta, fecha_limite, fecha_respuesta, respuesta_automatica
       ) VALUES (
         $1, $2, NULL, $3, $4, $5, 0, 'SIN_STOCK', now(), now(), now(), false
       )
       RETURNING *`,
      [
        detalle.id,
        detalle.producto_id,
        req.usuario.id,
        cantidad,
        precioOriginal
      ]
    );

    await cliente.query('COMMIT');
    return res.status(201).json({
      idempotente: false,
      sustitucion: mapearSustitucion({ ...creada, pedido_id: pedido.id })
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({
        error: 'Ya se informó sin stock para esta línea',
        codigo: 'AVISO_SIN_STOCK_DUPLICADO'
      });
    }
    console.error('Error al avisar sin stock:', error.message);
    return res.status(500).json({ error: 'No se pudo informar sin stock al cliente' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  listarCandidatos,
  proponer,
  avisarSinStock,
  listarCliente,
  obtenerPorId,
  aceptar,
  rechazar,
  decidir,
  resolverExpiradasHttp,
  cancelarSustitucionesAbiertasDePedido,
  // pruebas
  resolverExpiradas,
  decidirInterno
};
