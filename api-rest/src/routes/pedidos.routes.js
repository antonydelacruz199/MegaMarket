const express = require('express');
const pedidosController = require('../controllers/pedidos.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.post(
  '/',
  verificarToken,
  verificarRol('CLIENTE'),
  pedidosController.crear
);

module.exports = router;
