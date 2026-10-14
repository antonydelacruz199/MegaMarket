const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../database');

const JWT_EXPIRA_SEGUNDOS = 60 * 60 * 24;

function mapearUsuario(fila) {
  return {
    id: fila.id,
    nombre: fila.nombre,
    usuario: fila.usuario,
    correo: fila.correo,
    rol: fila.rol,
    version: fila.version
  };
}

async function login(req, res) {
  try {
    const usuario = String(req.body.usuario || '').trim();
    const clave = String(req.body.clave || '');

    if (!usuario || !clave) {
      return res.status(400).json({ error: 'usuario y clave son obligatorios' });
    }

    const resultado = await pool.query(
      `SELECT id, nombre, usuario, correo, rol, clave_hash, version
       FROM usuarios
       WHERE usuario = $1 AND deleted_at IS NULL
       LIMIT 1`,
      [usuario]
    );

    const fila = resultado.rows[0];
    if (!fila) {
      return res.status(401).json({ error: 'Credenciales incorrectas' });
    }

    const coincide = await bcrypt.compare(clave, fila.clave_hash);
    if (!coincide) {
      return res.status(401).json({ error: 'Credenciales incorrectas' });
    }

    const secreto = process.env.JWT_SECRET;
    if (!secreto || !String(secreto).trim()) {
      return res.status(500).json({ error: 'JWT_SECRET no configurado' });
    }

    const expiresAt = new Date(Date.now() + JWT_EXPIRA_SEGUNDOS * 1000).toISOString();
    const accessToken = jwt.sign(
      {
        id: fila.id,
        usuario: fila.usuario,
        rol: fila.rol
      },
      secreto,
      { expiresIn: JWT_EXPIRA_SEGUNDOS }
    );

    return res.status(200).json({
      accessToken,
      tokenType: 'Bearer',
      expiresAt,
      usuario: mapearUsuario(fila)
    });
  } catch (error) {
    console.error('Error en login:', error.message);
    return res.status(500).json({ error: 'No se pudo iniciar sesión' });
  }
}

async function me(req, res) {
  try {
    const resultado = await pool.query(
      `SELECT id, nombre, usuario, correo, rol, version
       FROM usuarios
       WHERE id = $1 AND deleted_at IS NULL
       LIMIT 1`,
      [req.usuario.id]
    );

    const fila = resultado.rows[0];
    if (!fila) {
      return res.status(404).json({ error: 'Usuario no encontrado' });
    }

    return res.status(200).json({ usuario: mapearUsuario(fila) });
  } catch (error) {
    console.error('Error en /auth/me:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener el usuario' });
  }
}

module.exports = {
  login,
  me
};
