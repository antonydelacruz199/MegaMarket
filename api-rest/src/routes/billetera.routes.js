const express = require('express');
const billeteraController = require('../controllers/billetera.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken);

router.get(
  '/saldo',
  verificarRol('CLIENTE', 'ADMINISTRADOR'),
  billeteraController.saldoPropio
);

router.get(
  '/movimientos',
  verificarRol('CLIENTE', 'ADMINISTRADOR'),
  billeteraController.movimientosPropios
);

router.get(
  '/comercio',
  verificarRol('ADMINISTRADOR'),
  billeteraController.saldoComercio
);

module.exports = router;
