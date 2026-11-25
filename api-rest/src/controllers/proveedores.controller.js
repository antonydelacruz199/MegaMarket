const pool = require('../database');
const { paginar, metaPaginacion } = require('../paginacion');
const {
  mapearProveedor,
  mapearOrden,
  validarRuc,
  validarCorreo,
  valor
} = require('../abastecimiento');

async function listar(req, res) {
  try {
    const { page, limit, offset } = paginar(req);
    const q = String(req.query.q || req.query.buscar || '').trim();
    const activoRaw = req.query.activo;
    const params = [];
    const filtros = [];

    if (q) {
      params.push(`%${q}%`);
      filtros.push(`(ruc ILIKE $${params.length} OR razon_social ILIKE $${params.length})`);
    }
    if (activoRaw !== undefined && activoRaw !== null && String(activoRaw).trim() !== '') {
      const activo = String(activoRaw).toLowerCase() === 'true' || activoRaw === '1' || activoRaw === true;
      params.push(activo);
      filtros.push(`activo = $${params.length}`);
    }

    const where = filtros.length ? `WHERE ${filtros.join(' AND ')}` : '';
    const totalRes = await pool.query(
      `SELECT COUNT(*)::int AS total FROM proveedores ${where}`,
      params
    );
    const lista = await pool.query(
      `SELECT * FROM proveedores ${where}
       ORDER BY razon_social ASC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, limit, offset]
    );

    return res.status(200).json({
      proveedores: lista.rows.map(mapearProveedor),
      ...metaPaginacion(totalRes.rows[0].total, { page, limit })
    });
  } catch (error) {
    console.error('Error listando proveedores:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar los proveedores' });
  }
}

async function obtener(req, res) {
  try {
    const { id } = req.params;
    const resultado = await pool.query(
      `SELECT * FROM proveedores WHERE id = $1 LIMIT 1`,
      [id]
    );
    if (!resultado.rows[0]) {
      return res.status(404).json({ error: 'Proveedor no encontrado' });
    }
    return res.status(200).json({ proveedor: mapearProveedor(resultado.rows[0]) });
  } catch (error) {
    console.error('Error obteniendo proveedor:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el proveedor' });
  }
}

async function crear(req, res) {
  try {
    const rucVal = validarRuc(valor(req.body, 'ruc', 'ruc', ''));
    if (!rucVal.ok) return res.status(400).json({ error: rucVal.error });

    const razonSocial = String(valor(req.body, 'razonSocial', 'razon_social', '')).trim();
    if (!razonSocial) {
      return res.status(400).json({ error: 'La razón social es obligatoria' });
    }

    const correoVal = validarCorreo(valor(req.body, 'correo', 'correo', null));
    if (!correoVal.ok) return res.status(400).json({ error: correoVal.error });

    const telefono = String(valor(req.body, 'telefono', 'telefono', '') || '').trim() || null;
    const cuentaBancaria =
      String(valor(req.body, 'cuentaBancaria', 'cuenta_bancaria', '') || '').trim() || null;
    const activoRaw = valor(req.body, 'activo', 'activo', true);
    const activo = activoRaw !== false && activoRaw !== 'false' && activoRaw !== 0;

    const insertado = await pool.query(
      `INSERT INTO proveedores (ruc, razon_social, telefono, correo, cuenta_bancaria, activo)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [rucVal.ruc, razonSocial, telefono, correoVal.correo, cuentaBancaria, activo]
    );
    return res.status(201).json({ proveedor: mapearProveedor(insertado.rows[0]) });
  } catch (error) {
    if (error && error.code === '23505') {
      return res.status(409).json({ error: 'Ya existe un proveedor con ese RUC' });
    }
    if (error && error.code === '23514') {
      return res.status(400).json({ error: 'Datos de proveedor inválidos según restricciones' });
    }
    console.error('Error creando proveedor:', error.message);
    return res.status(500).json({ error: 'No se pudo registrar el proveedor' });
  }
}

