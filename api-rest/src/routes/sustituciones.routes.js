const express = require('express');
const controller = require('../controllers/sustituciones.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.get(
  '/',
  verificarToken,
  verificarRol('CLIENTE'),
  controller.listarCliente
);

router.post(
  '/resolver-expiradas',
  verificarToken,
  verificarRol('ADMINISTRADOR', 'PICKER'),
  controller.resolverExpiradasHttp
);

router.get(
  '/:id',
  verificarToken,
  verificarRol('CLIENTE', 'PICKER', 'ADMINISTRADOR'),
  controller.obtenerPorId
);

router.post(
  '/:id/aceptar',
  verificarToken,
  verificarRol('CLIENTE'),
  controller.aceptar
);

router.post(
  '/:id/rechazar',
  verificarToken,
  verificarRol('CLIENTE'),
  controller.rechazar
);

module.exports = router;
