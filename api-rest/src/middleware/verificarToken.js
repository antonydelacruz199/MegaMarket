const jwt = require('jsonwebtoken');

function verificarToken(req, res, next) {
  const encabezado = req.headers.authorization || '';
  const partes = encabezado.split(' ');

  if (partes.length !== 2 || partes[0] !== 'Bearer' || !partes[1]) {
    return res.status(401).json({ error: 'Token ausente o inválido' });
  }

  const secreto = process.env.JWT_SECRET;
  if (!secreto || !String(secreto).trim()) {
    return res.status(500).json({ error: 'JWT_SECRET no configurado' });
  }

  try {
    const payload = jwt.verify(partes[1], secreto);
    req.usuario = {
      id: payload.id,
      usuario: payload.usuario,
      rol: payload.rol
    };
    return next();
  } catch (_error) {
    return res.status(401).json({ error: 'Token inválido o expirado' });
  }
}

module.exports = verificarToken;
