const pool = require('../database');

function mapearCategoria(fila) {
  return {
    id: fila.id,
    nombre: fila.nombre,
    version: Number(fila.version),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

async function listar(req, res) {
  try {
    const resultado = await pool.query(
      `SELECT id, nombre, version, created_at, updated_at
       FROM categorias
       WHERE deleted_at IS NULL
       ORDER BY nombre ASC`
    );
    return res.status(200).json({ categorias: resultado.rows.map(mapearCategoria) });
  } catch (error) {
    console.error('Error al listar categorías:', error.message);
    return res.status(500).json({ error: 'No se pudieron obtener las categorías' });
  }
}

module.exports = {
  listar
};
