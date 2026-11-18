const pool = require('../database');
const { mapearFranja } = require('../franjas');

async function listar(req, res) {
  try {
    const resultado = await pool.query(
      `SELECT f.id, f.fecha, f.hora_inicio, f.hora_fin, f.capacidad, f.activo, f.created_at,
              COALESCE((
                SELECT COUNT(*)::int
                FROM pedidos p
                WHERE p.franja_entrega_id = f.id
                  AND p.estado <> 'CANCELADO'
                  AND p.deleted_at IS NULL
              ), 0) AS ocupados
       FROM franjas_entrega f
       WHERE f.activo = true
         AND (f.fecha = CURRENT_DATE OR f.fecha = CURRENT_DATE + 1)
       ORDER BY f.fecha ASC, f.hora_inicio ASC`
    );

    const franjas = resultado.rows.map(mapearFranja);
    return res.status(200).json({ franjas });
  } catch (error) {
    console.error('Error al listar franjas:', error.message);
    return res.status(500).json({ error: 'No se pudieron obtener las franjas' });
  }
}

async function obtenerPorId(req, res) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const { rows } = await cliente.query(
      `SELECT f.id, f.fecha, f.hora_inicio, f.hora_fin, f.capacidad, f.activo, f.created_at
       FROM franjas_entrega f
       WHERE f.id = $1
       FOR UPDATE`,
      [req.params.id]
    );
    if (!rows[0]) {
      await cliente.query('ROLLBACK');
      return res.status(404).json({ error: 'Franja no encontrada' });
    }
    const { rows: cont } = await cliente.query(
      `SELECT COUNT(*)::int AS ocupados
       FROM pedidos
       WHERE franja_entrega_id = $1
         AND estado <> 'CANCELADO'
         AND deleted_at IS NULL`,
      [req.params.id]
    );
    await cliente.query('COMMIT');
    return res.status(200).json({
      franja: mapearFranja({ ...rows[0], ocupados: cont[0].ocupados })
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al obtener franja:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener la franja' });
  } finally {
    cliente.release();
  }
}

module.exports = {
  listar,
  obtenerPorId
};
