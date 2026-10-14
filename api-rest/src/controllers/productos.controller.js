const pool = require('../database');

function mapearProducto(fila) {
  return {
    id: fila.id,
    nombre: fila.nombre,
    marca: fila.marca,
    descripcion: fila.descripcion,
    categoriaId: fila.categoria_id,
    precioCentimos: Number(fila.precio_centimos),
    precioOfertaCentimos:
      fila.precio_oferta_centimos === null || fila.precio_oferta_centimos === undefined
        ? null
        : Number(fila.precio_oferta_centimos),
    stock: Number(fila.stock),
    imagenKey: fila.imagen_key == null || fila.imagen_key === '' ? null : fila.imagen_key,
    esOferta: Boolean(fila.es_oferta),
    activo: Boolean(fila.activo),
    version: Number(fila.version),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function valor(body, camel, snake, defecto) {
  if (body[camel] !== undefined) return body[camel];
  if (body[snake] !== undefined) return body[snake];
  return defecto;
}

function normalizarOferta(esOferta, precioOfertaCentimos) {
  if (!esOferta) return null;
  if (precioOfertaCentimos === null || precioOfertaCentimos === undefined || precioOfertaCentimos === '') {
    return undefined;
  }
  return Number(precioOfertaCentimos);
}

function validarCoherenciaOferta(precioCentimos, precioOfertaCentimos, esOferta) {
  if (!esOferta) {
    if (precioOfertaCentimos !== null && precioOfertaCentimos !== undefined && precioOfertaCentimos !== '') {
      return 'precio_oferta_centimos debe ser null cuando es_oferta es false';
    }
    return null;
  }
  if (precioOfertaCentimos === null || precioOfertaCentimos === undefined || precioOfertaCentimos === '') {
    return 'precio_oferta_centimos es obligatorio cuando es_oferta es true';
  }
  const oferta = Number(precioOfertaCentimos);
  if (!Number.isFinite(oferta) || oferta <= 0) {
    return 'precio_oferta_centimos debe ser > 0';
  }
  if (oferta >= Number(precioCentimos)) {
    return 'precio_oferta_centimos debe ser menor que precio_centimos';
  }
  return null;
}

async function buscarOperacionIdempotente(cliente, uuidOperacion) {
  if (!uuidOperacion) return null;
  const consulta = await cliente.query(
    `SELECT codigo_http, respuesta
     FROM operaciones_idempotentes
     WHERE uuid_operacion = $1
     LIMIT 1`,
    [uuidOperacion]
  );
  return consulta.rows[0] || null;
}

async function registrarOperacionIdempotente(
  cliente,
  { uuidOperacion, usuarioId, tipoOperacion, recurso, entidadId, codigoHttp, respuesta }
) {
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
     ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb)`,
    [
      uuidOperacion,
      usuarioId,
      tipoOperacion,
      recurso,
      entidadId,
      'PROCESADA',
      codigoHttp,
      JSON.stringify(respuesta)
    ]
  );
}

function validarCamposCreacion(body) {
  const errores = [];
  if (!String(body.nombre || '').trim()) errores.push('nombre es obligatorio');
  if (!String(body.marca || '').trim()) errores.push('marca es obligatoria');

  const categoriaId = valor(body, 'categoriaId', 'categoria_id', null);
  if (!categoriaId) errores.push('categoria_id es obligatorio');

  const precio = Number(valor(body, 'precioCentimos', 'precio_centimos', NaN));
  if (!Number.isFinite(precio) || precio <= 0) errores.push('precio_centimos debe ser > 0');

  const esOferta = valor(body, 'esOferta', 'es_oferta', undefined);
  if (typeof esOferta !== 'boolean') errores.push('es_oferta debe ser boolean');

  if (typeof body.activo !== 'boolean') errores.push('activo debe ser boolean');

  const stock = Number(body.stock);
  if (!Number.isInteger(stock) || stock < 0) errores.push('stock debe ser un entero >= 0');

  if (typeof esOferta === 'boolean') {
    const oferta = valor(body, 'precioOfertaCentimos', 'precio_oferta_centimos', null);
    const errorOferta = validarCoherenciaOferta(precio, oferta, esOferta);
    if (errorOferta) errores.push(errorOferta);
  }

  return errores;
}

async function listar(req, res) {
  try {
    const resultado = await pool.query(
      `SELECT id, nombre, marca, descripcion, categoria_id,
              precio_centimos, precio_oferta_centimos, stock, imagen_key,
              es_oferta, activo, version, created_at, updated_at
       FROM productos
       WHERE deleted_at IS NULL AND activo = true
       ORDER BY nombre ASC`
    );
    return res.status(200).json({ productos: resultado.rows.map(mapearProducto) });
  } catch (error) {
    console.error('Error al listar productos:', error.message);
    return res.status(500).json({ error: 'No se pudieron obtener los productos' });
  }
}

async function obtenerPorId(req, res) {
  try {
    const resultado = await pool.query(
      `SELECT id, nombre, marca, descripcion, categoria_id,
              precio_centimos, precio_oferta_centimos, stock, imagen_key,
              es_oferta, activo, version, created_at, updated_at
       FROM productos
       WHERE id = $1 AND deleted_at IS NULL AND activo = true
       LIMIT 1`,
      [req.params.id]
    );
    if (!resultado.rows[0]) {
      return res.status(404).json({ error: 'Producto no encontrado' });
    }
    return res.status(200).json({ producto: mapearProducto(resultado.rows[0]) });
  } catch (error) {
    console.error('Error al obtener producto:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el producto' });
  }
}

async function crear(req, res) {
  const cliente = await pool.connect();
  try {
    const errores = validarCamposCreacion(req.body);
    if (errores.length > 0) {
      return res.status(400).json({ error: errores.join('; ') });
    }

    const uuidOperacion = valor(req.body, 'uuidOperacion', 'uuid_operacion', null);
    const nombre = String(req.body.nombre).trim();
    const marca = String(req.body.marca).trim();
    const descripcion = String(req.body.descripcion || '').trim();
    const categoriaId = valor(req.body, 'categoriaId', 'categoria_id', null);
    const precioCentimos = Number(valor(req.body, 'precioCentimos', 'precio_centimos', 0));
    const esOferta = Boolean(valor(req.body, 'esOferta', 'es_oferta', false));
    const precioOfertaCentimos = normalizarOferta(
      esOferta,
      valor(req.body, 'precioOfertaCentimos', 'precio_oferta_centimos', null)
    );
    const stock = Number(req.body.stock);
    const imagenKeyRaw = valor(req.body, 'imagenKey', 'imagen_key', null);
    const imagenKey =
      imagenKeyRaw === null || imagenKeyRaw === undefined || imagenKeyRaw === ''
        ? null
        : String(imagenKeyRaw);
    const activo = Boolean(req.body.activo);

    await cliente.query('BEGIN');

    if (uuidOperacion) {
      const operacionAnterior = await buscarOperacionIdempotente(cliente, uuidOperacion);
      if (operacionAnterior) {
        await cliente.query('COMMIT');
        return res
          .status(operacionAnterior.codigo_http || 200)
          .json(operacionAnterior.respuesta);
      }
    }

    const categoria = await cliente.query(
      `SELECT id FROM categorias WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [categoriaId]
    );
    if (!categoria.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: 'categoria_id no existe' });
    }

    const insertado = await cliente.query(
      `INSERT INTO productos (
         nombre,
         marca,
         descripcion,
         categoria_id,
         precio_centimos,
         precio_oferta_centimos,
         stock,
         imagen_key,
         es_oferta,
         activo
       ) VALUES (
         $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
       )
       RETURNING id, nombre, marca, descripcion, categoria_id,
                 precio_centimos, precio_oferta_centimos, stock, imagen_key,
                 es_oferta, activo, version, created_at, updated_at`,
      [
        nombre,
        marca,
        descripcion,
        categoriaId,
        precioCentimos,
        precioOfertaCentimos,
        stock,
        imagenKey,
        esOferta,
        activo
      ]
    );

    const respuesta = { producto: mapearProducto(insertado.rows[0]) };

    if (uuidOperacion) {
      await registrarOperacionIdempotente(cliente, {
        uuidOperacion,
        usuarioId: req.usuario.id,
        tipoOperacion: 'CREAR_PRODUCTO',
        recurso: 'PRODUCTO',
        entidadId: insertado.rows[0].id,
        codigoHttp: 201,
        respuesta
      });
    }

    await cliente.query('COMMIT');
    return res.status(201).json(respuesta);
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al crear producto:', error.message);
    return res.status(500).json({ error: 'No se pudo crear el producto' });
  } finally {
    cliente.release();
  }
}

