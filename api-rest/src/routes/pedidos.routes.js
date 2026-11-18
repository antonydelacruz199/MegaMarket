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

router.get(
  '/',
  verificarToken,
  verificarRol('CLIENTE', 'ADMINISTRADOR', 'PICKER', 'REPARTIDOR'),
  pedidosController.listar
);

router.get(
  '/:id',
  verificarToken,
  verificarRol('CLIENTE', 'ADMINISTRADOR', 'PICKER', 'REPARTIDOR'),
  pedidosController.obtenerPorId
);

router.patch(
  '/:id/estado',
  verificarToken,
  verificarRol('CLIENTE', 'ADMINISTRADOR', 'PICKER', 'REPARTIDOR'),
  pedidosController.transicionar
);

module.exports = router;
