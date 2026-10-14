const express = require('express');
const inventarioController = require('../controllers/inventario.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.post(
  '/movimientos',
  verificarToken,
  verificarRol('ADMINISTRADOR', 'CLIENTE'),
  inventarioController.registrarMovimiento
);

module.exports = router;