async function actualizar(req, res) {
  const cliente = await pool.connect();
  try {
    if (req.body.version === undefined || req.body.version === null) {
      return res.status(400).json({ error: 'version es obligatoria para actualizar' });
    }

    const uuidOperacion = valor(req.body, 'uuidOperacion', 'uuid_operacion', null);

    await cliente.query('BEGIN');

    if (uuidOperacion) {
      const operacionAnterior = await buscarOperacionIdempotente(cliente, uuidOperacion);
      if (operacionAnterior) {
        await cliente.query('COMMIT');
        return res
          .status(operacionAnterior.codigo_http || 200)
          .json(operacionAnterior.respuesta);
      }
    }

    const actual = await cliente.query(
      `SELECT id, nombre, marca, descripcion, categoria_id,
              precio_centimos, precio_oferta_centimos, stock, imagen_key,
              es_oferta, activo, version, created_at, updated_at
       FROM productos
       WHERE id = $1 AND deleted_at IS NULL
       FOR UPDATE`,
      [req.params.id]
    );
    if (!actual.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Producto no encontrado' });
    }

    const fila = actual.rows[0];
    if (Number(fila.version) !== Number(req.body.version)) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'Conflicto de versión del producto' });
    }

    const nombre =
      req.body.nombre !== undefined ? String(req.body.nombre).trim() : fila.nombre;
    const marca = req.body.marca !== undefined ? String(req.body.marca).trim() : fila.marca;
    const descripcion =
      req.body.descripcion !== undefined ? String(req.body.descripcion).trim() : fila.descripcion;
    const categoriaId = valor(req.body, 'categoriaId', 'categoria_id', fila.categoria_id);
    const precioCentimos = Number(
      valor(req.body, 'precioCentimos', 'precio_centimos', fila.precio_centimos)
    );
    const esOferta =
      req.body.esOferta !== undefined || req.body.es_oferta !== undefined
        ? Boolean(valor(req.body, 'esOferta', 'es_oferta', false))
        : Boolean(fila.es_oferta);
    const ofertaEntrada =
      req.body.precioOfertaCentimos !== undefined || req.body.precio_oferta_centimos !== undefined
        ? valor(req.body, 'precioOfertaCentimos', 'precio_oferta_centimos', null)
        : fila.precio_oferta_centimos;
    const precioOfertaCentimos = normalizarOferta(esOferta, ofertaEntrada);
    const stock = Number(req.body.stock !== undefined ? req.body.stock : fila.stock);
    const imagenKeyRaw = valor(req.body, 'imagenKey', 'imagen_key', fila.imagen_key);
    const imagenKey =
      imagenKeyRaw === null || imagenKeyRaw === undefined || imagenKeyRaw === ''
        ? null
        : String(imagenKeyRaw);
    const activo = req.body.activo !== undefined ? Boolean(req.body.activo) : Boolean(fila.activo);

    if (!nombre) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: 'nombre es obligatorio' });
    }
    if (!marca) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: 'marca es obligatoria' });
    }
    if (!Number.isFinite(precioCentimos) || precioCentimos <= 0) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: 'precio_centimos debe ser > 0' });
    }
    const errorOferta = validarCoherenciaOferta(precioCentimos, precioOfertaCentimos, esOferta);
    if (errorOferta) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: errorOferta });
    }
    if (!Number.isInteger(stock) || stock < 0) {
      await cliente.query('ROLLBACK');
      return res.status(400).json({ error: 'stock debe ser un entero >= 0' });
    }

    const actualizado = await cliente.query(
      `UPDATE productos SET
         nombre = $1,
         marca = $2,
         descripcion = $3,
         categoria_id = $4,
         precio_centimos = $5,
         precio_oferta_centimos = $6,
         stock = $7,
         imagen_key = $8,
         es_oferta = $9,
         activo = $10
       WHERE id = $11 AND deleted_at IS NULL
       RETURNING id, nombre, marca, descripcion, categoria_id,
                 precio_centimos, precio_oferta_centimos, stock, imagen_key,
                 es_oferta, activo, version, created_at, updated_at`,
      [
        nombre,
        marca,
        descripcion,
        categoriaId,
        precioCentimos,
        precioOfertaCentimos,
        stock,
        imagenKey,
        esOferta,
        activo,
        req.params.id
      ]
    );

    const respuesta = { producto: mapearProducto(actualizado.rows[0]) };

    if (uuidOperacion) {
      await registrarOperacionIdempotente(cliente, {
        uuidOperacion,
        usuarioId: req.usuario.id,
        tipoOperacion: 'ACTUALIZAR_PRODUCTO',
        recurso: 'PRODUCTO',
        entidadId: actualizado.rows[0].id,
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
    console.error('Error al actualizar producto:', error.message);
    return res.status(500).json({ error: 'No se pudo actualizar el producto' });
  } finally {
    cliente.release();
  }
}

async function eliminar(req, res) {
  const cliente = await pool.connect();
  try {
    const uuidOperacion = valor(req.body || {}, 'uuidOperacion', 'uuid_operacion', null);

    await cliente.query('BEGIN');

    if (uuidOperacion) {
      const operacionAnterior = await buscarOperacionIdempotente(cliente, uuidOperacion);
      if (operacionAnterior) {
        await cliente.query('COMMIT');
        return res
          .status(operacionAnterior.codigo_http || 200)
          .json(operacionAnterior.respuesta);
      }
    }

    const actual = await cliente.query(
      `SELECT id, version FROM productos WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`,
      [req.params.id]
    );
    if (!actual.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Producto no encontrado' });
    }

    if (
      req.body &&
      req.body.version !== undefined &&
      Number(req.body.version) !== Number(actual.rows[0].version)
    ) {
      await cliente.query('ROLLBACK');
      return res.status(409).json({ error: 'Conflicto de versión del producto' });
    }

    // version/updated_at los incrementa el trigger actualizar_version_y_fecha()
    await cliente.query(
      `UPDATE productos
       SET deleted_at = NOW(), activo = false
       WHERE id = $1`,
      [req.params.id]
    );

    const respuesta = { ok: true, id: req.params.id };

    if (uuidOperacion) {
      await registrarOperacionIdempotente(cliente, {
        uuidOperacion,
        usuarioId: req.usuario.id,
        tipoOperacion: 'ELIMINAR_PRODUCTO',
        recurso: 'PRODUCTO',
        entidadId: req.params.id,
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
    console.error('Error al eliminar producto:', error.message);
    return res.status(500).json({ error: 'No se pudo eliminar el producto' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  listar,
  obtenerPorId,
  crear,
  actualizar,
  eliminar
};
