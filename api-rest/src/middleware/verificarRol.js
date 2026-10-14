function verificarRol(...rolesPermitidos) {
  return function (req, res, next) {
    const rol = req.usuario && req.usuario.rol;
    if (!rol || !rolesPermitidos.includes(rol)) {
      return res.status(403).json({ error: 'No tienes permiso para esta operación' });
    }
    return next();
  };
}

module.exports = verificarRol;
