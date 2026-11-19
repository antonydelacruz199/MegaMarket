const express = require('express');
const pagosController = require('../controllers/pagos.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken);

router.post(
  '/qr',
  verificarRol('CLIENTE', 'REPARTIDOR', 'ADMINISTRADOR'),
  pagosController.generarQr
);

router.post(
  '/ajustes/:id/confirmar',
  verificarRol('CLIENTE', 'ADMINISTRADOR'),
  pagosController.confirmarAjuste
);

router.get(
  '/pedido/:pedidoId',
  verificarRol('CLIENTE', 'REPARTIDOR', 'ADMINISTRADOR', 'PICKER'),
  pagosController.obtenerPorPedido
);

router.post(
  '/:id/confirmar',
  verificarRol('CLIENTE', 'REPARTIDOR', 'ADMINISTRADOR'),
  pagosController.confirmar
);

router.get(
  '/:id',
  verificarRol('CLIENTE', 'REPARTIDOR', 'ADMINISTRADOR', 'PICKER'),
  pagosController.obtenerPorId
);

module.exports = router;
