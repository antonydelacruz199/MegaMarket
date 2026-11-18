const express = require('express');
const mediosController = require('../controllers/medios.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');
const uploadImagen = require('../middleware/uploadImagen');

const router = express.Router();

router.post(
  '/productos/:productoId',
  verificarToken,
  verificarRol('ADMINISTRADOR'),
  uploadImagen,
  mediosController.subirProducto
);

router.get(
  '/productos/:productoId',
  verificarToken,
  verificarRol('ADMINISTRADOR', 'CLIENTE', 'PICKER', 'REPARTIDOR'),
  mediosController.obtenerProducto
);

router.post(
  '/entregas/:pedidoId',
  verificarToken,
  verificarRol('REPARTIDOR'),
  uploadImagen,
  mediosController.subirEntrega
);

router.get(
  '/entregas/:pedidoId',
  verificarToken,
  verificarRol('ADMINISTRADOR', 'CLIENTE', 'REPARTIDOR'),
  mediosController.obtenerEntrega
);

module.exports = router;
