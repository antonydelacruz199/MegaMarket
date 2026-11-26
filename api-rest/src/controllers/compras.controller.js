const pool = require('../database');
const { paginar, metaPaginacion } = require('../paginacion');
const bancoAndinoSimulado = require('../banco/bancoAndinoSimulado');
const {
  esUuid,
  valor,
  hashSolicitud,
  uuidDerivado,
  puedeTransicionar,
  ESTADOS_ORDEN,
  mapearOrden,
  mapearTransferencia,
  buscarOperacionIdempotente,
  registrarOperacionIdempotente,
  generarNumeroOrden,
  cargarOrdenCompleta,
  respuestaOrdenCompleta
} = require('../abastecimiento');

function adaptadorBancario() {
  return bancoAndinoSimulado;
}

async function listarOrdenes(req, res) {
  try {
    const { page, limit, offset } = paginar(req);
    const estado = String(req.query.estado || '').trim().toUpperCase();
    const proveedorId = String(req.query.proveedorId || req.query.proveedor_id || '').trim();
    const params = [];
    const filtros = [];

    if (estado) {
      if (!ESTADOS_ORDEN.has(estado)) {
        return res.status(400).json({ error: 'estado de orden inválido' });
      }
      params.push(estado);
      filtros.push(`o.estado = $${params.length}`);
    }
    if (proveedorId) {
      if (!esUuid(proveedorId)) {
        return res.status(400).json({ error: 'proveedorId debe ser un UUID válido' });
      }
      params.push(proveedorId);
      filtros.push(`o.proveedor_id = $${params.length}`);
    }

    const where = filtros.length ? `WHERE ${filtros.join(' AND ')}` : '';
    const totalRes = await pool.query(
      `SELECT COUNT(*)::int AS total FROM ordenes_compra o ${where}`,
      params
    );
    const lista = await pool.query(
      `SELECT o.*, p.razon_social AS proveedor_razon_social, p.ruc AS proveedor_ruc
       FROM ordenes_compra o
       JOIN proveedores p ON p.id = o.proveedor_id
       ${where}
       ORDER BY o.created_at DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, limit, offset]
    );

    return res.status(200).json({
      ordenes: lista.rows.map((fila) =>
        mapearOrden(fila, {
          proveedor: {
            id: fila.proveedor_id,
            ruc: fila.proveedor_ruc,
            razonSocial: fila.proveedor_razon_social
          }
        })
      ),
      ...metaPaginacion(totalRes.rows[0].total, { page, limit })
    });
  } catch (error) {
    console.error('Error listando órdenes de compra:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar las órdenes de compra' });
  }
}

async function obtenerOrden(req, res) {
  const cliente = await pool.connect();
  try {
    const paquete = await cargarOrdenCompleta(cliente, req.params.id);
    if (!paquete) {
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }
    return res.status(200).json(respuestaOrdenCompleta(paquete));
  } catch (error) {
    console.error('Error obteniendo orden de compra:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener la orden de compra' });
  } finally {
    cliente.release();
  }
}

async function crearOrden(req, res) {
  const cliente = await pool.connect();
  try {
    const proveedorId = String(
      valor(req.body, 'proveedorId', 'proveedor_id', '') || ''
    ).trim();
    const itemsRaw = valor(req.body, 'items', 'detalles', null);

    if (!esUuid(proveedorId)) {
      return res.status(400).json({ error: 'proveedorId debe ser un UUID válido' });
    }
    if (!Array.isArray(itemsRaw) || itemsRaw.length === 0) {
      return res.status(400).json({ error: 'La orden debe incluir al menos un producto' });
    }

    const productosVistos = new Set();
    const items = [];
    for (const raw of itemsRaw) {
      const productoId = String(
        valor(raw, 'productoId', 'producto_id', '') || ''
      ).trim();
      const cantidad = Number(valor(raw, 'cantidad', 'cantidad', NaN));
      const precio = Number(
        valor(raw, 'precioUnitarioCentimos', 'precio_unitario_centimos', NaN)
      );

      if (!esUuid(productoId)) {
        return res.status(400).json({ error: 'Cada ítem requiere productoId UUID válido' });
      }
      if (productosVistos.has(productoId)) {
        return res.status(400).json({ error: 'No se admiten productos duplicados en la orden' });
      }
      productosVistos.add(productoId);
      if (!Number.isInteger(cantidad) || cantidad <= 0) {
        return res.status(400).json({ error: 'cantidad debe ser un entero positivo' });
      }
      if (!Number.isInteger(precio) || precio <= 0) {
        return res
          .status(400)
          .json({ error: 'precioUnitarioCentimos debe ser un entero positivo' });
      }
      items.push({
        productoId,
        cantidad,
        precioUnitarioCentimos: precio,
        subtotalCentimos: cantidad * precio
      });
    }

    const totalCentimos = items.reduce((acc, it) => acc + it.subtotalCentimos, 0);
    if (totalCentimos <= 0) {
      return res.status(400).json({ error: 'El total de la orden debe ser positivo' });
    }

    await cliente.query('BEGIN');

    const proveedor = await cliente.query(
      `SELECT * FROM proveedores WHERE id = $1 FOR UPDATE`,
      [proveedorId]
    );
    if (!proveedor.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Proveedor no encontrado' });
    }
    if (!proveedor.rows[0].activo) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'El proveedor no está activo' });
    }

    const idsProductos = items.map((it) => it.productoId).sort();
    const productos = await cliente.query(
      `SELECT id FROM productos
       WHERE id = ANY($1::uuid[]) AND deleted_at IS NULL
       FOR UPDATE`,
      [idsProductos]
    );
    if (productos.rows.length !== idsProductos.length) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Uno o más productos no existen' });
    }

    const numeroOrden = await generarNumeroOrden(cliente);
    const ordenIns = await cliente.query(
      `INSERT INTO ordenes_compra (
         proveedor_id, numero_orden, estado, total_centimos
       ) VALUES ($1, $2, 'SUGERIDA', $3)
       RETURNING *`,
      [proveedorId, numeroOrden, totalCentimos]
    );
    const orden = ordenIns.rows[0];

    for (const it of items) {
      await cliente.query(
        `INSERT INTO orden_compra_detalle (
           orden_compra_id, producto_id, cantidad,
           precio_unitario_centimos, subtotal_centimos, cantidad_recibida
         ) VALUES ($1, $2, $3, $4, $5, 0)`,
        [
          orden.id,
          it.productoId,
          it.cantidad,
          it.precioUnitarioCentimos,
          it.subtotalCentimos
        ]
      );
    }

    const paquete = await cargarOrdenCompleta(cliente, orden.id);
    await cliente.query('COMMIT');
    return res.status(201).json(respuestaOrdenCompleta(paquete));
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({ error: 'Conflicto al crear la orden (número o producto duplicado)' });
    }
    console.error('Error creando orden de compra:', error.message);
    return res.status(500).json({ error: 'No se pudo crear la orden de compra' });
  } finally {
    cliente.release();
  }
}

async function aprobarOrden(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = String(
      valor(req.body, 'uuidOperacion', 'uuid_operacion', '') || ''
    ).trim();
    if (uuidOperacion && !esUuid(uuidOperacion)) {
      return res.status(400).json({ error: 'uuidOperacion debe ser un UUID válido' });
    }

    await cliente.query('BEGIN');

    if (uuidOperacion) {
      const previa = await buscarOperacionIdempotente(cliente, uuidOperacion);
      if (previa) {
        await cliente.query('COMMIT');
        return res.status(previa.codigo_http || 200).json(previa.respuesta);
      }
    }

    const ordenRes = await cliente.query(
      `SELECT * FROM ordenes_compra WHERE id = $1 FOR UPDATE`,
      [req.params.id]
    );
    const orden = ordenRes.rows[0];
    if (!orden) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }

    if (orden.estado === 'APROBADA' && orden.administrador_id === req.usuario.id) {
      const paquete = await cargarOrdenCompleta(cliente, orden.id);
      const respuesta = {
        ...respuestaOrdenCompleta(paquete),
        idempotente: true
      };
      if (uuidOperacion) {
        await registrarOperacionIdempotente(cliente, {
          uuidOperacion,
          usuarioId: req.usuario.id,
          tipoOperacion: 'APROBAR_ORDEN_COMPRA',
          recurso: 'ORDEN_COMPRA',
          entidadId: orden.id,
          codigoHttp: 200,
          respuesta
        });
      }
      await cliente.query('COMMIT');
      return res.status(200).json(respuesta);
    }

    if (!puedeTransicionar(orden.estado, 'APROBADA')) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: `No se puede aprobar una orden en estado ${orden.estado}`
      });
    }

    const detalles = await cliente.query(
      `SELECT * FROM orden_compra_detalle WHERE orden_compra_id = $1`,
      [orden.id]
    );
    if (!detalles.rows.length) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'La orden no tiene detalles para aprobar' });
    }

    const totalCalc = detalles.rows.reduce(
      (acc, d) => acc + Number(d.subtotal_centimos),
      0
    );
    if (totalCalc <= 0 || totalCalc !== Number(orden.total_centimos)) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'El total de la orden es inconsistente' });
    }

    await cliente.query(
      `UPDATE ordenes_compra
       SET estado = 'APROBADA',
           administrador_id = $1,
           fecha_aprobacion = now(),
           updated_at = now()
       WHERE id = $2`,
      [req.usuario.id, orden.id]
    );

    const paquete = await cargarOrdenCompleta(cliente, orden.id);
    const respuesta = {
      ...respuestaOrdenCompleta(paquete),
      idempotente: false
    };

    if (uuidOperacion) {
      await registrarOperacionIdempotente(cliente, {
        uuidOperacion,
        usuarioId: req.usuario.id,
        tipoOperacion: 'APROBAR_ORDEN_COMPRA',
        recurso: 'ORDEN_COMPRA',
        entidadId: orden.id,
        codigoHttp: 200,
        respuesta
      });
    }

    await cliente.query('COMMIT');
    return res.status(200).json(respuesta);
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505' && req.body) {
      const uuidOperacion = String(
        valor(req.body, 'uuidOperacion', 'uuid_operacion', '') || ''
      ).trim();
      if (uuidOperacion) {
        const previa = await pool.query(
          `SELECT codigo_http, respuesta FROM operaciones_idempotentes
           WHERE uuid_operacion = $1 LIMIT 1`,
          [uuidOperacion]
        );
        if (previa.rows[0]) {
          return res
            .status(previa.rows[0].codigo_http || 200)
            .json(previa.rows[0].respuesta);
        }
      }
    }
    console.error('Error aprobando orden:', error.message);
    return res.status(500).json({ error: 'No se pudo aprobar la orden de compra' });
  } finally {
    cliente.release();
  }
}

async function cancelarOrden(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const ordenRes = await cliente.query(
      `SELECT * FROM ordenes_compra WHERE id = $1 FOR UPDATE`,
      [req.params.id]
    );
    const orden = ordenRes.rows[0];
    if (!orden) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }
    if (!puedeTransicionar(orden.estado, 'CANCELADA')) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: `No se puede cancelar una orden en estado ${orden.estado}`
      });
    }

    await cliente.query(
      `UPDATE ordenes_compra
       SET estado = 'CANCELADA', updated_at = now()
       WHERE id = $1`,
      [orden.id]
    );
    const paquete = await cargarOrdenCompleta(cliente, orden.id);
    await cliente.query('COMMIT');
    return res.status(200).json(respuestaOrdenCompleta(paquete));
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error cancelando orden:', error.message);
    return res.status(500).json({ error: 'No se pudo cancelar la orden de compra' });
  } finally {
    cliente.release();
  }
}

/**
 * Pago a proveedor.
 * La llamada al adaptador bancario ocurre FUERA de la transacción abierta.
 */
async function pagarOrden(req, res) {
  const uuidOperacion = String(
    valor(req.body, 'uuidOperacion', 'uuid_operacion', '') || ''
  ).trim();
  if (!esUuid(uuidOperacion)) {
    return res.status(400).json({ error: 'uuidOperacion es obligatorio y debe ser UUID' });
  }

  const forzarResultado = valor(req.body, 'forzarResultado', 'forzar_resultado', undefined);
  let transferenciaId = null;
  let contextoPago = null;

  const clientePrep = await pool.connect();
  try {
    await clientePrep.query('BEGIN');

    const transferenciaExistente = await clientePrep.query(
      `SELECT * FROM transferencias_proveedor WHERE uuid_operacion = $1 FOR UPDATE`,
      [uuidOperacion]
    );
    if (transferenciaExistente.rows[0]) {
      const t = transferenciaExistente.rows[0];
      const montoBody = valor(req.body, 'montoCentimos', 'monto_centimos', undefined);
      const montoParaHash =
        montoBody === undefined || montoBody === null || montoBody === ''
          ? Number(t.monto_centimos)
          : Number(montoBody);
      const hashActual = hashSolicitud({
        ordenCompraId: String(req.params.id),
        montoCentimos: montoParaHash,
        uuidOperacion
      });
      if (String(t.orden_compra_id) !== String(req.params.id) || t.solicitud_hash !== hashActual) {
        await clientePrep.query('ROLLBACK');
        return res.status(409).json({
          error: 'uuidOperacion ya usado con un payload diferente'
        });
      }

      const previaOp = await buscarOperacionIdempotente(clientePrep, uuidOperacion);
      if (previaOp) {
        await clientePrep.query('COMMIT');
        return res.status(previaOp.codigo_http || 200).json({
          ...previaOp.respuesta,
          idempotente: true
        });
      }

      const paquete = await cargarOrdenCompleta(clientePrep, t.orden_compra_id);
      const respuesta = {
        ...respuestaOrdenCompleta(paquete),
        transferencia: mapearTransferencia(t),
        idempotente: true,
        simulacion: true
      };
      const codigo = t.estado === 'CONFIRMADA' ? 200 : 409;
      await registrarOperacionIdempotente(clientePrep, {
        uuidOperacion,
        usuarioId: req.usuario.id,
        tipoOperacion: 'PAGO_ORDEN_COMPRA',
        recurso: 'TRANSFERENCIA_PROVEEDOR',
        entidadId: t.id,
        codigoHttp: codigo,
        respuesta
      });
      await clientePrep.query('COMMIT');
      return res.status(codigo).json(respuesta);
    }

    const previaOp = await buscarOperacionIdempotente(clientePrep, uuidOperacion);
    if (previaOp) {
      await clientePrep.query('COMMIT');
      return res.status(previaOp.codigo_http || 200).json(previaOp.respuesta);
    }

    const ordenRes = await clientePrep.query(
      `SELECT * FROM ordenes_compra WHERE id = $1 FOR UPDATE`,
      [req.params.id]
    );
    const orden = ordenRes.rows[0];
    if (!orden) {
      await clientePrep.query('ROLLBACK');
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }

    if (orden.estado === 'PAGADA' || orden.estado === 'RECIBIDA') {
      await clientePrep.query('ROLLBACK');
      return res.status(409).json({
        error: `La orden ya está ${orden.estado}; no se emite otra transferencia`
      });
    }

    if (!puedeTransicionar(orden.estado, 'PAGADA')) {
      await clientePrep.query('ROLLBACK');
      return res.status(409).json({
        error: `No se puede pagar una orden en estado ${orden.estado}. Requiere aprobación previa.`
      });
    }

    const abierta = await clientePrep.query(
      `SELECT id, uuid_operacion, estado FROM transferencias_proveedor
       WHERE orden_compra_id = $1 AND estado IN ('PENDIENTE', 'DESCONOCIDA')
       ORDER BY created_at DESC
       LIMIT 1`,
      [orden.id]
    );
    if (abierta.rows[0] && String(abierta.rows[0].uuid_operacion) !== uuidOperacion) {
      await clientePrep.query('ROLLBACK');
      return res.status(409).json({
        error:
          'Existe una transferencia pendiente o con resultado desconocido. No emita otra automáticamente; consulte el uuid original.',
        transferenciaId: abierta.rows[0].id,
        uuidOperacionExistente: abierta.rows[0].uuid_operacion,
        estadoTransferencia: abierta.rows[0].estado,
        simulacion: true
      });
    }

    const proveedorRes = await clientePrep.query(
      `SELECT * FROM proveedores WHERE id = $1 FOR UPDATE`,
      [orden.proveedor_id]
    );
    const proveedor = proveedorRes.rows[0];
    if (!proveedor) {
      await clientePrep.query('ROLLBACK');
      return res.status(404).json({ error: 'Proveedor no encontrado' });
    }
    if (!proveedor.cuenta_bancaria || !String(proveedor.cuenta_bancaria).trim()) {
      await clientePrep.query('ROLLBACK');
      return res.status(409).json({
        error: 'El proveedor no tiene cuenta bancaria registrada'
      });
    }

    const montoBody = valor(req.body, 'montoCentimos', 'monto_centimos', undefined);
    const montoCentimos =
      montoBody === undefined || montoBody === null || montoBody === ''
        ? Number(orden.total_centimos)
        : Number(montoBody);
    if (!Number.isInteger(montoCentimos) || montoCentimos <= 0) {
      await clientePrep.query('ROLLBACK');
      return res.status(400).json({ error: 'montoCentimos debe ser un entero positivo' });
    }
    if (montoCentimos !== Number(orden.total_centimos)) {
      await clientePrep.query('ROLLBACK');
      return res.status(409).json({
        error: 'El monto no coincide con el total de la orden'
      });
    }

    const solicitudHash = hashSolicitud({
      ordenCompraId: String(orden.id),
      montoCentimos,
      uuidOperacion
    });

    const insertada = await clientePrep.query(
      `INSERT INTO transferencias_proveedor (
         uuid_operacion, orden_compra_id, administrador_id, monto_centimos,
         cuenta_destino, solicitud_hash, estado, simulacion, adaptador, mensaje
       ) VALUES ($1, $2, $3, $4, $5, $6, 'PENDIENTE', true, $7, $8)
       RETURNING *`,
      [
        uuidOperacion,
        orden.id,
        req.usuario.id,
        montoCentimos,
        proveedor.cuenta_bancaria,
        solicitudHash,
        adaptadorBancario().nombre,
        'Solicitud registrada. Transferencia pendiente de resultado.'
      ]
    );

    transferenciaId = insertada.rows[0].id;
    contextoPago = {
      orden,
      proveedor,
      montoCentimos,
      transferencia: insertada.rows[0]
    };
    await clientePrep.query('COMMIT');
  } catch (error) {
    try {
      await clientePrep.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      const previa = await pool.query(
        `SELECT codigo_http, respuesta FROM operaciones_idempotentes
         WHERE uuid_operacion = $1 LIMIT 1`,
        [uuidOperacion]
      );
      if (previa.rows[0]) {
        return res.status(previa.rows[0].codigo_http || 200).json(previa.rows[0].respuesta);
      }
      const t = await pool.query(
        `SELECT * FROM transferencias_proveedor WHERE uuid_operacion = $1 LIMIT 1`,
        [uuidOperacion]
      );
      if (t.rows[0]) {
        return res.status(409).json({
          error: 'uuidOperacion en conflicto con otra transferencia concurrente'
        });
      }
    }
    console.error('Error preparando pago a proveedor:', error.message);
    return res.status(500).json({ error: 'No se pudo iniciar el pago a proveedor' });
  } finally {
    clientePrep.release();
  }

  // Llamada bancaria FUERA de cualquier transacción abierta.
  let resultadoBanco;
  try {
    resultadoBanco = await adaptadorBancario().transferir({
      uuidOperacion,
      ordenCompraId: contextoPago.orden.id,
      numeroOrden: contextoPago.orden.numero_orden,
      montoCentimos: contextoPago.montoCentimos,
      rucProveedor: contextoPago.proveedor.ruc,
      razonSocial: contextoPago.proveedor.razon_social,
      cuentaDestino: contextoPago.proveedor.cuenta_bancaria,
      forzarResultado
    });
  } catch (error) {
    resultadoBanco = {
      estado: 'DESCONOCIDA',
      referenciaBanco: null,
      numeroOperacionBanco: null,
      mensaje:
        'El adaptador bancario no respondió. Resultado desconocido; no reintentar automáticamente.',
      simulacion: true,
      adaptador: adaptadorBancario().nombre
    };
    console.error('Adaptador bancario falló:', error.message);
  }

  const clienteFin = await pool.connect();
  try {
    await clienteFin.query('BEGIN');

    const transferenciaRes = await clienteFin.query(
      `SELECT * FROM transferencias_proveedor WHERE id = $1 FOR UPDATE`,
      [transferenciaId]
    );
    let transferencia = transferenciaRes.rows[0];
    if (!transferencia) {
      await clienteFin.query('ROLLBACK');
      return res.status(500).json({ error: 'Transferencia no encontrada tras el intento' });
    }

    // Si ya fue confirmada por una carrera, devolver idempotente.
    if (transferencia.estado === 'CONFIRMADA') {
      const paquete = await cargarOrdenCompleta(clienteFin, transferencia.orden_compra_id);
      const respuesta = {
        ...respuestaOrdenCompleta(paquete),
        transferencia: mapearTransferencia(transferencia),
        idempotente: true,
        simulacion: true
      };
      await clienteFin.query('COMMIT');
      return res.status(200).json(respuesta);
    }

    const ordenRes = await clienteFin.query(
      `SELECT * FROM ordenes_compra WHERE id = $1 FOR UPDATE`,
      [transferencia.orden_compra_id]
    );
    const orden = ordenRes.rows[0];

    const estadoFinal = resultadoBanco.estado;
    await clienteFin.query(
      `UPDATE transferencias_proveedor
       SET estado = $1,
           referencia_banco = $2,
           numero_operacion_banco = $3,
           mensaje = $4,
           fecha_respuesta = now(),
           updated_at = now()
       WHERE id = $5`,
      [
        estadoFinal,
        resultadoBanco.referenciaBanco,
        resultadoBanco.numeroOperacionBanco,
        resultadoBanco.mensaje,
        transferencia.id
      ]
    );

    if (estadoFinal === 'CONFIRMADA') {
      if (orden.estado !== 'APROBADA' && orden.estado !== 'PAGADA') {
        await clienteFin.query('ROLLBACK');
        return res.status(409).json({
          error: `Estado de orden incompatible tras confirmación: ${orden.estado}`
        });
      }
      if (orden.estado === 'APROBADA') {
        await clienteFin.query(
          `UPDATE ordenes_compra
           SET estado = 'PAGADA',
               numero_operacion_banco = $1,
               fecha_pago = now(),
               updated_at = now()
           WHERE id = $2`,
          [resultadoBanco.numeroOperacionBanco, orden.id]
        );
      }
    }

    transferencia = (
      await clienteFin.query(
        `SELECT * FROM transferencias_proveedor WHERE id = $1`,
        [transferencia.id]
      )
    ).rows[0];

    const paquete = await cargarOrdenCompleta(clienteFin, orden.id);
    const codigoHttp = estadoFinal === 'CONFIRMADA' ? 200 : 409;
    const respuesta = {
      ...respuestaOrdenCompleta(paquete),
      transferencia: mapearTransferencia(transferencia),
      idempotente: false,
      simulacion: true
    };
    if (estadoFinal !== 'CONFIRMADA') {
      respuesta.error =
        estadoFinal === 'RECHAZADA'
          ? 'La transferencia fue rechazada'
          : estadoFinal === 'PENDIENTE'
            ? 'La transferencia quedó pendiente'
            : 'Resultado de transferencia desconocido; no se marcó la orden como PAGADA';
    }

    await registrarOperacionIdempotente(clienteFin, {
      uuidOperacion,
      usuarioId: req.usuario.id,
      tipoOperacion: 'PAGO_ORDEN_COMPRA',
      recurso: 'TRANSFERENCIA_PROVEEDOR',
      entidadId: transferencia.id,
      codigoHttp,
      respuesta
    });

    await clienteFin.query('COMMIT');
    return res.status(codigoHttp).json(respuesta);
  } catch (error) {
    try {
      await clienteFin.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error finalizando pago a proveedor:', error.message);
    return res.status(500).json({
      error: 'No se pudo registrar el resultado de la transferencia',
      simulacion: true
    });
  } finally {
    clienteFin.release();
  }
}

async function consultarTransferencia(req, res) {
  try {
    const uuidOperacion = String(req.params.uuidOperacion || '').trim();
    if (!esUuid(uuidOperacion)) {
      return res.status(400).json({ error: 'uuidOperacion inválido' });
    }
    const resultado = await pool.query(
      `SELECT * FROM transferencias_proveedor WHERE uuid_operacion = $1 LIMIT 1`,
      [uuidOperacion]
    );
    if (!resultado.rows[0]) {
      return res.status(404).json({ error: 'Transferencia no encontrada' });
    }
    return res.status(200).json({
      transferencia: mapearTransferencia(resultado.rows[0]),
      simulacion: true
    });
  } catch (error) {
    console.error('Error consultando transferencia:', error.message);
    return res.status(500).json({ error: 'No se pudo consultar la transferencia' });
  }
}

async function registrarRecepcion(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = String(
      valor(req.body, 'uuidOperacion', 'uuid_operacion', '') || ''
    ).trim();
    if (!esUuid(uuidOperacion)) {
      return res.status(400).json({ error: 'uuidOperacion es obligatorio y debe ser UUID' });
    }

    const itemsRaw = valor(req.body, 'items', 'recepciones', null);
    if (!Array.isArray(itemsRaw) || itemsRaw.length === 0) {
      return res.status(400).json({ error: 'Debe enviar al menos un ítem de recepción' });
    }

    const items = [];
    const detallesVistos = new Set();
    for (const raw of itemsRaw) {
      const detalleId = String(valor(raw, 'detalleId', 'detalle_id', '') || '').trim();
      const cantidad = Number(valor(raw, 'cantidad', 'cantidad', NaN));
      if (!esUuid(detalleId)) {
        return res.status(400).json({ error: 'detalleId debe ser un UUID válido' });
      }
      if (detallesVistos.has(detalleId)) {
        return res.status(400).json({ error: 'detalleId duplicado en la solicitud' });
      }
      detallesVistos.add(detalleId);
      if (!Number.isInteger(cantidad) || cantidad <= 0) {
        return res.status(400).json({ error: 'cantidad de recepción debe ser un entero positivo' });
      }
      items.push({ detalleId, cantidad });
    }

    await cliente.query('BEGIN');

    const previa = await buscarOperacionIdempotente(cliente, uuidOperacion);
    if (previa) {
      await cliente.query('COMMIT');
      return res.status(previa.codigo_http || 200).json(previa.respuesta);
    }

    const ordenRes = await cliente.query(
      `SELECT * FROM ordenes_compra WHERE id = $1 FOR UPDATE`,
      [req.params.id]
    );
    const orden = ordenRes.rows[0];
    if (!orden) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }
    if (orden.estado !== 'PAGADA' && orden.estado !== 'RECIBIDA') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se puede recibir mercadería de órdenes PAGADA'
      });
    }
    if (orden.estado === 'RECIBIDA') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'La orden ya fue recibida completamente' });
    }

    // Bloqueo determinista de detalles.
    const idsDetalle = items.map((it) => it.detalleId).sort();
    const detallesRes = await cliente.query(
      `SELECT * FROM orden_compra_detalle
       WHERE orden_compra_id = $1 AND id = ANY($2::uuid[])
       ORDER BY id ASC
       FOR UPDATE`,
      [orden.id, idsDetalle]
    );
    if (detallesRes.rows.length !== idsDetalle.length) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({
        error: 'Uno o más detalles no pertenecen a esta orden'
      });
    }

    const detallePorId = new Map(detallesRes.rows.map((d) => [d.id, d]));
    const idsProducto = [...new Set(detallesRes.rows.map((d) => d.producto_id))].sort();
    const productosRes = await cliente.query(
      `SELECT id, stock, version FROM productos
       WHERE id = ANY($1::uuid[]) AND deleted_at IS NULL
       ORDER BY id ASC
       FOR UPDATE`,
      [idsProducto]
    );
    if (productosRes.rows.length !== idsProducto.length) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Producto asociado no encontrado' });
    }
    const productoPorId = new Map(productosRes.rows.map((p) => [p.id, p]));

    const movimientos = [];
    for (const it of items) {
      const detalle = detallePorId.get(it.detalleId);
      const pendiente = Number(detalle.cantidad) - Number(detalle.cantidad_recibida);
      if (it.cantidad > pendiente) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: `Cantidad excesiva para el detalle ${detalle.id}. Pendiente: ${pendiente}`
        });
      }

      const uuidMovimiento = uuidDerivado(uuidOperacion, detalle.id);
      const movimientoPrevio = await cliente.query(
        `SELECT id FROM movimientos_inventario
         WHERE uuid_operacion = $1 AND deleted_at IS NULL
         LIMIT 1`,
        [uuidMovimiento]
      );
      if (movimientoPrevio.rows[0]) {
        await cliente.query('ROLLBACK');
        return res.status(409).json({
          error: 'Conflicto de idempotencia en movimiento de recepción'
        });
      }

      const producto = productoPorId.get(detalle.producto_id);
      const stockAnterior = Number(producto.stock);
      const stockFinal = stockAnterior + it.cantidad;

      await cliente.query(
        `UPDATE productos SET stock = $1 WHERE id = $2`,
        [stockFinal, producto.id]
      );
      producto.stock = stockFinal;

      await cliente.query(
        `UPDATE orden_compra_detalle
         SET cantidad_recibida = cantidad_recibida + $1
         WHERE id = $2`,
        [it.cantidad, detalle.id]
      );
      detalle.cantidad_recibida = Number(detalle.cantidad_recibida) + it.cantidad;

      const mov = await cliente.query(
        `INSERT INTO movimientos_inventario (
           uuid_operacion, producto_id, pedido_id, usuario_id, tipo, cantidad,
           orden_compra_detalle_id
         ) VALUES ($1, $2, NULL, $3, 'ENTRADA_COMPRA', $4, $5)
         RETURNING id, uuid_operacion, producto_id, usuario_id, tipo, cantidad,
                   orden_compra_detalle_id, version, created_at, updated_at`,
        [uuidMovimiento, detalle.producto_id, req.usuario.id, it.cantidad, detalle.id]
      );
      movimientos.push({
        ...mov.rows[0],
        stockAnterior,
        stockFinal
      });
    }

    const pendientes = await cliente.query(
      `SELECT COUNT(*)::int AS n
       FROM orden_compra_detalle
       WHERE orden_compra_id = $1 AND cantidad_recibida < cantidad`,
      [orden.id]
    );
    let estadoFinal = 'PAGADA';
    if (pendientes.rows[0].n === 0) {
      estadoFinal = 'RECIBIDA';
      await cliente.query(
        `UPDATE ordenes_compra
         SET estado = 'RECIBIDA',
             fecha_recepcion = now(),
             updated_at = now()
         WHERE id = $1`,
        [orden.id]
      );
    } else {
      await cliente.query(
        `UPDATE ordenes_compra SET updated_at = now() WHERE id = $1`,
        [orden.id]
      );
    }

    const paquete = await cargarOrdenCompleta(cliente, orden.id);
    const respuesta = {
      ...respuestaOrdenCompleta(paquete),
      recepcionCompleta: estadoFinal === 'RECIBIDA',
      movimientos: movimientos.map((m) => ({
        id: m.id,
        uuidOperacion: m.uuid_operacion,
        productoId: m.producto_id,
        tipo: m.tipo,
        cantidad: Number(m.cantidad),
        ordenCompraDetalleId: m.orden_compra_detalle_id,
        stockAnterior: m.stockAnterior,
        stockFinal: m.stockFinal,
        createdAt: m.created_at
      })),
      idempotente: false
    };

    await registrarOperacionIdempotente(cliente, {
      uuidOperacion,
      usuarioId: req.usuario.id,
      tipoOperacion: 'RECEPCION_ORDEN_COMPRA',
      recurso: 'ORDEN_COMPRA',
      entidadId: orden.id,
      codigoHttp: 201,
      respuesta
    });

    await cliente.query('COMMIT');
    return res.status(201).json(respuesta);
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      const uuidOperacion = String(
        valor(req.body, 'uuidOperacion', 'uuid_operacion', '') || ''
      ).trim();
      const previa = await pool.query(
        `SELECT codigo_http, respuesta FROM operaciones_idempotentes
         WHERE uuid_operacion = $1 LIMIT 1`,
        [uuidOperacion]
      );
      if (previa.rows[0]) {
        return res.status(previa.rows[0].codigo_http || 200).json({
          ...previa.rows[0].respuesta,
          idempotente: true
        });
      }
    }
    console.error('Error registrando recepción:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar la recepción' });
  } finally {
    cliente.release();
  }
}

async function historialRecepciones(req, res) {
  try {
    const ordenId = req.params.id;
    const orden = await pool.query(
      `SELECT id FROM ordenes_compra WHERE id = $1 LIMIT 1`,
      [ordenId]
    );
    if (!orden.rows[0]) {
      return res.status(404).json({ error: 'Orden de compra no encontrada' });
    }

    const movimientos = await pool.query(
      `SELECT m.*, d.producto_id AS detalle_producto_id, p.nombre AS nombre_producto
       FROM movimientos_inventario m
       JOIN orden_compra_detalle d ON d.id = m.orden_compra_detalle_id
       JOIN productos p ON p.id = m.producto_id
       WHERE d.orden_compra_id = $1
         AND m.tipo = 'ENTRADA_COMPRA'
         AND m.deleted_at IS NULL
       ORDER BY m.created_at ASC`,
      [ordenId]
    );

    return res.status(200).json({
      movimientos: movimientos.rows.map((m) => ({
        id: m.id,
        uuidOperacion: m.uuid_operacion,
        productoId: m.producto_id,
        nombreProducto: m.nombre_producto,
        tipo: m.tipo,
        cantidad: Number(m.cantidad),
        ordenCompraDetalleId: m.orden_compra_detalle_id,
        createdAt: m.created_at
      }))
    });
  } catch (error) {
    console.error('Error listando recepciones:', error.message);
    return res.status(500).json({ error: 'No se pudo listar el historial de recepción' });
  }
}

module.exports = {
  listarOrdenes,
  obtenerOrden,
  crearOrden,
  aprobarOrden,
  cancelarOrden,
  pagarOrden,
  consultarTransferencia,
  registrarRecepcion,
  historialRecepciones
};