async function actualizar(req, res) {
  const cliente = await pool.connect();
  try {
    const { id } = req.params;
    await cliente.query('BEGIN');
    const actual = await cliente.query(
      `SELECT * FROM proveedores WHERE id = $1 FOR UPDATE`,
      [id]
    );
    if (!actual.rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Proveedor no encontrado' });
    }

    const fila = actual.rows[0];
    let ruc = fila.ruc;
    if (valor(req.body, 'ruc', 'ruc', undefined) !== undefined) {
      const rucVal = validarRuc(valor(req.body, 'ruc', 'ruc', ''));
      if (!rucVal.ok) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: rucVal.error });
      }
      ruc = rucVal.ruc;
    }

    let razonSocial = fila.razon_social;
    if (valor(req.body, 'razonSocial', 'razon_social', undefined) !== undefined) {
      razonSocial = String(valor(req.body, 'razonSocial', 'razon_social', '')).trim();
      if (!razonSocial) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: 'La razón social es obligatoria' });
      }
    }

    let correo = fila.correo;
    if (valor(req.body, 'correo', 'correo', undefined) !== undefined) {
      const correoVal = validarCorreo(valor(req.body, 'correo', 'correo', null));
      if (!correoVal.ok) {
        await cliente.query('ROLLBACK');
        return res.status(400).json({ error: correoVal.error });
      }
      correo = correoVal.correo;
    }

    let telefono = fila.telefono;
    if (valor(req.body, 'telefono', 'telefono', undefined) !== undefined) {
      telefono = String(valor(req.body, 'telefono', 'telefono', '') || '').trim() || null;
    }

    let cuentaBancaria = fila.cuenta_bancaria;
    if (valor(req.body, 'cuentaBancaria', 'cuenta_bancaria', undefined) !== undefined) {
      cuentaBancaria =
        String(valor(req.body, 'cuentaBancaria', 'cuenta_bancaria', '') || '').trim() || null;
    }

    let activo = fila.activo;
    if (valor(req.body, 'activo', 'activo', undefined) !== undefined) {
      const activoRaw = valor(req.body, 'activo', 'activo', true);
      activo = !(activoRaw === false || activoRaw === 'false' || activoRaw === 0);
    }

    const actualizado = await cliente.query(
      `UPDATE proveedores
       SET ruc = $1,
           razon_social = $2,
           telefono = $3,
           correo = $4,
           cuenta_bancaria = $5,
           activo = $6,
           updated_at = now()
       WHERE id = $7
       RETURNING *`,
      [ruc, razonSocial, telefono, correo, cuentaBancaria, activo, id]
    );
    await cliente.query('COMMIT');
    return res.status(200).json({ proveedor: mapearProveedor(actualizado.rows[0]) });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    if (error && error.code === '23505') {
      return res.status(409).json({ error: 'Ya existe un proveedor con ese RUC' });
    }
    if (error && error.code === '23514') {
      return res.status(400).json({ error: 'Datos de proveedor inválidos según restricciones' });
    }
    console.error('Error actualizando proveedor:', error.message);
    return res.status(500).json({ error: 'No se pudo actualizar el proveedor' });
  } finally {
    cliente.release();
  }
}

async function ordenesDelProveedor(req, res) {
  try {
    const { id } = req.params;
    const proveedor = await pool.query(
      `SELECT id FROM proveedores WHERE id = $1 LIMIT 1`,
      [id]
    );
    if (!proveedor.rows[0]) {
      return res.status(404).json({ error: 'Proveedor no encontrado' });
    }

    const { page, limit, offset } = paginar(req);
    const estado = String(req.query.estado || '').trim().toUpperCase();
    const params = [id];
    const filtros = ['proveedor_id = $1'];
    if (estado) {
      params.push(estado);
      filtros.push(`estado = $${params.length}`);
    }

    const where = `WHERE ${filtros.join(' AND ')}`;
    const totalRes = await pool.query(
      `SELECT COUNT(*)::int AS total FROM ordenes_compra ${where}`,
      params
    );
    const lista = await pool.query(
      `SELECT * FROM ordenes_compra ${where}
       ORDER BY created_at DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, limit, offset]
    );

    return res.status(200).json({
      ordenes: lista.rows.map((fila) => mapearOrden(fila)),
      ...metaPaginacion(totalRes.rows[0].total, { page, limit })
    });
  } catch (error) {
    console.error('Error listando órdenes del proveedor:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar las órdenes del proveedor' });
  }
}

module.exports = {
  listar,
  obtener,
  crear,
  actualizar,
  ordenesDelProveedor
};
